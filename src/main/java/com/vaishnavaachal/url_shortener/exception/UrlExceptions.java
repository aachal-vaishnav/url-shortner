package com.vaishnavaachal.url_shortener.exception;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Custom Domain Exceptions
 *
 * Grouped in one file for readability. In a larger project, each exception
 * would have its own file under the .exception package.
 *
 * Design pattern: Each exception carries an HTTP status annotation.
 * The GlobalExceptionHandler reads this annotation to set the response code.
 */
public class UrlExceptions {

    /**
     * Thrown when a requested short code does not exist in Redis or MySQL.
     * Maps to HTTP 404 Not Found.
     */
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public static class UrlNotFoundException extends RuntimeException {
        public UrlNotFoundException(String shortCode) {
            super("Short URL '" + shortCode + "' not found. It may have been deleted or never existed.");
        }
    }

    /**
     * Thrown when the provided long URL fails format validation, or when
     * a short code fails Base62 format check (e.g., wrong length, bad chars).
     * Maps to HTTP 400 Bad Request.
     */
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public static class InvalidUrlException extends RuntimeException {
        public InvalidUrlException(String message) {
            super(message);
        }
    }

    /**
     * Thrown when a short URL is found but its expiry timestamp has passed.
     * Maps to HTTP 410 Gone — semantically correct: resource existed but is no longer available.
     */
    @ResponseStatus(HttpStatus.GONE)
    public static class UrlExpiredException extends RuntimeException {
        public UrlExpiredException(String shortCode) {
            super("Short URL '" + shortCode + "' has expired and is no longer available.");
        }
    }
}
