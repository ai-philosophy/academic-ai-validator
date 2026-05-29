package com.aiacademy.academic;

import java.util.List;

public record AcademicReviewResponse(
        String field,
        String academicLevel,
        String citationStyle,
        String summary,
        RiskLevel overallRisk,
        int riskScore,
        int reviewedClaimCount,
        List<ClaimReview> claims,
        List<ChecklistItem> checklist,
        List<String> sourceSearchQueries,
        List<String> reviewNotes
) {
}
