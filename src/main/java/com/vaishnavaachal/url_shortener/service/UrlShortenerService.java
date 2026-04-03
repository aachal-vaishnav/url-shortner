package com.vaishnavaachal.url_shortener.service;


import com.vaishnavaachal.url_shortener.dto.UrlDtos.*;
import com.vaishnavaachal.url_shortener.exception.UrlExceptions.*;
import com.vaishnavaachal.url_shortener.model.UrlMapping;
import com.vaishnavaachal.url_shortener.repository.UrlMappingRepository;
import com.vaishnavaachal.url_shortener.util.Base62Util;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Optional;

/**
 * Core URL Shortener Service
 *
 * ════════════════════════════════════════════════════════════════════
 * ARCHITECTURE: Cache-Aside Pattern (Lazy Loading)
 * ════════════════════════════════════════════════════════════════════
 *
 * READ PATH (redirect):
 *   1. Check Redis →  HIT  → return long_url (sub-millisecond)
 *                  →  MISS → query MySQL → populate Redis → return long_url
 *
 * WRITE PATH (shorten):
 *   1. Deduplication check (by long_url)
 *   2. Save to MySQL → get auto-increment ID
 *   3. Encode ID → Base62 short_code
 *   4. Update the record with the short_code
 *   5. Populate Redis cache
 *
 * ASYNC PATH (click count):
 *   - Runs in background thread via @Async
 *   - Does NOT block the redirect HTTP response
 *
 * ════════════════════════════════════════════════════════════════════
 * BLOOM FILTER (Simulated)
 * ════════════════════════════════════════════════════════════════════
 * A true Bloom Filter uses Redis Stack's RedisBloom module. Since
 * that requires a specific Redis distribution, we simulate the same
 * behavior here using Base62 format validation as a pre-filter.
 *
 * Real Bloom Filter interview talking points:
 *   - Uses multiple hash functions on the key.
 *   - Stores a compact bit array in Redis.
 *   - No false negatives; small false-positive rate (~1%).
 *   - Prevents cache stampedes and DB lookups for garbage inputs.
 *   - To use: add `redisbloom` to Redis and use the Jedis BF.ADD / BF.EXISTS commands.
 * ════════════════════════════════════════════════════════════════════
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class UrlShortenerService {

    private final UrlMappingRepository repository;
    private final RedisTemplate<String, String> redisTemplate;
    private final Base62Util base62Util;

    @Value("${app.base-url}")
    private String baseUrl;

    private static final String CACHE_PREFIX   = "url:";   // Redis key prefix
    private static final Duration CACHE_TTL    = Duration.ofHours(24);

    // ─────────────────────────────────────────────────────────────────────────
    // 1. SHORTEN A URL
    // ─────────────────────────────────────────────────────────────────────────

    @Transactional
    public ShortenResponse shorten(ShortenRequest request) {
        String longUrl = request.getLongUrl().trim();

        // ── Deduplication: return existing short URL if this long URL was already shortened ──
        Optional<UrlMapping> existing = repository.findByLongUrl(longUrl);
        if (existing.isPresent()) {
            UrlMapping mapping = existing.get();
            log.info("Deduplication hit: returning existing short code [{}] for URL", mapping.getShortCode());
            return buildShortenResponse(mapping);
        }

        // ── Step 1: Save with a placeholder short_code to acquire the auto-increment ID ──
        LocalDateTime expiresAt = request.getExpiryHours() != null
                ? LocalDateTime.now().plusHours(request.getExpiryHours())
                : null;

        UrlMapping mapping = UrlMapping.builder()
                .longUrl(longUrl)
                .shortCode("PENDING")   // Temporary placeholder; will be replaced after ID is known
                .expiresAt(expiresAt)
                .build();

        mapping = repository.save(mapping);

        // ── Step 2: Encode the auto-increment ID to Base62 ──
        String shortCode = base62Util.encode(mapping.getId());
        mapping.setShortCode(shortCode);
        mapping = repository.save(mapping);

        // ── Step 3: Warm the Redis cache immediately (no cache-miss on first access) ──
        cacheUrl(shortCode, longUrl, expiresAt);

        log.info("Shortened URL: id={}, shortCode={}", mapping.getId(), shortCode);
        return buildShortenResponse(mapping);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 2. RESOLVE A SHORT CODE → LONG URL  (The Hot Path)
    // ─────────────────────────────────────────────────────────────────────────

    public String resolve(String shortCode) {

        // ── Bloom Filter Layer: reject malformed codes before touching any store ──
        if (!base62Util.isValidCode(shortCode)) {
            log.warn("Bloom filter rejected invalid short code: {}", shortCode);
            throw new InvalidUrlException(
                    "Invalid short code format: '" + shortCode + "'. Expected 7 alphanumeric characters."
            );
        }

        // ── Cache-Aside Step 1: Check Redis ──
        String cacheKey = CACHE_PREFIX + shortCode;
        String cachedUrl = redisTemplate.opsForValue().get(cacheKey);

        if (cachedUrl != null) {
            log.debug("Cache HIT for shortCode={}", shortCode);
            incrementClickCountAsync(shortCode);   // Non-blocking
            return cachedUrl;
        }

        // ── Cache-Aside Step 2: Cache MISS → query MySQL ──
        log.debug("Cache MISS for shortCode={} — querying MySQL", shortCode);

        UrlMapping mapping = repository.findByShortCode(shortCode)
                .orElseThrow(() -> new UrlNotFoundException(shortCode));

        // ── Expiry Check ──
        if (mapping.isExpired()) {
            evictFromCache(shortCode);
            throw new UrlExpiredException(shortCode);
        }

        // ── Cache-Aside Step 3: Populate Redis cache for future requests ──
        cacheUrl(shortCode, mapping.getLongUrl(), mapping.getExpiresAt());
        incrementClickCountAsync(shortCode);

        return mapping.getLongUrl();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 3. GET STATS FOR A SHORT URL
    // ─────────────────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public UrlStatsResponse getStats(String shortCode) {
        UrlMapping mapping = repository.findByShortCode(shortCode)
                .orElseThrow(() -> new UrlNotFoundException(shortCode));

        return UrlStatsResponse.builder()
                .shortCode(mapping.getShortCode())
                .shortUrl(buildShortUrl(mapping.getShortCode()))
                .longUrl(mapping.getLongUrl())
                .clickCount(mapping.getClickCount())
                .createdAt(mapping.getCreatedAt())
                .expiresAt(mapping.getExpiresAt())
                .expired(mapping.isExpired())
                .build();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 4. DELETE A SHORT URL
    // ─────────────────────────────────────────────────────────────────────────

    @Transactional
    public void delete(String shortCode) {
        UrlMapping mapping = repository.findByShortCode(shortCode)
                .orElseThrow(() -> new UrlNotFoundException(shortCode));

        repository.delete(mapping);
        evictFromCache(shortCode);
        log.info("Deleted short URL: {}", shortCode);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // PRIVATE HELPERS
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Blind Update: increment click count in a background thread.
     *
     * @Async ensures this does NOT block the HTTP redirect response.
     * The user is already being redirected while this DB write happens.
     *
     * executor = "clickCountExecutor" → uses our custom thread pool from AsyncConfig.
     */
    @Async("clickCountExecutor")
    @Transactional
    public void incrementClickCountAsync(String shortCode) {
        try {
            repository.incrementClickCount(shortCode);
            log.debug("Async click count incremented for: {}", shortCode);
        } catch (Exception e) {
            // Non-critical operation: log and swallow to prevent async thread failure
            log.error("Failed to increment click count for {}: {}", shortCode, e.getMessage());
        }
    }

    /**
     * Stores short_code → long_url in Redis.
     * Sets TTL to match the URL's expiry time if one exists; otherwise uses default 24h TTL.
     */
    private void cacheUrl(String shortCode, String longUrl, LocalDateTime expiresAt) {
        String cacheKey = CACHE_PREFIX + shortCode;
        Duration ttl = (expiresAt != null)
                ? Duration.between(LocalDateTime.now(), expiresAt)
                : CACHE_TTL;

        if (!ttl.isNegative() && !ttl.isZero()) {
            redisTemplate.opsForValue().set(cacheKey, longUrl, ttl);
            log.debug("Cached shortCode={} with TTL={}", shortCode, ttl);
        }
    }

    /** Removes a short code from the Redis cache (used on deletion and expiry). */
    private void evictFromCache(String shortCode) {
        redisTemplate.delete(CACHE_PREFIX + shortCode);
        log.debug("Evicted shortCode={} from cache", shortCode);
    }

    private String buildShortUrl(String shortCode) {
        return baseUrl + "/" + shortCode;
    }

    private ShortenResponse buildShortenResponse(UrlMapping mapping) {
        return ShortenResponse.builder()
                .shortCode(mapping.getShortCode())
                .shortUrl(buildShortUrl(mapping.getShortCode()))
                .longUrl(mapping.getLongUrl())
                .createdAt(mapping.getCreatedAt())
                .expiresAt(mapping.getExpiresAt())
                .build();
    }
}

