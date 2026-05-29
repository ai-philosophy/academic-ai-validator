package com.aiacademy.academic;

import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/academic-review")
public class AcademicReviewController {

    private final AcademicReviewOrchestrator reviewOrchestrator;

    public AcademicReviewController(AcademicReviewOrchestrator reviewOrchestrator) {
        this.reviewOrchestrator = reviewOrchestrator;
    }

    @PostMapping
    public ResponseEntity<?> review(@RequestBody AcademicReviewRequest request) {
        if (request == null || request.content() == null || request.content().isBlank()) {
            return ResponseEntity.badRequest()
                    .body(Map.of("message", "Vui lòng nhập nội dung học thuật cần kiểm định."));
        }

        return ResponseEntity.ok(reviewOrchestrator.review(request).review());
    }
}
