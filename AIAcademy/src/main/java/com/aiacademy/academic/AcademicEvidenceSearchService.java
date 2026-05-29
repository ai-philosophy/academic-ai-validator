package com.aiacademy.academic;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

@Service
public class AcademicEvidenceSearchService {

    private static final Locale VIETNAMESE = Locale.forLanguageTag("vi-VN");
    private static final Pattern AI_TERM = Pattern.compile("(^|[^\\p{L}\\p{N}])ai([^\\p{L}\\p{N}]|$)",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
    private static final Set<String> QUERY_STOP_WORDS = Set.of(
            "research", "study", "studies", "student", "students", "academic", "education", "higher",
            "school", "theory", "outcomes", "outcome", "paper", "source", "review");

    private final RestClient restClient;
    private final boolean enabled;
    private final int maxClaims;
    private final int maxSourcesPerClaim;

    public AcademicEvidenceSearchService(
            @Value("${academic.evidence.enabled:true}") boolean enabled,
            @Value("${academic.evidence.max-claims:6}") int maxClaims,
            @Value("${academic.evidence.max-sources-per-claim:4}") int maxSourcesPerClaim) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofSeconds(4));
        requestFactory.setReadTimeout(Duration.ofSeconds(6));
        this.restClient = RestClient.builder().requestFactory(requestFactory).build();
        this.enabled = enabled;
        this.maxClaims = Math.max(1, maxClaims);
        this.maxSourcesPerClaim = Math.max(1, maxSourcesPerClaim);
    }

    public List<ClaimEvidence> search(AcademicReviewResponse baseline) {
        if (!enabled || baseline.claims().isEmpty()) {
            return List.of();
        }

        return baseline.claims().stream()
                .filter(this::shouldSearchEvidence)
                .limit(maxClaims)
                .map(claim -> searchClaim(claim, baseline.field()))
                .toList();
    }

    private ClaimEvidence searchClaim(ClaimReview claim, String field) {
        String query = buildQuery(claim.text(), field);
        List<EvidenceSource> sources = new ArrayList<>();

        try {
            sources.addAll(searchOpenAlex(query));
        } catch (RuntimeException exception) {
            // Keep the review flow resilient; missing external evidence should not block the chat.
        }

        try {
            sources.addAll(searchCrossref(query));
        } catch (RuntimeException exception) {
            // Same as above: fallback to model/rule analysis if public indexes are unavailable.
        }

        List<EvidenceSource> distinctSources = distinctSources(sources).stream()
                .filter(source -> isRelevant(source, query))
                .limit(maxSourcesPerClaim)
                .toList();

        String status = distinctSources.isEmpty()
                ? "Không tìm thấy tài liệu liên quan đủ rõ từ OpenAlex/Crossref trong lượt tra cứu nhanh."
                : "Tìm thấy " + distinctSources.size()
                        + " tài liệu liên quan để người dùng kiểm tra trước khi dùng làm citation.";

        return new ClaimEvidence(claim.id(), claim.text(), query, status, distinctSources);
    }

    private boolean shouldSearchEvidence(ClaimReview claim) {
        return claim.riskLevel() == RiskLevel.MEDIUM
                || claim.riskLevel() == RiskLevel.HIGH
                || claim.riskLevel() == RiskLevel.CRITICAL
                || claim.verdict() != Verdict.LOW_RISK_TEXTUAL;
    }

    private List<EvidenceSource> searchOpenAlex(String query) {
        String url = "https://api.openalex.org/works?per-page=3&search=" + encode(query);
        Map<String, Object> payload = restClient.get()
                .uri(url)
                .retrieve()
                .body(new ParameterizedTypeReference<Map<String, Object>>() {
                });

        List<?> results = listValue(payload == null ? null : payload.get("results"));
        List<EvidenceSource> sources = new ArrayList<>();
        for (Object item : results) {
            if (!(item instanceof Map<?, ?> work)) {
                continue;
            }

            String title = stringValue(work.get("title"));
            String year = stringValue(work.get("publication_year"));
            String doi = normalizeDoi(stringValue(work.get("doi")));
            String urlValue = firstNonBlank(stringValue(work.get("id")), doiUrl(doi));
            String venue = openAlexVenue(work);
            String authors = openAlexAuthors(work);
            sources.add(new EvidenceSource(title, authors, year, venue, doi, urlValue, "OpenAlex"));
        }
        return sources;
    }

    private List<EvidenceSource> searchCrossref(String query) {
        String url = "https://api.crossref.org/works?rows=3&query.bibliographic=" + encode(query);
        Map<String, Object> payload = restClient.get()
                .uri(url)
                .retrieve()
                .body(new ParameterizedTypeReference<Map<String, Object>>() {
                });

        Object message = payload == null ? null : payload.get("message");
        List<?> items = message instanceof Map<?, ?> map ? listValue(map.get("items")) : List.of();
        List<EvidenceSource> sources = new ArrayList<>();
        for (Object item : items) {
            if (!(item instanceof Map<?, ?> work)) {
                continue;
            }

            String title = firstListString(work.get("title"));
            String year = crossrefYear(work);
            String doi = normalizeDoi(stringValue(work.get("DOI")));
            String urlValue = firstNonBlank(stringValue(work.get("URL")), doiUrl(doi));
            String venue = firstListString(work.get("container-title"));
            String authors = crossrefAuthors(work);
            sources.add(new EvidenceSource(title, authors, year, venue, doi, urlValue, "Crossref"));
        }
        return sources;
    }

    String buildQuery(String claimText, String field) {
        String lowerClaim = claimText.toLowerCase(VIETNAMESE);
        String lowerField = defaultValue(field, "").toLowerCase(VIETNAMESE);
        List<String> concepts = new ArrayList<>();

        addIfContains(concepts, lowerClaim, "học tập cá nhân hóa", "personalized learning");
        addIfContains(concepts, lowerClaim, "học cá nhân hóa", "personalized learning");
        addIfContains(concepts, lowerClaim, "cá nhân hóa", "personalized learning");
        addIfContains(concepts, lowerClaim, "kết quả học tập", "learning outcomes academic achievement");
        addIfContains(concepts, lowerClaim, "thành tích học tập", "academic achievement");
        addIfContains(concepts, lowerClaim, "hiệu quả học tập", "learning effectiveness academic achievement");
        addIfContains(concepts, lowerClaim, "sinh viên", "students higher education");
        addIfContains(concepts, lowerClaim, "học sinh", "students school");
        addIfContains(concepts, lowerClaim, "động lực", "student motivation");
        addIfContains(concepts, lowerClaim, "trì hoãn", "academic procrastination");
        addIfContains(concepts, lowerClaim, "chatbot", "educational chatbot");
        addIfContains(concepts, lowerClaim, "hệ thống gợi ý", "recommender system education");
        addIfContains(concepts, lowerClaim, "trí tuệ nhân tạo", "artificial intelligence education");
        if (AI_TERM.matcher(lowerClaim).find()) {
            concepts.add("artificial intelligence education");
        }
        addIfContains(concepts, lowerClaim, "constructivism", "constructivism learning theory");
        addIfContains(concepts, lowerClaim, "kiến tạo", "constructivism learning theory");
        addIfContains(concepts, lowerClaim, "tương tác xã hội", "social interaction learning");
        addIfContains(concepts, lowerClaim, "tự điều chỉnh", "self regulated learning");
        addIfContains(concepts, lowerClaim, "tâm lý", "psychology");
        addIfContains(concepts, lowerClaim, "y sinh", "biomedicine");
        addIfContains(concepts, lowerClaim, "kinh tế", "economics");
        addIfContains(concepts, lowerClaim, "xã hội", "sociology");

        if (lowerField.contains("giáo dục") || lowerField.contains("học thuật")) {
            concepts.add("education research");
        }
        if (lowerField.contains("tâm lý")) {
            concepts.add("psychology research");
        }
        if (lowerField.contains("máy tính")) {
            concepts.add("computer science research");
        }
        if (!concepts.isEmpty()) {
            return String.join(" ", concepts.stream().distinct().toList());
        }

        String normalized = claimText
                .replaceAll("[\"“”‘’]", "")
                .replaceAll("\\s+", " ")
                .trim();
        if (normalized.length() > 140) {
            normalized = normalized.substring(0, 140).trim();
        }
        return normalized + " " + defaultValue(field, "academic research");
    }

    private void addIfContains(List<String> concepts, String text, String needle, String concept) {
        if (text.contains(needle)) {
            concepts.add(concept);
        }
    }

    boolean isRelevant(EvidenceSource source, String query) {
        String title = source.title().toLowerCase(Locale.ROOT);
        if (title.startsWith("erratum") || title.startsWith("correction") || title.contains("retraction notice")) {
            return false;
        }

        String haystack = (source.title() + " " + source.venue() + " " + source.authors()).toLowerCase(Locale.ROOT);
        List<String> terms = List.of(query.toLowerCase(Locale.ROOT).split("\\s+")).stream()
                .map(term -> term.replaceAll("[^a-z0-9-]", ""))
                .filter(term -> term.length() >= 5)
                .filter(term -> !QUERY_STOP_WORDS.contains(term))
                .distinct()
                .toList();

        if (terms.isEmpty()) {
            return !source.title().isBlank();
        }

        long matchedTerms = terms.stream().filter(haystack::contains).count();
        int requiredMatches = terms.size() >= 3 ? 2 : 1;
        if (matchedTerms >= requiredMatches) {
            return true;
        }

        return hasImportantPhraseMatch(haystack, query.toLowerCase(Locale.ROOT));
    }

    private boolean hasImportantPhraseMatch(String haystack, String query) {
        return (query.contains("personalized learning") && haystack.contains("personalized learning"))
                || (query.contains("artificial intelligence") && haystack.contains("artificial intelligence"))
                || (query.contains("academic achievement") && haystack.contains("academic achievement"))
                || (query.contains("educational chatbot") && haystack.contains("chatbot"))
                || (query.contains("constructivism") && haystack.contains("constructivism"));
    }

    private List<EvidenceSource> distinctSources(List<EvidenceSource> sources) {
        Map<String, EvidenceSource> distinct = new LinkedHashMap<>();
        for (EvidenceSource source : sources) {
            if (source.title().isBlank()) {
                continue;
            }
            String key = !source.doi().isBlank()
                    ? source.doi().toLowerCase(Locale.ROOT)
                    : source.title().toLowerCase(Locale.ROOT);
            distinct.putIfAbsent(key, source);
        }
        return new ArrayList<>(distinct.values());
    }

    private String openAlexVenue(Map<?, ?> work) {
        Object primaryLocation = work.get("primary_location");
        if (primaryLocation instanceof Map<?, ?> location) {
            Object source = location.get("source");
            if (source instanceof Map<?, ?> sourceMap) {
                return stringValue(sourceMap.get("display_name"));
            }
        }
        return "";
    }

    private String openAlexAuthors(Map<?, ?> work) {
        return listValue(work.get("authorships")).stream()
                .limit(4)
                .map(item -> {
                    if (!(item instanceof Map<?, ?> authorship)) {
                        return "";
                    }
                    Object author = authorship.get("author");
                    return author instanceof Map<?, ?> authorMap ? stringValue(authorMap.get("display_name")) : "";
                })
                .filter(value -> !value.isBlank())
                .reduce((first, second) -> first + "; " + second)
                .orElse("");
    }

    private String crossrefAuthors(Map<?, ?> work) {
        return listValue(work.get("author")).stream()
                .limit(4)
                .map(item -> {
                    if (!(item instanceof Map<?, ?> author)) {
                        return "";
                    }
                    String given = stringValue(author.get("given"));
                    String family = stringValue(author.get("family"));
                    return (given + " " + family).trim();
                })
                .filter(value -> !value.isBlank())
                .reduce((first, second) -> first + "; " + second)
                .orElse("");
    }

    private String crossrefYear(Map<?, ?> work) {
        Object issued = work.get("issued");
        if (!(issued instanceof Map<?, ?> issuedMap)) {
            return "";
        }
        List<?> dateParts = listValue(issuedMap.get("date-parts"));
        if (dateParts.isEmpty()) {
            return "";
        }
        Object firstPart = dateParts.get(0);
        if (!(firstPart instanceof List<?> values) || values.isEmpty()) {
            return "";
        }
        return stringValue(values.get(0));
    }

    private String firstListString(Object value) {
        List<?> list = listValue(value);
        return list.isEmpty() ? "" : stringValue(list.get(0));
    }

    private String normalizeDoi(String value) {
        String normalized = value == null ? "" : value.trim();
        return normalized.replaceFirst("(?i)^https?://(?:dx\\.)?doi\\.org/", "");
    }

    private String doiUrl(String doi) {
        return doi == null || doi.isBlank() ? "" : "https://doi.org/" + doi;
    }

    private String firstNonBlank(String first, String second) {
        return first == null || first.isBlank() ? defaultValue(second, "") : first;
    }

    private String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private List<?> listValue(Object value) {
        return value instanceof List<?> list ? list : List.of();
    }

    private String stringValue(Object value) {
        return Objects.toString(value, "").trim();
    }

    private String defaultValue(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }
}
