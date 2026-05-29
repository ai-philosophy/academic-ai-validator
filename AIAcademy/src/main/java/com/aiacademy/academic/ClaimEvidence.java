package com.aiacademy.academic;

import java.util.List;

public record ClaimEvidence(
        String claimId,
        String claimText,
        String searchQuery,
        String status,
        List<EvidenceSource> sources
) {
}
