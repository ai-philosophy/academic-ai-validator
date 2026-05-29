package com.aiacademy.academic;

public record AcademicChatRequest(
        String message,
        String field,
        String academicLevel,
        String citationStyle
) {
}
