package com.vaishnavaachal.url_shortener.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.validator.constraints.URL;

import java.time.LocalDateTime;

/**
 * Data Transfer Objects (DTOs)
 *
 * All API request/response objects are defined here.
 * DTOs decouple the API contract from the internal entity model (SOLID: ISP).
 * Using separate files per DTO is industry preference; they're grouped here
 * as nested static classes for single-file readability in this submission.
 */
public class UrlDtos {

    // ─────────────────────────────────────────────────────────────────────────
    // REQUEST DTOs
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Payload for POST /api/urls
     * Hibernate Validator annotations enforce input contract at the API boundary.
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class ShortenRequest {

        @NotBlank(message = "URL must not be blank.")
        @URL(message = "Please provide a valid URL (must start with http:// or https://).")
        private String longUrl;

        /**
         * Optional: expiry in hours. Null means the URL never expires.
         * Example: 24 → expires after 24 hours.
         */
        @Positive(message = "Expiry hours must be a positive number.")
        private Integer expiryHours;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // RESPONSE DTOs
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Response for successful URL shortening.
     * @JsonInclude(NON_NULL) omits null fields (e.g., expiresAt) from the JSON output.
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class ShortenResponse {
        private String  shortCode;
        private String  shortUrl;       // Full clickable URL: base-url + "/" + shortCode
        private String  longUrl;
        private LocalDateTime createdAt;
        private LocalDateTime expiresAt; // Null if the URL never expires
    }

    /**
     * Analytics response for GET /api/urls/{shortCode}/stats
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class UrlStatsResponse {
        private String  shortCode;
        private String  shortUrl;
        private String  longUrl;
        private Long    clickCount;
        private LocalDateTime createdAt;
        private LocalDateTime expiresAt;
        private Boolean expired;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // ERROR RESPONSE DTO
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Standard JSON error envelope returned by the Global Exception Handler.
     *
     * Example output:
     * {
     *   "status": 404,
     *   "error": "Not Found",
     *   "message": "Short URL 'abc1234' not found.",
     *   "path": "/abc1234",
     *   "timestamp": "2026-06-01T10:30:00"
     * }
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ErrorResponse {
        private int    status;
        private String error;
        private String message;
        private String path;
        private LocalDateTime timestamp;
    }
}
