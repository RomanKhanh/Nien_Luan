package com.brainblocks.backend.exception;

public class FacebookSignInUnavailableException extends RuntimeException {
    public FacebookSignInUnavailableException() {
        super("Facebook sign-in is not available right now, please try again later");
    }
}
