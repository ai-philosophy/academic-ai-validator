package com.aiacademy.academic;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class AcademicEvidenceSearchServiceTests {

    @Test
    void disabledSearchDoesNotCallExternalIndexes() {
        AcademicReviewResponse baseline = new AcademicReviewService().review(new AcademicReviewRequest(
                "Nghiên cứu cho thấy 85% sinh viên học tốt hơn khi sử dụng AI trong lớp học.",
                "Giáo dục học",
                "Đại học",
                "APA"));

        AcademicEvidenceSearchService service = new AcademicEvidenceSearchService(false, 2, 2);

        assertThat(service.search(baseline)).isEmpty();
    }

    @Test
    void buildsEnglishQueryForVietnameseEducationClaims() {
        AcademicEvidenceSearchService service = new AcademicEvidenceSearchService(false, 2, 2);

        String query = service.buildQuery(
                "Học tập cá nhân hóa giúp cải thiện kết quả học tập của sinh viên.",
                "Giáo dục học");

        assertThat(query)
                .contains("personalized learning")
                .contains("academic achievement")
                .contains("students higher education")
                .contains("education research");
    }

    @Test
    void rejectsIrrelevantEvidenceCandidates() {
        AcademicEvidenceSearchService service = new AcademicEvidenceSearchService(false, 2, 2);
        String query = "personalized learning learning outcomes academic achievement students higher education";

        EvidenceSource irrelevantMedicalPaper = new EvidenceSource(
                "C3 glomerulopathy: consensus report",
                "A. Author",
                "2024",
                "Kidney International",
                "",
                "",
                "OpenAlex");
        EvidenceSource relevantEducationPaper = new EvidenceSource(
                "Personalized learning and academic achievement in higher education",
                "B. Author",
                "2024",
                "Computers & Education",
                "",
                "",
                "Crossref");

        assertThat(service.isRelevant(irrelevantMedicalPaper, query)).isFalse();
        assertThat(service.isRelevant(relevantEducationPaper, query)).isTrue();
    }
}
