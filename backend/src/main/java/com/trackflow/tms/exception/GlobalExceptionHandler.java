package com.trackflow.tms.exception;

import jakarta.validation.ConstraintViolationException;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.mapping.PropertyReferenceException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.lang.NonNull;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * Turns every exception into application/problem+json (RFC 7807). Spring MVC's
 * own errors (405, 415, malformed JSON...) come through the base class and are
 * decorated the same way.
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    @ExceptionHandler(ApiException.class)
    public ProblemDetail handleApiException(ApiException ex) {
        return ProblemDetails.create(ex.getStatus(), ex.getTitle(), ex.getMessage());
    }

    /** Login failures share one message so the API never reveals which emails exist. */
    @ExceptionHandler({BadCredentialsException.class, UsernameNotFoundException.class, DisabledException.class})
    public ProblemDetail handleBadCredentials(AuthenticationException ex) {
        return ProblemDetails.create(HttpStatus.UNAUTHORIZED, "Unauthorized", "Invalid email or password");
    }

    @ExceptionHandler(AuthenticationException.class)
    public ProblemDetail handleAuthentication(AuthenticationException ex) {
        return ProblemDetails.create(HttpStatus.UNAUTHORIZED, "Unauthorized",
                "Authentication is required to access this resource");
    }

    /** @PreAuthorize denials surface here (before the security filter sees them). */
    @ExceptionHandler(AccessDeniedException.class)
    public ProblemDetail handleAccessDenied(AccessDeniedException ex) {
        return ProblemDetails.create(HttpStatus.FORBIDDEN, "Forbidden", ProblemDetails.FORBIDDEN_DETAIL);
    }

    @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
    public ProblemDetail handleOptimisticLock(ObjectOptimisticLockingFailureException ex) {
        return ProblemDetails.create(HttpStatus.CONFLICT, "Conflict",
                "This record was changed by someone else. Reload it and try again.");
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ProblemDetail handleDataIntegrity(DataIntegrityViolationException ex) {
        log.warn("Data integrity violation: {}", ex.getMostSpecificCause().getMessage());
        return ProblemDetails.create(HttpStatus.CONFLICT, "Conflict", "The request conflicts with existing data");
    }

    /** e.g. ?sort=nonsense on a paged endpoint. */
    @ExceptionHandler(PropertyReferenceException.class)
    public ProblemDetail handleBadSortProperty(PropertyReferenceException ex) {
        return ProblemDetails.create(HttpStatus.BAD_REQUEST, "Bad request",
                "Unknown sort or filter property '" + ex.getPropertyName() + "'");
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ProblemDetail handleConstraintViolation(ConstraintViolationException ex) {
        ProblemDetail problem = ProblemDetails.create(HttpStatus.BAD_REQUEST, "Validation failed",
                "One or more parameters are invalid");
        problem.setProperty("errors", ex.getConstraintViolations().stream()
                .map(v -> Map.of("field", v.getPropertyPath().toString(), "message", v.getMessage()))
                .toList());
        return problem;
    }

    @ExceptionHandler(Exception.class)
    public ProblemDetail handleUnexpected(Exception ex) {
        log.error("Unhandled exception", ex);
        return ProblemDetails.create(HttpStatus.INTERNAL_SERVER_ERROR, "Internal server error",
                "Something went wrong. Quote the correlationId when reporting this.");
    }

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(@NonNull MethodArgumentNotValidException ex,
                                                                  @NonNull HttpHeaders headers,
                                                                  @NonNull HttpStatusCode status,
                                                                  @NonNull WebRequest request) {
        ProblemDetail problem = ProblemDetails.create(HttpStatus.BAD_REQUEST, "Validation failed",
                "One or more fields are invalid");
        List<Map<String, String>> errors = ex.getBindingResult().getFieldErrors().stream()
                .map(error -> Map.of(
                        "field", error.getField(),
                        "message", error.getDefaultMessage() == null ? "is invalid" : error.getDefaultMessage()))
                .toList();
        problem.setProperty("errors", errors);
        return ResponseEntity.badRequest().body(problem);
    }

    @Override
    protected ResponseEntity<Object> createResponseEntity(Object body, @NonNull HttpHeaders headers,
                                                          @NonNull HttpStatusCode statusCode,
                                                          @NonNull WebRequest request) {
        if (body instanceof ProblemDetail problem) {
            ProblemDetails.decorate(problem);
        }
        return super.createResponseEntity(body, headers, statusCode, request);
    }
}
