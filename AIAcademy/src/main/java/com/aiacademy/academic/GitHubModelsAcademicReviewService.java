package com.aiacademy.academic;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

@Service
public class GitHubModelsAcademicReviewService {

    private static final String DEFAULT_PROVIDER = "GitHub Models";

    private final RestClient restClient;
    private final JsonMapper jsonMapper;
    private final String endpoint;
    private final String modelName;
    private final String apiVersion;
    private final String configuredToken;
    private final boolean enabled;

    public GitHubModelsAcademicReviewService(
            @Value("${github.models.endpoint:https://models.github.ai/inference}") String endpoint,
            @Value("${github.models.model:openai/gpt-4.1}") String modelName,
            @Value("${github.models.api-version:2026-03-10}") String apiVersion,
            @Value("${github.models.token:}") String configuredToken,
            @Value("${github.models.enabled:true}") boolean enabled) {
        this.restClient = RestClient.create();
        this.jsonMapper = JsonMapper.builder().build();
        this.endpoint = normalizeEndpoint(endpoint);
        this.modelName = modelName;
        this.apiVersion = apiVersion;
        this.configuredToken = configuredToken;
        this.enabled = enabled;
    }

    public boolean isConfigured() {
        return enabled && !token().isBlank();
    }

    public String modelName() {
        return modelName;
    }

    public String configurationStatus() {
        if (!enabled) {
            return "GitHub Models đang bị tắt bằng cấu hình github.models.enabled=false.";
        }
        if (token().isBlank()) {
            return "Chưa thấy GITHUB_MODELS_TOKEN hoặc GITHUB_TOKEN trong môi trường chạy app.";
        }
        return "GitHub Models đã sẵn sàng.";
    }

    public AcademicReviewResponse review(AcademicReviewRequest request, AcademicReviewResponse baseline) {
        return review(request, baseline, List.of());
    }

    public AcademicReviewResponse review(AcademicReviewRequest request, AcademicReviewResponse baseline,
            List<ClaimEvidence> evidence) {
        String content = chatCompletionContentWithRetry(request, baseline, evidence);
        Map<String, Object> payload = parseJsonPayload(content);
        return mapReview(payload, request, baseline);
    }

    private String chatCompletionContentWithRetry(AcademicReviewRequest request, AcademicReviewResponse baseline,
            List<ClaimEvidence> evidence) {
        try {
            return chatCompletionContent(request, baseline, evidence);
        } catch (RestClientResponseException exception) {
            if (exception.getStatusCode().value() == 429) {
                sleepBeforeRetry();
                try {
                    return chatCompletionContent(request, baseline, evidence);
                } catch (RestClientResponseException retryException) {
                    throw sanitizedHttpException(retryException);
                }
            }
            throw sanitizedHttpException(exception);
        }
    }

    private String chatCompletionContent(AcademicReviewRequest request, AcademicReviewResponse baseline,
            List<ClaimEvidence> evidence) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", modelName);
        body.put("temperature", 0.05);
        body.put("max_completion_tokens", 3200);
        body.put("messages", List.of(
                Map.of("role", "system", "content", systemPrompt()),
                Map.of("role", "user", "content", userPrompt(request, baseline, evidence))));

        Map<String, Object> response = restClient.post()
                .uri(endpoint + "/chat/completions")
                .header("Accept", "application/vnd.github+json")
                .header("Authorization", "Bearer " + token())
                .header("X-GitHub-Api-Version", apiVersion)
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .body(new ParameterizedTypeReference<Map<String, Object>>() {
                });

        if (response == null) {
            throw new IllegalStateException("empty response");
        }

        List<?> choices = listValue(response.get("choices"));
        if (choices.isEmpty() || !(choices.get(0) instanceof Map<?, ?> firstChoice)) {
            throw new IllegalStateException("missing choices");
        }

        Object message = firstChoice.get("message");
        if (!(message instanceof Map<?, ?> messageMap)) {
            throw new IllegalStateException("missing message");
        }

