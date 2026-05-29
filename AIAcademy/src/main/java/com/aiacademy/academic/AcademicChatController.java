package com.aiacademy.academic;

import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/academic-chat")
public class AcademicChatController {

    private final AcademicChatService chatService;

    public AcademicChatController(AcademicChatService chatService) {
        this.chatService = chatService;
    }

    @PostMapping
    public ResponseEntity<?> reply(@RequestBody AcademicChatRequest request) {
        if (request == null || request.message() == null || request.message().isBlank()) {
            return ResponseEntity.badRequest()
                    .body(Map.of("message", "Vui lòng nhập nội dung học thuật cần kiểm định."));
        }

        return ResponseEntity.ok(chatService.reply(request));
    }
}
