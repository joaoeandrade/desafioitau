package com.example.eventplatform.adapter.in.rest;

import com.example.eventplatform.application.EventLogUnavailableException;
import com.example.eventplatform.domain.model.DomainValidationException;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import java.net.URI;

/**
 * Erros no formato RFC 9457 (Problem Details). Mensagens internas nunca vazam para o cliente
 * em erros 5xx; o traceId (logs/tracing) permite correlacionar a falha.
 */
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);
    private static final String TYPE_BASE = "https://errors.event-platform.example/";

    @ExceptionHandler({DomainValidationException.class, IllegalArgumentException.class})
    ProblemDetail handleValidation(RuntimeException e) {
        return problem(HttpStatus.BAD_REQUEST, "invalid-request", e.getMessage());
    }

    @ExceptionHandler(ConstraintViolationException.class)
    ProblemDetail handleConstraint(ConstraintViolationException e) {
        return problem(HttpStatus.BAD_REQUEST, "invalid-request", e.getMessage());
    }

    @ExceptionHandler(AccessDeniedException.class)
    ProblemDetail handleForbidden(AccessDeniedException e) {
        return problem(HttpStatus.FORBIDDEN, "forbidden", e.getMessage());
    }

    @ExceptionHandler(EventLogUnavailableException.class)
    ResponseEntity<ProblemDetail> handleUnavailable(EventLogUnavailableException e) {
        log.warn("ingestão indisponível: {}", e.getMessage());
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .header(HttpHeaders.RETRY_AFTER, "1")
                .body(problem(HttpStatus.SERVICE_UNAVAILABLE, "temporarily-unavailable",
                        "Não foi possível registrar o evento agora. Reenvie com a mesma Idempotency-Key."));
    }

    @ExceptionHandler(Exception.class)
    ProblemDetail handleUnexpected(Exception e) {
        log.error("erro inesperado", e);
        return problem(HttpStatus.INTERNAL_SERVER_ERROR, "internal-error", "Erro interno.");
    }

    private static ProblemDetail problem(HttpStatus status, String type, String detail) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(status, detail);
        pd.setType(URI.create(TYPE_BASE + type));
        return pd;
    }
}
