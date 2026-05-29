package com.aiacademy.academic;

public record AcademicReviewRequest(
        String content,
        String field,
        String academicLevel,
        String citationStyle
) {
}
