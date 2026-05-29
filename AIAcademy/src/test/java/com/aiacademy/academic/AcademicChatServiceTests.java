package com.aiacademy.academic;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class AcademicChatServiceTests {

    private final AcademicChatService chatService = new AcademicChatService(new AcademicReviewOrchestrator(
            new AcademicReviewService(),
            new GitHubModelsAcademicReviewService(
                    "https://models.github.ai/inference",
                    "openai/gpt-4.1",
                    "2026-03-10",
                    "",
                    false),
            new AcademicEvidenceSearchService(false, 2, 2)));

    @Test
    void returnsConversationalReplyWithReviewPayload() {
        AcademicChatResponse response = chatService.reply(new AcademicChatRequest(
                "Nghiên cứu cho thấy 85% sinh viên học tốt hơn khi sử dụng AI trong lớp học.",
                "Giáo dục học",
                "Đại học",
                "APA"));

        assertThat(response.assistantMessage()).contains("Đã kiểm định");
        assertThat(response.usedAiModel()).isFalse();
        assertThat(response.modelProvider()).isEqualTo("Rule-based verifier");
        assertThat(response.nextPrompts()).hasSize(3);
        assertThat(response.review().claims()).hasSize(1);
        assertThat(response.review().claims().get(0).verdict()).isEqualTo(Verdict.POSSIBLE_HALLUCINATION);
    }
}
