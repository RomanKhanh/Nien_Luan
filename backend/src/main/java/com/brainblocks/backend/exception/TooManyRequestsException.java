package com.brainblocks.backend.exception;

// 429: gửi quá nhiều yêu cầu trong thời gian ngắn, client chờ rồi thử lại.
public class TooManyRequestsException extends RuntimeException {
    public TooManyRequestsException(String message) {
        super(message);
    }
}
