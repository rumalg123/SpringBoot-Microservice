package com.rumal.search_service.exception;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.stream.Collectors;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(UnauthorizedException.class)
    public ResponseEntity<?> unauthorized(UnauthorizedException ex) {
        log.warn("Unauthorized request: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(error(HttpStatus.UNAUTHORIZED, ex.getMessage()));
    }

    @ExceptionHandler(ValidationException.class)
    public ResponseEntity<?> validation(ValidationException ex) {
        log.warn("Validation error: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error(HttpStatus.BAD_REQUEST, ex.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<?> methodArgumentNotValid(MethodArgumentNotValidException ex) {
        String fieldErrors = ex.getBindingResult().getFieldErrors().stream()
                .map(FieldError::getDefaultMessage)
                .collect(Collectors.joining("; "));
        log.warn("Validation failed: {}", fieldErrors);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error(HttpStatus.BAD_REQUEST, fieldErrors));
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
            log.warn("Request failed with {}: {}", ex.getStatusCode().value(), message);
        } else {
            log.error("Request failed with {}: {}", ex.getStatusCode().value(), message, ex);
        }
        return ResponseEntity.status(ex.getStatusCode()).body(error(ex.getStatusCode(), message));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<?> handleUnexpected(Exception ex) {
        log.error("Unexpected error", ex);
        return ResponseEntity.status(500).body(error(HttpStatus.INTERNAL_SERVER_ERROR, "Unexpected error"));
    }

    private ApiError error(HttpStatusCode statusCode, String message) {
        return new ApiError(Instant.now(), statusCode.value(), message, message);
    }
}
