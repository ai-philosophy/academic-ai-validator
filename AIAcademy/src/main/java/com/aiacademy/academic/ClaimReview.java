package com.aiacademy.academic;

import java.util.List;

public record ClaimReview(
        String id,
        String text,
        ClaimType type,
        RiskLevel riskLevel,
        Verdict verdict,
        int riskScore,
        List<String> reasons,
        List<String> evidenceSignals,
        String suggestedRevision,
        List<String> reviewQuestions
) {
}
