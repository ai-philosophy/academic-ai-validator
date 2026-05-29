package com.aiacademy.academic;

public record AcademicReviewResult(
        AcademicReviewResponse review,
        boolean usedAiModel,
        String modelProvider,
        String modelName,
        String modelStatus
) {
}
