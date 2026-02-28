package com.rumal.customer_service.exception;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import com.rumal.customer_service.auth.KeycloakRequestException;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<?> notFound(ResourceNotFoundException ex) {
        log.warn("Customer resource not found: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(error(HttpStatus.NOT_FOUND, ex.getMessage()));
    }

    @ExceptionHandler(DuplicateResourceException.class)
    public ResponseEntity<?> conflict(DuplicateResourceException ex) {
        log.warn("Customer duplicate resource: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.CONFLICT).body(error(HttpStatus.CONFLICT, ex.getMessage()));
    }

    @ExceptionHandler(UnauthorizedException.class)
    public ResponseEntity<?> unauthorized(UnauthorizedException ex) {
        log.warn("Customer unauthorized request: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(error(HttpStatus.UNAUTHORIZED, ex.getMessage()));
    }

    @ExceptionHandler(ValidationException.class)
    public ResponseEntity<?> validationException(ValidationException ex) {
        log.warn("Customer validation failed: {}", ex.getMessage());
        return ResponseEntity.badRequest().body(error(HttpStatus.BAD_REQUEST, ex.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<?> validation(MethodArgumentNotValidException ex) {
        log.warn("Customer request argument validation failed: {}", ex.getMessage());
        Map<String, String> fieldErrors = new LinkedHashMap<>();

        for (FieldError fe : ex.getBindingResult().getFieldErrors()) {
            fieldErrors.putIfAbsent(fe.getField(), fe.getDefaultMessage());
        }

        return ResponseEntity.badRequest().body(new ApiError(Instant.now(), 400, "Validation failed", "Validation failed", fieldErrors));
    }

    @ExceptionHandler(KeycloakRequestException.class)
    public ResponseEntity<?> keycloakError(KeycloakRequestException ex) {
        log.error("Keycloak request failed: {}", ex.getMessage(), ex);
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(error(HttpStatus.BAD_GATEWAY, ex.getMessage()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<?> handleUnexpected(Exception ex) {
        log.error("Unexpected error", ex);
        return ResponseEntity.status(500).body(error(HttpStatus.INTERNAL_SERVER_ERROR, "Unexpected error"));
    }

    private ApiError error(HttpStatus status, String message) {
        return new ApiError(Instant.now(), status.value(), message, message);
    }
}
