package com.brainblocks.backend.exception;

// 409: thao tác xung đột với một lần xử lý khác, client có thể tải lại rồi thử lại.
public class ChatbotConflictException extends RuntimeException {
    public ChatbotConflictException(String message) {
        super(message);
    }
}
