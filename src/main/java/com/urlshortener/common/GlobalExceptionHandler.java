package com.urlshortener.common;

import com.urlshortener.common.exception.AliasConflictException;
import com.urlshortener.common.exception.CodeGenerationConflictException;
import com.urlshortener.common.exception.InvalidRequestException;
import com.urlshortener.common.exception.InvalidUrlException;
import com.urlshortener.common.exception.LinkAccessDeniedException;
import com.urlshortener.common.exception.LinkDeactivatedException;
import com.urlshortener.common.exception.LinkExpiredException;
import com.urlshortener.common.exception.LinkNotFoundException;
import com.urlshortener.common.exception.RateLimitExceededException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Central error envelope for every controller. Keeps the response shape consistent
 * ({@code {"error": "...", "message": "...", "details": {...}}}) so clients only ever
 * parse one error format, and ensures internal exception details/stack traces never leak.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(LinkNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleNotFound(LinkNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ErrorResponse.of("not_found", ex.getMessage(), Map.of("code", ex.getCode())));
    }

    @ExceptionHandler(AliasConflictException.class)
    public ResponseEntity<ErrorResponse> handleAliasConflict(AliasConflictException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ErrorResponse.of("alias_conflict", ex.getMessage()));
    }

    @ExceptionHandler(LinkExpiredException.class)
    public ResponseEntity<ErrorResponse> handleExpired(LinkExpiredException ex) {
        return ResponseEntity.status(HttpStatus.GONE)
                .body(ErrorResponse.of("link_expired", ex.getMessage(), Map.of("code", ex.getCode())));
    }

    @ExceptionHandler(LinkDeactivatedException.class)
    public ResponseEntity<ErrorResponse> handleDeactivated(LinkDeactivatedException ex) {
        return ResponseEntity.status(HttpStatus.GONE)
                .body(ErrorResponse.of("link_deactivated", ex.getMessage(), Map.of("code", ex.getCode())));
    }

    @ExceptionHandler(LinkAccessDeniedException.class)
    public ResponseEntity<ErrorResponse> handleAccessDenied(LinkAccessDeniedException ex) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(ErrorResponse.of("access_denied", ex.getMessage()));
    }

    @ExceptionHandler(RateLimitExceededException.class)
    public ResponseEntity<ErrorResponse> handleRateLimit(RateLimitExceededException ex) {
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .header(HttpHeaders.RETRY_AFTER, String.valueOf(ex.getRetryAfterSeconds()))
                .body(ErrorResponse.of("rate_limit_exceeded", ex.getMessage()));
    }

    @ExceptionHandler(InvalidUrlException.class)
    public ResponseEntity<ErrorResponse> handleInvalidUrl(InvalidUrlException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ErrorResponse.of("invalid_request", ex.getMessage()));
    }

    @ExceptionHandler(InvalidRequestException.class)
    public ResponseEntity<ErrorResponse> handleInvalidRequest(InvalidRequestException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ErrorResponse.of("invalid_request", ex.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException ex) {
        Map<String, Object> fieldErrors = new LinkedHashMap<>();
        ex.getBindingResult().getFieldErrors().forEach(fe ->
                fieldErrors.put(fe.getField(), fe.getDefaultMessage()));
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ErrorResponse.of("invalid_request", "Request validation failed", fieldErrors));
    }

    @ExceptionHandler(CodeGenerationConflictException.class)
    public ResponseEntity<ErrorResponse> handleCodeConflict(CodeGenerationConflictException ex) {
        log.error("Code generation conflict: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ErrorResponse.of("code_generation_conflict", "Could not generate a unique code, please retry"));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnexpected(Exception ex) {
        log.error("Unhandled exception", ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ErrorResponse.of("internal_error", "An unexpected error occurred"));
    }
}