        String content = stringValue(messageMap.get("content"));
        if (content.isBlank()) {
            throw new IllegalStateException("empty model content");
        }
        return content;
    }

    private IllegalStateException sanitizedHttpException(RestClientResponseException exception) {
        int statusCode = exception.getStatusCode().value();
        String message = switch (statusCode) {
            case 401 -> "GitHub Models từ chối token (HTTP 401). Kiểm tra GITHUB_MODELS_TOKEN/GITHUB_TOKEN.";
            case 403 -> "GitHub Models không cho phép request này (HTTP 403). Kiểm tra quyền token hoặc quyền truy cập GitHub Models.";
            case 429 -> "GitHub Models đang giới hạn tần suất (HTTP 429). Tạm dùng rule-based; thử lại sau vài phút.";
            default -> "GitHub Models trả lỗi HTTP " + statusCode + ". Tạm dùng rule-based.";
        };
        return new IllegalStateException(message, exception);
    }

    private void sleepBeforeRetry() {
        try {
            Thread.sleep(1_500);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        }
    }

    private String systemPrompt() {
        return """
                Bạn là một trợ lý kiểm định nội dung học thuật. Nhiệm vụ của bạn là phát hiện claim có nguy cơ sai,
                thiếu nguồn, citation khả nghi, số liệu không có bằng chứng, hoặc lập luận quá mạnh so với dữ liệu.

                Quy tắc bắt buộc:
                - Giữ nguyên ID claim từ phân tích rule-based nếu có thể.
                - Chỉ dùng evidence từ OpenAlex/Crossref như "gợi ý nguồn liên quan để kiểm tra", không tự coi là nguồn xác nhận nếu chưa khớp rõ với claim.
                - Không được tự xác nhận một claim là đúng nếu evidence không trực tiếp hỗ trợ claim đó.
                - Nếu claim nghe hợp lý nhưng thiếu nguồn, đánh dấu MISSING_CITATION hoặc NEEDS_REVIEW.
                - Nếu có số liệu, phần trăm, tên tác giả, năm, DOI, paper, hãy yêu cầu người dùng kiểm tra nguồn gốc.
                - Không bịa citation, DOI, tên paper hoặc kết quả nghiên cứu.
                - Nếu evidence không khớp claim, hãy nói rõ trong reasons/reviewQuestions rằng tài liệu chỉ liên quan để đối chiếu, chưa xác nhận claim.
                - Trong evidenceSignals, tránh dùng cụm khó hiểu như "nguồn ứng viên". Hãy viết theo mẫu:
                  "Gợi ý nguồn liên quan: Tên tác giả (năm), DOI/link. Cần đọc để kiểm tra claim."
                  hoặc "Chưa có nguồn khớp trực tiếp để xác nhận claim này."
                - Ưu tiên đánh dấu OVERSTATED cho kết luận tuyệt đối, và NEEDS_SOURCE_CHECK cho citation/nguồn cần kiểm tra.
                - Trả về duy nhất JSON hợp lệ, không markdown, không code fence.

                Schema JSON:
                {
                  "summary": "string",
                  "overallRisk": "LOW|MEDIUM|HIGH|CRITICAL",
                  "riskScore": 0,
                  "assistantMessage": "string",
                  "claims": [
                    {
                      "id": "C1",
                      "text": "string",
                      "type": "CONCEPT_DEFINITION|AUTHORSHIP_OR_SOURCE|STATISTIC_OR_DATA|DATE_OR_HISTORY|CITATION|CAUSAL_ARGUMENT|GENERALIZATION|ACADEMIC_ARGUMENT",
                      "riskLevel": "LOW|MEDIUM|HIGH|CRITICAL",
                      "verdict": "LOW_RISK_TEXTUAL|MISSING_CITATION|NEEDS_REVIEW|NEEDS_SOURCE_CHECK|OVERSTATED|POSSIBLE_HALLUCINATION",
                      "riskScore": 0,
                      "reasons": ["string"],
                      "evidenceSignals": ["string: nêu rõ tài liệu liên quan nào cần đọc/đối chiếu, hoặc vì sao chưa đủ bằng chứng"],
                      "suggestedRevision": "string",
                      "reviewQuestions": ["string"]
                    }
                  ],
                  "checklist": [
                    {
                      "id": "CHK-1",
                      "title": "string",
                      "description": "string",
                      "priority": "HIGH|MEDIUM|LOW",
                      "category": "string",
                      "relatedClaimIds": ["C1"]
                    }
                  ],
                  "sourceSearchQueries": ["string"],
                  "reviewNotes": ["string"]
                }
                """;
    }

    private String userPrompt(AcademicReviewRequest request, AcademicReviewResponse baseline,
            List<ClaimEvidence> evidence) {
        try {
            return """
                    Hãy kiểm định nội dung sau trong phạm vi học thuật.

                    Lĩnh vực: %s
                    Cấp độ: %s
                    Chuẩn trích dẫn mong muốn: %s

                    Nội dung người dùng:
                    %s

                    Phân tích rule-based ban đầu để tham khảo, không bắt buộc giữ nguyên:
                    %s

                    Evidence search từ OpenAlex/Crossref.
                    Lưu ý: đây chỉ là gợi ý nguồn liên quan để kiểm tra, không được coi là xác nhận nếu không khớp trực tiếp với claim:
                    %s
                    """.formatted(
                    defaultValue(request.field(), "Học thuật tổng quát"),
                    defaultValue(request.academicLevel(), "Đại học"),
                    defaultValue(request.citationStyle(), "APA"),
                    request.content(),
                    jsonMapper.writeValueAsString(baseline),
                    jsonMapper.writeValueAsString(evidence));
        } catch (Exception exception) {
            throw new IllegalStateException("cannot build model prompt", exception);
        }
    }

    private AcademicReviewResponse mapReview(Map<String, Object> payload, AcademicReviewRequest request,
            AcademicReviewResponse baseline) {
        List<ClaimReview> claims = mapClaims(payload.get("claims"), baseline.claims());
        int riskScore = intValue(payload.get("riskScore"), averageRisk(claims, baseline.riskScore()));
        RiskLevel overallRisk = enumValue(RiskLevel.class, payload.get("overallRisk"), riskLevelFromScore(riskScore));
        String summary = defaultValue(stringValue(payload.get("summary")), baseline.summary());
        String field = defaultValue(request.field(), baseline.field());
        String academicLevel = defaultValue(request.academicLevel(), baseline.academicLevel());
        String citationStyle = defaultValue(request.citationStyle(), baseline.citationStyle());

        List<ChecklistItem> checklist = mapChecklist(payload.get("checklist"), baseline.checklist());
        List<String> queries = stringList(payload.get("sourceSearchQueries"));
        if (queries.isEmpty()) {
            queries = baseline.sourceSearchQueries();
        }
        List<String> notes = new ArrayList<>(stringList(payload.get("reviewNotes")));
        notes.add(0, "Đã phân tích bằng " + DEFAULT_PROVIDER + " model " + modelName + ".");

        return new AcademicReviewResponse(
                field,
                academicLevel,
                citationStyle,
                summary,
                overallRisk,
                riskScore,
                claims.size(),
                claims,
                checklist,
                queries,
                notes);
    }

    private List<ClaimReview> mapClaims(Object rawClaims, List<ClaimReview> fallback) {
        List<?> values = listValue(rawClaims);
        if (values.isEmpty()) {
            return fallback;
        }

        List<ClaimReview> claims = new ArrayList<>();
        for (int index = 0; index < values.size(); index++) {
            if (!(values.get(index) instanceof Map<?, ?> map)) {
                continue;
            }

            String id = defaultValue(stringValue(map.get("id")), "C" + (index + 1));
            RiskLevel riskLevel = enumValue(RiskLevel.class, map.get("riskLevel"), RiskLevel.MEDIUM);
            int riskScore = intValue(map.get("riskScore"), scoreFromRisk(riskLevel));
            claims.add(new ClaimReview(
                    id,
                    defaultValue(stringValue(map.get("text")), "Claim cần kiểm tra lại"),
                    enumValue(ClaimType.class, map.get("type"), ClaimType.ACADEMIC_ARGUMENT),
                    riskLevel,
                    enumValue(Verdict.class, map.get("verdict"), Verdict.NEEDS_REVIEW),
                    riskScore,
                    nonEmptyStringList(map.get("reasons"), List.of("Model đánh dấu claim này cần rà soát học thuật.")),
                    stringList(map.get("evidenceSignals")),
                    defaultValue(stringValue(map.get("suggestedRevision")),
                            "Hãy đối chiếu với nguồn học thuật trước khi sử dụng."),
                    nonEmptyStringList(map.get("reviewQuestions"),
                            List.of("Claim này có nguồn học thuật đáng tin cậy không?"))));
        }

        return claims.isEmpty() ? fallback : claims;
    }

    private List<ChecklistItem> mapChecklist(Object rawChecklist, List<ChecklistItem> fallback) {
        List<?> values = listValue(rawChecklist);
        if (values.isEmpty()) {
            return fallback;
        }

        List<ChecklistItem> items = new ArrayList<>();
        for (int index = 0; index < values.size(); index++) {
            if (!(values.get(index) instanceof Map<?, ?> map)) {
                continue;
            }

            items.add(new ChecklistItem(
                    defaultValue(stringValue(map.get("id")), "CHK-" + (index + 1)),
                    defaultValue(stringValue(map.get("title")), "Rà soát claim học thuật"),
                    defaultValue(stringValue(map.get("description")),
                            "Đối chiếu claim với nguồn học thuật trước khi sử dụng."),
                    enumValue(ChecklistPriority.class, map.get("priority"), ChecklistPriority.MEDIUM),
                    defaultValue(stringValue(map.get("category")), "Kiểm định học thuật"),
                    stringList(map.get("relatedClaimIds"))));
        }

        return items.isEmpty() ? fallback : items;
    }

    private Map<String, Object> parseJsonPayload(String content) {
        String json = extractJson(content);
        try {
            return jsonMapper.readValue(json, new TypeReference<Map<String, Object>>() {
            });
        } catch (Exception exception) {
            throw new IllegalStateException("model returned non-JSON content", exception);
        }
    }

    private String extractJson(String content) {
        String trimmed = content.trim();
        if (trimmed.startsWith("```")) {
            trimmed = trimmed.replaceFirst("^```(?:json)?\\s*", "").replaceFirst("\\s*```$", "");
        }

        int start = trimmed.indexOf('{');
        int end = trimmed.lastIndexOf('}');
        if (start >= 0 && end > start) {
            return trimmed.substring(start, end + 1);
        }
        return trimmed;
    }

    private String token() {
        if (configuredToken != null && !configuredToken.isBlank()) {
            return configuredToken.trim();
        }
        String githubModelsToken = System.getenv("GITHUB_MODELS_TOKEN");
        if (githubModelsToken != null && !githubModelsToken.isBlank()) {
            return githubModelsToken.trim();
        }
        String githubToken = System.getenv("GITHUB_TOKEN");
        return githubToken == null ? "" : githubToken.trim();
    }

    private String normalizeEndpoint(String value) {
        String normalized = defaultValue(value, "https://models.github.ai/inference");
        while (normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return normalized;
    }

    private <E extends Enum<E>> E enumValue(Class<E> enumType, Object value, E fallback) {
        String raw = stringValue(value);
        if (raw.isBlank()) {
            return fallback;
        }

        String normalized = raw.trim()
                .toUpperCase(Locale.ROOT)
                .replace('-', '_')
                .replace(' ', '_');
        try {
            return Enum.valueOf(enumType, normalized);
        } catch (IllegalArgumentException exception) {
            return fallback;
        }
    }

    private List<String> nonEmptyStringList(Object value, List<String> fallback) {
        List<String> strings = stringList(value);
        return strings.isEmpty() ? fallback : strings;
    }

    private List<String> stringList(Object value) {
        return listValue(value).stream()
                .map(this::stringValue)
                .filter(item -> !item.isBlank())
                .toList();
    }

    private List<?> listValue(Object value) {
        return value instanceof List<?> list ? list : List.of();
    }

    private String stringValue(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    private String defaultValue(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }

    private int intValue(Object value, int fallback) {
        if (value instanceof Number number) {
            return clamp(number.intValue(), 0, 100);
        }
        try {
            return clamp(Integer.parseInt(stringValue(value)), 0, 100);
        } catch (NumberFormatException exception) {
            return fallback;
        }
    }

    private int averageRisk(List<ClaimReview> claims, int fallback) {
        return claims.isEmpty()
                ? fallback
                : (int) Math.round(claims.stream().mapToInt(ClaimReview::riskScore).average().orElse(fallback));
    }

    private int scoreFromRisk(RiskLevel riskLevel) {
        return switch (riskLevel) {
            case LOW -> 20;
            case MEDIUM -> 45;
            case HIGH -> 65;
            case CRITICAL -> 85;
        };
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

    private int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}
