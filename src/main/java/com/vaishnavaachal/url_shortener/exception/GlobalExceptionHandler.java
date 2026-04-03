package com.vaishnavaachal.url_shortener.exception;

import com.vaishnavaachal.url_shortener.dto.UrlDtos.ErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.LocalDateTime;
import java.util.stream.Collectors;

/**
 * Global Exception Handler
 *
 * Intercepts ALL exceptions thrown from any @RestController and converts
 * them to consistent JSON error responses using the ErrorResponse DTO.
 *
 * Why @RestControllerAdvice over @ControllerAdvice?
 *   → @RestControllerAdvice = @ControllerAdvice + @ResponseBody
 *   → Automatically serializes returned objects to JSON without @ResponseBody.
 *
 * Covers:
 *  1. UrlNotFoundException     → 404 Not Found
 *  2. UrlExpiredException      → 410 Gone
 *  3. InvalidUrlException      → 400 Bad Request
 *  4. Validation errors        → 400 Bad Request (with field-level details)
 *  5. Any uncaught exception   → 500 Internal Server Error
 */
@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    // ── 404: Short URL not found ────────────────────────────────────────────
    @ExceptionHandler(UrlExceptions.UrlNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleUrlNotFound(
            UrlExceptions.UrlNotFoundException ex,
            HttpServletRequest request) {

        log.warn("URL not found: {}", ex.getMessage());
        return buildResponse(HttpStatus.NOT_FOUND, ex.getMessage(), request.getRequestURI());
    }

    // ── 410: Short URL expired ──────────────────────────────────────────────
    @ExceptionHandler(UrlExceptions.UrlExpiredException.class)
    public ResponseEntity<ErrorResponse> handleUrlExpired(
            UrlExceptions.UrlExpiredException ex,
            HttpServletRequest request) {

        log.warn("Expired URL accessed: {}", ex.getMessage());
        return buildResponse(HttpStatus.GONE, ex.getMessage(), request.getRequestURI());
    }

    // ── 400: Invalid input URL or short code format ─────────────────────────
    @ExceptionHandler(UrlExceptions.InvalidUrlException.class)
    public ResponseEntity<ErrorResponse> handleInvalidUrl(
            UrlExceptions.InvalidUrlException ex,
            HttpServletRequest request) {

        log.warn("Invalid URL input: {}", ex.getMessage());
        return buildResponse(HttpStatus.BAD_REQUEST, ex.getMessage(), request.getRequestURI());
    }

    // ── 400: Hibernate Validator constraint violations ──────────────────────
    // Collects ALL field errors and joins them into a single readable message.
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidationErrors(
            MethodArgumentNotValidException ex,
            HttpServletRequest request) {

        String details = ex.getBindingResult()
                .getFieldErrors()
                .stream()
                .map(FieldError::getDefaultMessage)
                .collect(Collectors.joining("; "));

        log.warn("Validation failed: {}", details);
        return buildResponse(HttpStatus.BAD_REQUEST, "Validation failed: " + details, request.getRequestURI());
    }

    // ── 500: Catch-all for unexpected server errors ─────────────────────────
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGenericException(
            Exception ex,
            HttpServletRequest request) {

        // Log full stack trace for unexpected errors only
        log.error("Unexpected error at [{}]: {}", request.getRequestURI(), ex.getMessage(), ex);
        return buildResponse(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "An unexpected error occurred. Please try again later.",
                request.getRequestURI()
        );
    }

    // ── Shared builder ───────────────────────────────────────────────────────
    private ResponseEntity<ErrorResponse> buildResponse(HttpStatus status, String message, String path) {
        ErrorResponse body = ErrorResponse.builder()
                .status(status.value())
                .error(status.getReasonPhrase())
                .message(message)
                .path(path)
                .timestamp(LocalDateTime.now())
                .build();

        return ResponseEntity.status(status).body(body);
    }
}

