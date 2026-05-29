package com.aiacademy.academic;

import java.util.List;

public record AcademicChatResponse(
        String assistantMessage,
        boolean usedAiModel,
        String modelProvider,
        String modelName,
        String modelStatus,
        List<String> nextPrompts,
        AcademicReviewResponse review
) {
}
