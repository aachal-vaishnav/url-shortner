package com.vaishnavaachal.url_shortener.controller;

import com.vaishnavaachal.url_shortener.dto.UrlDtos.*;
import com.vaishnavaachal.url_shortener.service.UrlShortenerService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;

/**
 * URL Shortener REST Controller
 *
 * Endpoints:
 *   POST   /api/urls              → Shorten a URL
 *   GET    /{shortCode}           → Redirect to original URL (the hot path)
 *   GET    /api/urls/{code}/stats → Analytics for a short URL
 *   DELETE /api/urls/{code}       → Delete a short URL
 *
 * Design notes:
 *   - The redirect endpoint is at the root (/) for clean short URLs: short.ly/abc1234
 *   - API endpoints are namespaced under /api/urls for clarity
 *   - @Valid on @RequestBody triggers Hibernate Validator before the method executes
 *   - HTTP 302 (FOUND) is used for redirects; use 301 only if URLs never change
 */
@RestController
@RequiredArgsConstructor
@Slf4j
@Tag(name = "URL Shortener", description = "Shorten, resolve, and manage short URLs")
public class UrlShortenerController {

    private final UrlShortenerService service;

    // ─────────────────────────────────────────────────────────────────────────
    // POST /api/urls  →  Shorten a long URL
    // ─────────────────────────────────────────────────────────────────────────

    @Operation(
            summary = "Shorten a URL",
            description = "Accepts a long URL and returns a shortened 7-character Base62 code."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "URL shortened successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid URL or request body")
    })
    @PostMapping("/api/urls")
    public ResponseEntity<ShortenResponse> shorten(@Valid @RequestBody ShortenRequest request) {
        ShortenResponse response = service.shorten(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // GET /{shortCode}  →  Redirect (The Hot Path - must be <100ms)
    // ─────────────────────────────────────────────────────────────────────────

    @Operation(
            summary = "Redirect to original URL",
            description = "Resolves a short code and redirects to the original long URL using HTTP 302."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "302", description = "Redirecting to original URL"),
            @ApiResponse(responseCode = "400", description = "Invalid short code format"),
            @ApiResponse(responseCode = "404", description = "Short URL not found"),
            @ApiResponse(responseCode = "410", description = "Short URL has expired")
    })
    @GetMapping("/{shortCode}")
    public ResponseEntity<Void> redirect(
            @Parameter(description = "7-character Base62 short code", example = "0000001")
            @PathVariable String shortCode) {

        String longUrl = service.resolve(shortCode);
        log.info("Redirecting [{}] → {}", shortCode, longUrl);

        return ResponseEntity
                .status(HttpStatus.FOUND)                      // HTTP 302
                .header(HttpHeaders.LOCATION, longUrl)
                .build();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // GET /api/urls/{shortCode}/stats  →  Click analytics
    // ─────────────────────────────────────────────────────────────────────────

    @Operation(
            summary = "Get URL analytics",
            description = "Returns click count, creation date, expiry, and other metadata for a short URL."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Stats returned successfully"),
            @ApiResponse(responseCode = "404", description = "Short URL not found")
    })
    @GetMapping("/api/urls/{shortCode}/stats")
    public ResponseEntity<UrlStatsResponse> getStats(
            @Parameter(description = "7-character Base62 short code")
            @PathVariable String shortCode) {

        return ResponseEntity.ok(service.getStats(shortCode));
    }

    // ─────────────────────────────────────────────────────────────────────────
    // DELETE /api/urls/{shortCode}  →  Remove a short URL
    // ─────────────────────────────────────────────────────────────────────────

    @Operation(summary = "Delete a short URL", description = "Removes a short URL from the database and cache.")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Deleted successfully"),
            @ApiResponse(responseCode = "404", description = "Short URL not found")
    })
    @DeleteMapping("/api/urls/{shortCode}")
    public ResponseEntity<Void> delete(
            @Parameter(description = "7-character Base62 short code")
            @PathVariable String shortCode) {

        service.delete(shortCode);
        return ResponseEntity.noContent().build();   // HTTP 204
    }
}

