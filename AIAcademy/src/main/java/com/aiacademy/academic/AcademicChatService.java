package com.aiacademy.academic;

import java.util.List;

import org.springframework.stereotype.Service;

@Service
public class AcademicChatService {

    private final AcademicReviewOrchestrator reviewOrchestrator;

    public AcademicChatService(AcademicReviewOrchestrator reviewOrchestrator) {
        this.reviewOrchestrator = reviewOrchestrator;
    }

    public AcademicChatResponse reply(AcademicChatRequest request) {
        AcademicReviewResult result = reviewOrchestrator.review(new AcademicReviewRequest(
                request.message(),
                request.field(),
                request.academicLevel(),
                request.citationStyle()));
        AcademicReviewResponse review = result.review();

        long highRiskClaims = review.claims().stream()
                .filter(claim -> claim.riskLevel() == RiskLevel.HIGH || claim.riskLevel() == RiskLevel.CRITICAL)
                .count();
        long missingCitationClaims = review.claims().stream()
                .filter(claim -> claim.verdict() == Verdict.MISSING_CITATION
                        || claim.verdict() == Verdict.POSSIBLE_HALLUCINATION)
                .count();

        String assistantMessage = buildAssistantMessage(review, highRiskClaims, missingCitationClaims);
        List<String> nextPrompts = List.of(
                "Hãy viết lại các claim rủi ro cao theo văn phong học thuật thận trọng.",
                "Tạo danh sách nguồn cần tìm cho từng claim.",
                "Chỉ giữ lại các ý có thể dùng trong bài học thuật.");

        return new AcademicChatResponse(
                assistantMessage,
                result.usedAiModel(),
                result.modelProvider(),
                result.modelName(),
                result.modelStatus(),
                nextPrompts,
                review);
    }

    private String buildAssistantMessage(AcademicReviewResponse review, long highRiskClaims,
            long missingCitationClaims) {
        if (review.reviewedClaimCount() == 0) {
            return "Mình chưa tách được claim học thuật rõ ràng từ tin nhắn này. Bạn có thể gửi một đoạn dài hơn hoặc dán trực tiếp phần nội dung AI cần kiểm định.";
        }

        String riskLabel = switch (review.overallRisk()) {
            case LOW -> "thấp";
            case MEDIUM -> "trung bình";
            case HIGH -> "cao";
            case CRITICAL -> "rất cao";
        };

        String message = "Đã kiểm định " + review.reviewedClaimCount() + " claim. Mức rủi ro: " + riskLabel + ".";

        if (highRiskClaims > 0 || missingCitationClaims > 0) {
            return message + " Ưu tiên xem " + highRiskClaims + " claim rủi ro cao và "
                    + missingCitationClaims + " claim cần kiểm tra nguồn/citation.";
        }

        return message + " Chưa thấy claim rủi ro cao, nhưng vẫn nên đối chiếu nguồn cho các ý trọng tâm.";
    }
}
