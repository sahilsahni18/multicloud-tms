package com.trackflow.tms.exception;

import com.trackflow.tms.config.CorrelationIdFilter;
import java.time.Instant;
import org.slf4j.MDC;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;

/** Builds the RFC 7807 bodies used everywhere, with timestamp and correlation id added. */
public final class ProblemDetails {

    public static final String FORBIDDEN_DETAIL = "You do not have permission to perform this action";

    private ProblemDetails() {
    }

    public static ProblemDetail create(HttpStatusCode status, String title, String detail) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setTitle(title);
        return decorate(problem);
    }

    public static ProblemDetail decorate(ProblemDetail problem) {
        problem.setProperty("timestamp", Instant.now().toString());
        String correlationId = MDC.get(CorrelationIdFilter.MDC_KEY);
        if (correlationId != null) {
            problem.setProperty("correlationId", correlationId);
        }
        return problem;
    }
}
