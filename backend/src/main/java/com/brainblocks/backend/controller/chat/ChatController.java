package com.brainblocks.backend.controller.chat;

import com.brainblocks.backend.dto.request.chat.ChatSessionRequest;
import com.brainblocks.backend.dto.request.chat.SendChatMessageRequest;
import com.brainblocks.backend.dto.response.ApiResponse;
import com.brainblocks.backend.dto.response.chat.ChatReplyResponse;
import com.brainblocks.backend.dto.response.chat.ChatSessionDetailResponse;
import com.brainblocks.backend.dto.response.chat.ChatSessionSummaryResponse;
import com.brainblocks.backend.service.ai.ChatService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

// Chatbot tư vấn cho khách hàng; hội thoại của khách khác trả 404 (kiểm tra ở ChatService)
@RestController
@RequestMapping("/api/chat")
@PreAuthorize("hasRole('CUSTOMER')")
@RequiredArgsConstructor
public class ChatController {
    private final ChatService chatService;

    @GetMapping("/sessions")
    public ResponseEntity<ApiResponse<List<ChatSessionSummaryResponse>>> getMySessions() {
        return ResponseEntity.ok(ApiResponse.success(chatService.getMySessions()));
    }

    // body có thể bỏ trống = tư vấn chung, không gắn hồ sơ bé
    @PostMapping("/sessions")
    public ResponseEntity<ApiResponse<ChatSessionDetailResponse>> createSession(
            @Valid @RequestBody(required = false) ChatSessionRequest request) {
        ChatSessionRequest body = request == null ? new ChatSessionRequest(null) : request;
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Chat session created", chatService.createSession(body)));
    }

    @GetMapping("/sessions/{id}")
    public ResponseEntity<ApiResponse<ChatSessionDetailResponse>> getSession(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success(chatService.getSession(id)));
    }

    // đổi hồ sơ bé đang được tư vấn trong hội thoại
    @PatchMapping("/sessions/{id}")
    public ResponseEntity<ApiResponse<ChatSessionSummaryResponse>> updateSession(
            @PathVariable Long id, @Valid @RequestBody ChatSessionRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Chat session updated", chatService.updateSession(id, request)));
    }

    @DeleteMapping("/sessions/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteSession(@PathVariable Long id) {
        chatService.deleteSession(id);
        return ResponseEntity.ok(ApiResponse.success("Chat session deleted", null));
    }

    // gửi câu hỏi và nhận câu trả lời; AI lỗi trả 503, ngữ cảnh đổi giữa chừng trả 409, gửi dồn dập trả 429
    @PostMapping("/sessions/{id}/messages")
    public ResponseEntity<ApiResponse<ChatReplyResponse>> sendMessage(
            @PathVariable Long id, @Valid @RequestBody SendChatMessageRequest request) {
        return ResponseEntity.ok(ApiResponse.success(chatService.sendMessage(id, request)));
    }

    @GetMapping("/suggestions")
    public ResponseEntity<ApiResponse<List<String>>> getSuggestions(
            @RequestParam(required = false) Long childProfileId) {
        return ResponseEntity.ok(ApiResponse.success(chatService.getSuggestions(childProfileId)));
    }
}
