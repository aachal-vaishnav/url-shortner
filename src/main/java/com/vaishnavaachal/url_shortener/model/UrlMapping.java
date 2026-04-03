package com.vaishnavaachal.url_shortener.model;


import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * JPA Entity: url_mappings table
 *
 * Schema Design Decisions:
 * 1. id (BIGINT, AUTO_INCREMENT) → used as input to Base62 encoder for guaranteed uniqueness.
 * 2. short_code → UNIQUE INDEX ensures O(1) DB lookup and prevents duplicates.
 * 3. click_count → updated asynchronously via Blind Update pattern.
 * 4. expires_at → nullable; null means the URL never expires.
 *
 * Index strategy (interview-ready):
 *   - PRIMARY KEY on `id` for JPA operations.
 *   - UNIQUE INDEX on `short_code` → the hot read path during redirects.
 *   - INDEX on `long_url` → for deduplication checks before inserting.
 */
@Entity
@Table(
        name = "url_mappings",
        indexes = {
                @Index(name = "idx_short_code", columnList = "short_code", unique = true),
                @Index(name = "idx_long_url",   columnList = "long_url")
        }
)
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UrlMapping {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** The original long URL submitted by the user. Max 2048 chars covers all practical URLs. */
    @Column(name = "long_url", nullable = false, length = 768) // 768 * 4 = 3072 bytes
    private String longUrl;

    /**
     * The 7-character Base62 code derived from the auto-increment ID.
     * Example: id=1 → "0000001", id=3521614606207 → "aaaaaaa"
     */
    @Column(name = "short_code", nullable = false, unique = true, length = 10)
    private String shortCode;

    /** Number of times this short URL has been accessed. Updated asynchronously. */
    @Column(name = "click_count", nullable = false)
    @Builder.Default
    private Long clickCount = 0L;

    /** Timestamp of record creation. Set once, never updated. */
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    /** Optional expiry timestamp. Null = never expires. */
    @Column(name = "expires_at")
    private LocalDateTime expiresAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
    }

    /** Convenience method: checks if this URL mapping has expired. */
    public boolean isExpired() {
        return expiresAt != null && LocalDateTime.now().isAfter(expiresAt);
    }
}
