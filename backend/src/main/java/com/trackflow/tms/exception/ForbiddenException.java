package com.trackflow.tms.exception;

import org.springframework.http.HttpStatus;

/** Row-level denial (e.g. not a member of the project), as opposed to a role denial. */
public class ForbiddenException extends ApiException {

    public ForbiddenException(String detail) {
        super(HttpStatus.FORBIDDEN, "Forbidden", detail);
    }

    public ForbiddenException() {
        this(ProblemDetails.FORBIDDEN_DETAIL);
    }
}
