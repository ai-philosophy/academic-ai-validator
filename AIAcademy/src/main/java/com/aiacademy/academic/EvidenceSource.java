package com.aiacademy.academic;

public record EvidenceSource(
        String title,
        String authors,
        String year,
        String venue,
        String doi,
        String url,
        String source
) {
}
