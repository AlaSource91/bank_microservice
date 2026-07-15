package com.alaeldin.Auth_service.exception;

public class RefreshTokenNotFoundException  extends RuntimeException{

    public RefreshTokenNotFoundException(String message) {
        super(message);
    }

    public  RefreshTokenNotFoundException(String message, Throwable cause) {
        super(message, cause);
    }
}
