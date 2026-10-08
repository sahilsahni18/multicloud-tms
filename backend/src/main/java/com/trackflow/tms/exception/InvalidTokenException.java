package com.trackflow.tms.exception;

import org.springframework.http.HttpStatus;

/** Refresh token missing, unknown, expired or revoked. */
public class InvalidTokenException extends ApiException {

    public InvalidTokenException(String detail) {
        super(HttpStatus.UNAUTHORIZED, "Unauthorized", detail);
    }
}
