package com.brainblocks.backend.exception;

// 503: chưa cấu hình GOOGLE_CLIENT_ID hoặc không tải được khóa công khai của Google để kiểm tra chữ ký
public class GoogleSignInUnavailableException extends RuntimeException {
    public GoogleSignInUnavailableException() {
        super("Google sign-in is not available right now");
    }

    public GoogleSignInUnavailableException(Throwable cause) {
        super("Google sign-in is not available right now", cause);
    }
}
