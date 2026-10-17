package com.brainblocks.backend.controller.admin;

import com.brainblocks.backend.dto.request.chat.ChatbotConfigRequest;
import com.brainblocks.backend.dto.response.ApiResponse;
import com.brainblocks.backend.dto.response.chat.ChatbotConfigResponse;
import com.brainblocks.backend.service.ai.ChatbotConfigService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

// Tham số cơ bản của chatbot (đề 2.13). API key không nằm ở đây: chỉ đọc từ biến môi trường phía server.
@RestController
@RequestMapping("/api/admin/chatbot-configs")
@RequiredArgsConstructor
public class AdminChatbotConfigController {
    private final ChatbotConfigService chatbotConfigService;

    @GetMapping
    public ResponseEntity<ApiResponse<List<ChatbotConfigResponse>>> getConfigs() {
        return ResponseEntity.ok(ApiResponse.success(chatbotConfigService.getConfigs()));
    }

    // cấu hình mới luôn active = false, admin bấm kích hoạt riêng
    @PostMapping
    public ResponseEntity<ApiResponse<ChatbotConfigResponse>> createConfig(@Valid @RequestBody ChatbotConfigRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Chatbot config created", chatbotConfigService.createConfig(request)));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<ChatbotConfigResponse>> updateConfig(
            @PathVariable Long id, @Valid @RequestBody ChatbotConfigRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Chatbot config updated", chatbotConfigService.updateConfig(id, request)));
    }

    // bật cấu hình này và tắt mọi cấu hình khác (chỉ một cấu hình active)
    @PatchMapping("/{id}/activate")
    public ResponseEntity<ApiResponse<ChatbotConfigResponse>> activateConfig(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success("Chatbot config activated", chatbotConfigService.activateConfig(id)));
    }
}
