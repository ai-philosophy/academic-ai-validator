package com.aiacademy.academic;

import java.text.BreakIterator;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

import org.springframework.stereotype.Service;

@Service
public class AcademicReviewService {

    private static final int MAX_CLAIMS = 40;
    private static final Locale VIETNAMESE = Locale.forLanguageTag("vi-VN");

    private static final Pattern DOI_PATTERN = Pattern.compile(
            "\\b10\\.\\d{4,9}/[-._;()/:A-Z0-9]+\\b",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern YEAR_PATTERN = Pattern.compile("\\b(18|19|20)\\d{2}\\b");
    private static final Pattern CITATION_PATTERN = Pattern.compile(
            "\\((?:[^)]*?\\b(?:18|19|20)\\d{2}[^)]*?)\\)|\\b[A-Z][\\p{L}'’-]+\\s+et\\s+al\\.?,?\\s*(?:\\((?:18|19|20)\\d{2}\\)|(?:18|19|20)\\d{2})",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
    private static final Pattern PERCENT_PATTERN = Pattern.compile("\\b\\d+(?:[.,]\\d+)?\\s*(?:%|phần trăm|percent)\\b",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
    private static final Pattern NUMBER_PATTERN = Pattern.compile("\\b\\d+(?:[.,]\\d+)?\\b");

    private static final List<String> ABSOLUTE_TERMS = List.of(
            "luôn", "luôn luôn", "tất cả", "mọi", "không bao giờ", "chắc chắn", "hoàn toàn",
            "duy nhất", "bắt buộc", "always", "never", "all", "everyone", "must", "only",
            "certainly", "completely", "proves");
    private static final List<String> HEDGE_TERMS = List.of(
            "có thể", "có xu hướng", "trong một số", "phụ thuộc", "khả năng", "dường như",
            "suggests", "may", "might", "can", "tends to", "appears", "in some contexts");
    private static final List<String> RESEARCH_CUES = List.of(
            "nghiên cứu", "bài báo", "paper", "study", "studies", "research", "literature",
            "meta-analysis", "systematic review", "tổng quan", "thực nghiệm", "empirical");
    private static final List<String> STATISTIC_CUES = List.of(
            "tỷ lệ", "tỉ lệ", "mẫu", "khảo sát", "người tham gia", "sinh viên", "participants",
            "respondents", "sample", "survey", "increase", "decrease", "mean", "average",
            "correlation", "p-value", "standard deviation");
    private static final List<String> CAUSAL_CUES = List.of(
            "dẫn đến", "gây ra", "làm tăng", "làm giảm", "ảnh hưởng đến", "tác động đến",
            "giúp", "cải thiện", "nâng cao", "tăng cường", "hỗ trợ", "thúc đẩy",
            "kết quả là", "vì vậy", "do đó", "therefore", "causes", "leads to", "results in",
            "improves", "improve", "enhances", "impact", "effect");
    private static final List<String> DEFINITION_CUES = List.of(
            "là một", "được định nghĩa", "khái niệm", "lý thuyết", "mô hình", "framework",
            "theory", "model", "defined as", "refers to", "concept");
    private static final List<String> SOURCE_DETAIL_CUES = List.of(
            "doi", "journal", "tạp chí", "publisher", "nhà xuất bản", "conference", "proceedings",
            "vol.", "volume", "issue", "pp.", "pages", "isbn", "issn");

    public AcademicReviewResponse review(AcademicReviewRequest request) {
        String field = defaultValue(request.field(), "Học thuật tổng quát");
        String level = defaultValue(request.academicLevel(), "Đại học");
        String citationStyle = defaultValue(request.citationStyle(), "APA");
        List<String> claimTexts = extractClaimTexts(request.content());

        List<ClaimReview> claims = new ArrayList<>();
        for (int index = 0; index < claimTexts.size(); index++) {
            claims.add(reviewClaim("C" + (index + 1), claimTexts.get(index), citationStyle));
        }

        int averageRisk = claims.isEmpty()
                ? 0
                : (int) Math.round(claims.stream().mapToInt(ClaimReview::riskScore).average().orElse(0));
        long highRiskCount = claims.stream()
                .filter(claim -> claim.riskLevel() == RiskLevel.HIGH || claim.riskLevel() == RiskLevel.CRITICAL)
                .count();
        long reviewCount = claims.stream()
                .filter(claim -> claim.verdict() != Verdict.LOW_RISK_TEXTUAL)
                .count();
        String summary = "Đã tách " + claims.size() + " claim học thuật; " + reviewCount
                + " claim cần xem xét lại, trong đó " + highRiskCount + " claim có rủi ro cao.";

        List<String> notes = List.of(
                "MVP này kiểm định bằng tín hiệu học thuật trong văn bản và sinh checklist rà soát.",
                "Kết quả không thay thế việc đối chiếu trực tiếp với paper, sách, DOI hoặc cơ sở dữ liệu học thuật.",
                "Claim thiếu nguồn sẽ được đánh dấu để người dùng kiểm chứng thay vì được xác nhận là đúng.");

        return new AcademicReviewResponse(
                field,
                level,
                citationStyle,
                summary,
                riskLevelFromScore(averageRisk),
                averageRisk,
                claims.size(),
                claims,
                buildChecklist(claims, citationStyle),
                buildSourceSearchQueries(claims, field),
                notes);
    }

    private ClaimReview reviewClaim(String id, String text, String citationStyle) {
        String lower = text.toLowerCase(VIETNAMESE);
        boolean hasDoi = DOI_PATTERN.matcher(text).find();
        boolean hasCitation = hasDoi || CITATION_PATTERN.matcher(text).find();
        boolean hasYear = YEAR_PATTERN.matcher(text).find();
        boolean hasPercent = PERCENT_PATTERN.matcher(text).find();
        boolean hasNumber = NUMBER_PATTERN.matcher(text).find();
        boolean hasStatistic = hasPercent || (hasNumber && containsAny(lower, STATISTIC_CUES));
        boolean hasAbsolute = containsAny(lower, ABSOLUTE_TERMS);
        boolean hasHedge = containsAny(lower, HEDGE_TERMS);
        boolean hasResearchCue = containsAny(lower, RESEARCH_CUES);
        boolean hasCausalCue = containsAny(lower, CAUSAL_CUES);
        boolean hasDefinitionCue = containsAny(lower, DEFINITION_CUES);
        boolean hasSourceDetails = hasDoi || containsAny(lower, SOURCE_DETAIL_CUES);
        boolean requiresCitation = hasStatistic || hasYear || hasResearchCue || hasCausalCue || hasDefinitionCue
                || hasAbsolute;

        List<String> reasons = new ArrayList<>();
        List<String> evidenceSignals = new ArrayList<>();
        List<String> reviewQuestions = new ArrayList<>();
        int score = 10;

        if (hasCitation) {
            evidenceSignals.add("Có dấu hiệu citation hoặc năm xuất bản trong câu.");
            score -= hasDoi ? 10 : 0;
        }
        if (hasDoi) {
            evidenceSignals.add("Có DOI, nên có thể tra cứu nguồn gốc nhanh hơn.");
        }
        if (hasHedge) {
            evidenceSignals.add("Ngôn ngữ có mức độ thận trọng học thuật.");
            score -= 5;
        }

        if (requiresCitation && !hasCitation) {
            score += 28;
            reasons.add("Claim có nội dung học thuật cần nguồn nhưng chưa có citation.");
            reviewQuestions.add("Claim này nên được gắn với paper, sách, DOI hoặc tài liệu học thuật nào?");
        }
        if (hasStatistic) {
            score += hasCitation ? 18 : 30;
            reasons.add("Có số liệu/thống kê nên cần kiểm tra nguồn dữ liệu, mẫu nghiên cứu và bối cảnh.");
            reviewQuestions.add("Số liệu này đến từ mẫu nghiên cứu nào, năm nào, và phương pháp đo lường nào?");
        }
        if (hasYear && !hasCitation) {
            score += 15;
            reasons.add("Có mốc thời gian nhưng chưa có nguồn đối chiếu.");
            reviewQuestions.add("Mốc thời gian này có khớp với tài liệu gốc không?");
        }
        if (hasResearchCue && !hasCitation) {
            score += 20;
            reasons.add("Cụm như 'nghiên cứu cho thấy' dễ bị AI dùng mơ hồ nếu không có nguồn cụ thể.");
            reviewQuestions.add("Có thể thay cụm mơ hồ này bằng tên tác giả, năm, tên bài và DOI không?");
        }
        if (hasCitation && !hasSourceDetails) {
            score += 12;
            reasons.add("Có citation nhưng chưa có tín hiệu đủ để biết nguồn có tồn tại hay phù hợp không.");
            reviewQuestions.add("Citation này có xuất hiện thật trên Google Scholar, Crossref hoặc thư viện trường không?");
        }
        if (hasAbsolute) {
            score += 18;
            reasons.add("Ngôn ngữ tuyệt đối có thể làm kết luận mạnh hơn bằng chứng học thuật cho phép.");
            reviewQuestions.add("Có cần thay bằng cách diễn đạt thận trọng hơn như 'có thể', 'trong một số bối cảnh' không?");
        }
        if (hasCausalCue && !hasHedge) {
            score += 12;
            reasons.add("Claim nhân quả cần bằng chứng mạnh hơn claim tương quan hoặc mô tả.");
            reviewQuestions.add("Nguồn được trích dẫn là nghiên cứu thực nghiệm, tương quan, hay tổng quan tài liệu?");
        }
        if (hasDefinitionCue && !hasCitation) {
            score += 10;
            reasons.add("Định nghĩa/khái niệm học thuật nên đối chiếu với sách nền tảng hoặc paper gốc.");
            reviewQuestions.add("Định nghĩa này đang theo trường phái/tác giả nào?");
        }
        if (reasons.isEmpty()) {
            reasons.add("Chưa phát hiện tín hiệu rủi ro lớn, nhưng vẫn nên kiểm tra nếu claim là phần trọng tâm.");
        }

        int riskScore = clamp(score, 0, 100);
        ClaimType type = classifyClaim(hasCitation, hasStatistic, hasYear, hasAbsolute, hasCausalCue, hasDefinitionCue,
                hasResearchCue);
        Verdict verdict = decideVerdict(riskScore, hasCitation, hasAbsolute, hasSourceDetails, requiresCitation);
        String suggestedRevision = suggestRevision(verdict, citationStyle, hasAbsolute, hasCausalCue);

        return new ClaimReview(
                id,
                text,
                type,
                riskLevelFromScore(riskScore),
                verdict,
                riskScore,
                List.copyOf(reasons),
                List.copyOf(evidenceSignals),
                suggestedRevision,
                List.copyOf(reviewQuestions));
    }

    private List<String> extractClaimTexts(String content) {
        List<String> claims = new ArrayList<>();
        String normalizedContent = content == null ? "" : content.replace("\r", "\n").trim();
        for (String rawLine : normalizedContent.split("\\n+")) {
            String line = normalizeClaimText(rawLine);
            if (line.isBlank()) {
                continue;
            }
            if (line.length() <= 260 && looksLikeClaim(line)) {
                claims.add(line);
            } else {
                claims.addAll(splitSentences(line));
            }
            if (claims.size() >= MAX_CLAIMS) {
                break;
            }
        }

        return claims.stream()
                .map(this::normalizeClaimText)
                .filter(this::looksLikeClaim)
                .distinct()
                .limit(MAX_CLAIMS)
                .toList();
    }

    private List<String> splitSentences(String text) {
        List<String> sentences = new ArrayList<>();
        BreakIterator iterator = BreakIterator.getSentenceInstance(VIETNAMESE);
        iterator.setText(text);
        int start = iterator.first();
        for (int end = iterator.next(); end != BreakIterator.DONE; start = end, end = iterator.next()) {
            String sentence = normalizeClaimText(text.substring(start, end));
            if (looksLikeClaim(sentence)) {
                sentences.add(sentence);
            }
        }
        if (sentences.isEmpty() && looksLikeClaim(text)) {
            sentences.add(text);
        }
        return sentences;
    }

    private List<ChecklistItem> buildChecklist(List<ClaimReview> claims, String citationStyle) {
        Map<String, ChecklistItem> items = new LinkedHashMap<>();
        addChecklist(items, "CHK-1", "Đối chiếu claim rủi ro cao với nguồn gốc",
                "Kiểm tra paper, sách nền tảng, DOI hoặc cơ sở dữ liệu học thuật trước khi dùng trong bài.",
                ChecklistPriority.HIGH, "Nguồn học thuật", claimIdsByRisk(claims, RiskLevel.HIGH, RiskLevel.CRITICAL));
        addChecklist(items, "CHK-2", "Bổ sung citation cho claim thiếu bằng chứng",
                "Ưu tiên citation chuẩn " + citationStyle + " cho các câu có số liệu, tác giả, năm hoặc kết luận nghiên cứu.",
                ChecklistPriority.HIGH, "Citation", claimIdsByVerdict(claims, Verdict.MISSING_CITATION));
        addChecklist(items, "CHK-3", "Tra cứu sự tồn tại của citation",
                "Đối chiếu tên tác giả, năm, tiêu đề, tạp chí và DOI trên Crossref, Google Scholar hoặc thư viện trường.",
                ChecklistPriority.HIGH, "Citation", claimIdsByVerdict(claims, Verdict.NEEDS_SOURCE_CHECK));
        addChecklist(items, "CHK-4", "Kiểm tra số liệu và phương pháp",
                "Với số liệu/phần trăm, xác nhận kích thước mẫu, đối tượng khảo sát, năm thu thập và bối cảnh nghiên cứu.",
                ChecklistPriority.HIGH, "Dữ liệu", claimIdsByType(claims, ClaimType.STATISTIC_OR_DATA));
        addChecklist(items, "CHK-5", "Giảm ngôn ngữ tuyệt đối",
                "Viết lại các câu dùng 'luôn', 'tất cả', 'chắc chắn', 'proves' theo hướng thận trọng hơn.",
                ChecklistPriority.MEDIUM, "Văn phong học thuật", claimIdsByVerdict(claims, Verdict.OVERSTATED));
        addChecklist(items, "CHK-6", "Phân biệt tương quan và nhân quả",
                "Nếu claim nói A gây ra B, cần kiểm tra loại nghiên cứu và mức bằng chứng trước khi kết luận.",
                ChecklistPriority.MEDIUM, "Lập luận", claimIdsByType(claims, ClaimType.CAUSAL_ARGUMENT));
        addChecklist(items, "CHK-7", "Đối chiếu khái niệm với tài liệu nền tảng",
                "So sánh định nghĩa trong nội dung với textbook, paper gốc hoặc tác giả nền tảng của lĩnh vực.",
                ChecklistPriority.MEDIUM, "Khái niệm", claimIdsByType(claims, ClaimType.CONCEPT_DEFINITION));
        addChecklist(items, "CHK-8", "Ghi chú claim chưa thể xác nhận",
                "Tách các claim chưa tìm được nguồn vào phần 'cần kiểm tra thêm' thay vì đưa thẳng vào kết luận.",
                ChecklistPriority.LOW, "Quy trình rà soát", claimIdsByVerdict(claims, Verdict.NEEDS_REVIEW));

        return items.values().stream()
                .filter(item -> !item.relatedClaimIds().isEmpty() || item.id().equals("CHK-1"))
                .toList();
    }

    private List<String> buildSourceSearchQueries(List<ClaimReview> claims, String field) {
        return claims.stream()
                .filter(claim -> claim.riskLevel() == RiskLevel.HIGH || claim.riskLevel() == RiskLevel.CRITICAL)
                .limit(6)
                .map(claim -> truncate(claim.text(), 110) + " " + field + " DOI academic source")
                .toList();
    }

    private void addChecklist(Map<String, ChecklistItem> items, String id, String title, String description,
            ChecklistPriority priority, String category, List<String> claimIds) {
        items.put(id, new ChecklistItem(id, title, description, priority, category, claimIds));
    }

    private List<String> claimIdsByRisk(List<ClaimReview> claims, RiskLevel... riskLevels) {
        List<RiskLevel> levels = List.of(riskLevels);
        return claims.stream()
                .filter(claim -> levels.contains(claim.riskLevel()))
                .map(ClaimReview::id)
                .toList();
    }

    private List<String> claimIdsByVerdict(List<ClaimReview> claims, Verdict verdict) {
        return claims.stream()
                .filter(claim -> claim.verdict() == verdict)
                .map(ClaimReview::id)
                .toList();
    }

    private List<String> claimIdsByType(List<ClaimReview> claims, ClaimType type) {
        return claims.stream()
                .filter(claim -> claim.type() == type)
                .map(ClaimReview::id)
                .toList();
    }

    private Verdict decideVerdict(int riskScore, boolean hasCitation, boolean hasAbsolute, boolean hasSourceDetails,
            boolean requiresCitation) {
        if (riskScore >= 70 && !hasCitation) {
            return Verdict.POSSIBLE_HALLUCINATION;
        }
        if (hasAbsolute && riskScore >= 45) {
            return Verdict.OVERSTATED;
        }
        if (requiresCitation && !hasCitation) {
            return Verdict.MISSING_CITATION;
        }
        if (hasCitation && !hasSourceDetails) {
            return Verdict.NEEDS_SOURCE_CHECK;
        }
        if (riskScore >= 45) {
            return Verdict.NEEDS_REVIEW;
        }
        return Verdict.LOW_RISK_TEXTUAL;
    }

    private ClaimType classifyClaim(boolean hasCitation, boolean hasStatistic, boolean hasYear, boolean hasAbsolute,
            boolean hasCausalCue, boolean hasDefinitionCue, boolean hasResearchCue) {
        if (hasStatistic) {
            return ClaimType.STATISTIC_OR_DATA;
        }
        if (hasCitation) {
            return ClaimType.CITATION;
        }
        if (hasDefinitionCue) {
            return ClaimType.CONCEPT_DEFINITION;
        }
        if (hasCausalCue) {
            return ClaimType.CAUSAL_ARGUMENT;
        }
        if (hasAbsolute) {
            return ClaimType.GENERALIZATION;
        }
        if (hasYear || hasResearchCue) {
            return ClaimType.AUTHORSHIP_OR_SOURCE;
        }
        return ClaimType.ACADEMIC_ARGUMENT;
    }

    private String suggestRevision(Verdict verdict, String citationStyle, boolean hasAbsolute, boolean hasCausalCue) {
        if (verdict == Verdict.POSSIBLE_HALLUCINATION) {
            return "Tạm đánh dấu là chưa dùng được; hãy tìm nguồn gốc học thuật trước khi đưa vào bài.";
        }
        if (verdict == Verdict.MISSING_CITATION) {
            return "Bổ sung citation chuẩn " + citationStyle + " hoặc viết lại thành nhận định thận trọng hơn.";
        }
        if (verdict == Verdict.NEEDS_SOURCE_CHECK) {
            return "Tra cứu citation để xác nhận tác giả, năm, tiêu đề và DOI có tồn tại thật.";
        }
        if (verdict == Verdict.OVERSTATED || hasAbsolute) {
            return "Giảm các từ tuyệt đối và nêu rõ bối cảnh, điều kiện hoặc giới hạn bằng chứng.";
        }
        if (hasCausalCue) {
            return "Nêu rõ bằng chứng là nhân quả hay chỉ là tương quan.";
        }
        return "Có thể giữ lại sau khi đối chiếu với nguồn học thuật liên quan.";
    }

    private RiskLevel riskLevelFromScore(int score) {
        if (score >= 75) {
            return RiskLevel.CRITICAL;
        }
        if (score >= 55) {
            return RiskLevel.HIGH;
        }
        if (score >= 30) {
            return RiskLevel.MEDIUM;
        }
        return RiskLevel.LOW;
    }

    private boolean containsAny(String lowerText, List<String> terms) {
        return terms.stream().anyMatch(lowerText::contains);
    }

    private boolean looksLikeClaim(String value) {
        String text = value == null ? "" : value.trim();
        if (text.length() < 18) {
            return false;
        }
        long wordCount = Pattern.compile("\\s+").splitAsStream(text).filter(token -> !token.isBlank()).count();
        return wordCount >= 5 && Pattern.compile("\\p{L}", Pattern.UNICODE_CASE).matcher(text).find();
    }

    private String normalizeClaimText(String value) {
        if (value == null) {
            return "";
        }
        return value
                .replaceAll("^\\s*(?:[-*•]|\\d+[.)])\\s*", "")
                .replaceAll("\\s+", " ")
                .trim();
    }

    private String defaultValue(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }

    private String truncate(String value, int maxLength) {
        if (value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength - 1).trim() + "...";
    }

    private int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}
