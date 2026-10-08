package com.trackflow.tms.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

/** Base for business errors that map straight to an HTTP status and a problem+json body. */
@Getter
public abstract class ApiException extends RuntimeException {

    private final HttpStatus status;
    private final String title;

    protected ApiException(HttpStatus status, String title, String detail) {
        super(detail);
        this.status = status;
        this.title = title;
    }
}
