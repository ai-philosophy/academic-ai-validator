package com.aiacademy.academic;

import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Service;

@Service
public class AcademicReviewOrchestrator {

    private final AcademicReviewService ruleBasedReviewService;
    private final GitHubModelsAcademicReviewService gitHubModelsReviewService;
    private final AcademicEvidenceSearchService evidenceSearchService;

    public AcademicReviewOrchestrator(AcademicReviewService ruleBasedReviewService,
            GitHubModelsAcademicReviewService gitHubModelsReviewService,
            AcademicEvidenceSearchService evidenceSearchService) {
        this.ruleBasedReviewService = ruleBasedReviewService;
        this.gitHubModelsReviewService = gitHubModelsReviewService;
        this.evidenceSearchService = evidenceSearchService;
    }

    public AcademicReviewResult review(AcademicReviewRequest request) {
        AcademicReviewResponse baseline = ruleBasedReviewService.review(request);

        if (!gitHubModelsReviewService.isConfigured()) {
            return new AcademicReviewResult(
                    baseline,
                    false,
                    "Rule-based verifier",
                    "Không có GitHub Models token",
                    gitHubModelsReviewService.configurationStatus());
        }

        List<ClaimEvidence> evidence = evidenceSearchService.search(baseline);
        try {
            AcademicReviewResponse aiReview = gitHubModelsReviewService.review(request, baseline, evidence);
            return new AcademicReviewResult(
                    appendEvidenceNotes(aiReview, evidence),
                    true,
                    "GitHub Models",
                    gitHubModelsReviewService.modelName(),
                    "Đang dùng GitHub Models để phân tích claim học thuật.");
        } catch (RuntimeException exception) {
            return new AcademicReviewResult(
                    appendEvidenceNotes(baseline, evidence),
                    false,
                    "Rule-based verifier",
                    "Fallback sau lỗi GitHub Models",
                    "GitHub Models chưa phản hồi hợp lệ: " + exception.getMessage());
        }
    }

    private AcademicReviewResponse appendEvidenceNotes(AcademicReviewResponse review, List<ClaimEvidence> evidence) {
        if (evidence.isEmpty()) {
            return review;
        }

        List<String> notes = new ArrayList<>(review.reviewNotes());
        evidence.stream()
                .map(this::formatEvidenceNote)
                .forEach(notes::add);

        return new AcademicReviewResponse(
                review.field(),
                review.academicLevel(),
                review.citationStyle(),
                review.summary(),
                review.overallRisk(),
                review.riskScore(),
                review.reviewedClaimCount(),
                review.claims(),
                review.checklist(),
                review.sourceSearchQueries(),
                notes);
    }

    private String formatEvidenceNote(ClaimEvidence claimEvidence) {
        if (claimEvidence.sources().isEmpty()) {
            return claimEvidence.claimId() + ": " + claimEvidence.status()
                    + " Query: " + claimEvidence.searchQuery();
        }

        String sources = claimEvidence.sources().stream()
                .map(source -> {
                    String year = source.year().isBlank() ? "n.d." : source.year();
                    String venue = source.venue().isBlank() ? source.source() : source.venue();
                    String link = source.url().isBlank() ? source.doi() : source.url();
                    return source.title() + " (" + year + ", " + venue + ") " + link;
                })
                .limit(3)
                .reduce((first, second) -> first + " | " + second)
                .orElse("");

        return claimEvidence.claimId()
                + ": Gợi ý nguồn liên quan từ OpenAlex/Crossref để kiểm tra, chưa phải bằng chứng xác nhận: "
                + sources;
    }
}
