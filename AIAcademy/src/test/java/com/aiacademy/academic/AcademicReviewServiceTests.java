package com.aiacademy.academic;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class AcademicReviewServiceTests {

    private final AcademicReviewService service = new AcademicReviewService();

    @Test
    void flagsUnsupportedStatisticAsHighRisk() {
        AcademicReviewResponse response = service.review(new AcademicReviewRequest(
                "Nghiên cứu cho thấy 85% sinh viên học tốt hơn khi sử dụng AI trong lớp học.",
                "Giáo dục học",
                "Đại học",
                "APA"));

        assertThat(response.claims()).hasSize(1);
        ClaimReview claim = response.claims().get(0);

        assertThat(claim.type()).isEqualTo(ClaimType.STATISTIC_OR_DATA);
        assertThat(claim.verdict()).isEqualTo(Verdict.POSSIBLE_HALLUCINATION);
        assertThat(claim.riskLevel()).isIn(RiskLevel.HIGH, RiskLevel.CRITICAL);
        assertThat(response.checklist())
                .anySatisfy(item -> assertThat(item.title()).contains("số liệu"));
    }

    @Test
    void asksUserToCheckCitationExistence() {
        AcademicReviewResponse response = service.review(new AcademicReviewRequest(
                "Theo Smith (2020), học tập cá nhân hóa có thể cải thiện động lực học tập.",
                "Giáo dục học",
                "Đại học",
                "APA"));

        ClaimReview claim = response.claims().get(0);

        assertThat(claim.verdict()).isEqualTo(Verdict.NEEDS_SOURCE_CHECK);
        assertThat(claim.reviewQuestions())
                .anyMatch(question -> question.contains("Google Scholar") || question.contains("Crossref"));
    }

    @Test
    void flagsVietnameseImprovementClaimWithoutCitation() {
        AcademicReviewResponse response = service.review(new AcademicReviewRequest(
                "Học tập cá nhân hóa giúp cải thiện kết quả học tập của sinh viên.",
                "Giáo dục học",
                "Đại học",
                "APA"));

        ClaimReview claim = response.claims().get(0);

        assertThat(claim.type()).isEqualTo(ClaimType.CAUSAL_ARGUMENT);
        assertThat(claim.verdict()).isEqualTo(Verdict.MISSING_CITATION);
        assertThat(claim.riskLevel()).isEqualTo(RiskLevel.MEDIUM);
    }

    @Test
    void detectsOverstatedAcademicLanguage() {
        AcademicReviewResponse response = service.review(new AcademicReviewRequest(
                "Học tập cá nhân hóa luôn cải thiện kết quả của tất cả sinh viên trong mọi bối cảnh.",
                "Giáo dục học",
                "Đại học",
                "APA"));

        ClaimReview claim = response.claims().get(0);

        assertThat(claim.verdict()).isEqualTo(Verdict.OVERSTATED);
        assertThat(claim.suggestedRevision()).contains("tuyệt đối");
    }
}
