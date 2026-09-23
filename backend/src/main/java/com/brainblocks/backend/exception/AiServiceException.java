package com.brainblocks.backend.exception;

// Sau này tầng AI gọi API lỗi thì throw cái này
public class AiServiceException extends RuntimeException {
    public AiServiceException(String message) {
        super(message);
    }

    public AiServiceException(String message, Throwable cause) {
        super(message, cause);
    }
}
