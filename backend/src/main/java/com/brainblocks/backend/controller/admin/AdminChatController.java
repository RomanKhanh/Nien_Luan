package com.brainblocks.backend.controller.admin;

import com.brainblocks.backend.dto.response.ApiResponse;
import com.brainblocks.backend.dto.response.PageResponse;
import com.brainblocks.backend.dto.response.chat.AdminChatSessionDetailResponse;
import com.brainblocks.backend.dto.response.chat.AdminChatSessionSummaryResponse;
import com.brainblocks.backend.dto.response.chat.ChatStatsResponse;
import com.brainblocks.backend.service.ai.ChatService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

// Giám sát chatbot (đề 2.13, 2.14): lịch sử hội thoại của khách và thống kê câu hỏi / chủ đề
@RestController
@RequestMapping("/api/admin/chat")
@RequiredArgsConstructor
public class AdminChatController {
    private final ChatService chatService;

    @GetMapping("/stats")
    public ResponseEntity<ApiResponse<ChatStatsResponse>> getStats() {
        return ResponseEntity.ok(ApiResponse.success(chatService.getStats()));
    }

    // keyword: tên / email khách, tên bé, tiêu đề hoặc nội dung tin nhắn
    @GetMapping("/sessions")
    public ResponseEntity<ApiResponse<PageResponse<AdminChatSessionSummaryResponse>>> getSessions(
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(ApiResponse.success(chatService.getSessionsForAdmin(keyword, page, size)));
    }

    @GetMapping("/sessions/{id}")
    public ResponseEntity<ApiResponse<AdminChatSessionDetailResponse>> getSession(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success(chatService.getSessionForAdmin(id)));
    }
}
