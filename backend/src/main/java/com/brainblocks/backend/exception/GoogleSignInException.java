package com.brainblocks.backend.exception;

// 401: ID token Google không hợp lệ, email chưa xác minh hoặc email đã gắn với tài khoản Google khác
public class GoogleSignInException extends RuntimeException {
    public GoogleSignInException(String message) {
        super(message);
    }
}
