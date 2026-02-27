package com.rumal.admin_service.exception;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(UnauthorizedException.class)
    public ResponseEntity<?> unauthorized(UnauthorizedException ex) {
        log.warn("Unauthorized admin request: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(error(HttpStatus.UNAUTHORIZED, ex.getMessage()));
    }

    @ExceptionHandler(ServiceUnavailableException.class)
    public ResponseEntity<?> serviceUnavailable(ServiceUnavailableException ex) {
        log.warn("Downstream service unavailable: {}", ex.getMessage(), ex);
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(error(HttpStatus.SERVICE_UNAVAILABLE, ex.getMessage()));
    }

    @ExceptionHandler(DownstreamHttpException.class)
    public ResponseEntity<?> downstreamHttp(DownstreamHttpException ex) {
        log.warn("Downstream HTTP error {}: {}", ex.getStatusCode().value(), ex.getMessage(), ex);
        return ResponseEntity.status(ex.getStatusCode()).body(error(ex.getStatusCode(), ex.getMessage()));
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<?> responseStatus(ResponseStatusException ex) {
        String message = ex.getReason() == null ? "Request failed" : ex.getReason();
        if (ex.getStatusCode().is4xxClientError()) {
            log.warn("Request failed with {}: {}", ex.getStatusCode().value(), message, ex);
        } else {
            log.error("Request failed with {}: {}", ex.getStatusCode().value(), message, ex);
        }
        return ResponseEntity.status(ex.getStatusCode()).body(error(ex.getStatusCode(), message));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<?> handleValidation(MethodArgumentNotValidException ex) {
        log.warn("Admin request argument validation failed: {}", ex.getMessage());
        Map<String, Object> body = error(HttpStatus.BAD_REQUEST, "Validation failed");
        Map<String, String> fieldErrors = new LinkedHashMap<>();
        for (FieldError fe : ex.getBindingResult().getFieldErrors()) {
            fieldErrors.putIfAbsent(fe.getField(), fe.getDefaultMessage());
        }
        body.put("fieldErrors", fieldErrors);
        return ResponseEntity.badRequest().body(body);
    }

    @ExceptionHandler(OptimisticLockingFailureException.class)
    public ResponseEntity<?> optimisticLockConflict(OptimisticLockingFailureException ex) {
        log.warn("Optimistic lock conflict: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.CONFLICT).body(error(HttpStatus.CONFLICT, "Resource was modified by another request. Please retry."));
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<?> dataIntegrityViolation(DataIntegrityViolationException ex) {
        log.warn("Data integrity violation: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.CONFLICT).body(error(HttpStatus.CONFLICT, "Resource already exists or data conflict. Please retry."));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleUnexpected(Exception ex) {
        log.error("Unexpected error", ex);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("timestamp", Instant.now());
        body.put("status", 500);
        body.put("error", "Unexpected error");
        body.put("message", "Unexpected error");
        return ResponseEntity.status(500).body(body);
    }

    private Map<String, Object> error(HttpStatusCode statusCode, String message) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("timestamp", Instant.now());
        m.put("status", statusCode.value());
        m.put("error", message);
        m.put("message", message);
        return m;
    }
}
