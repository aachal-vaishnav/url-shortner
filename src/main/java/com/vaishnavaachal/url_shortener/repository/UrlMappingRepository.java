package com.vaishnavaachal.url_shortener.repository;

import com.vaishnavaachal.url_shortener.model.UrlMapping;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * Repository for UrlMapping entity.
 *
 * Uses Spring Data JPA derived queries and JPQL for custom operations.
 * The UNIQUE INDEX on short_code in the entity makes findByShortCode O(1) at the DB level.
 */
@Repository
public interface UrlMappingRepository extends JpaRepository<UrlMapping, Long> {

    /**
     * Primary lookup for redirect operations.
     * Hits the UNIQUE INDEX on short_code for sub-millisecond DB lookup.
     */
    Optional<UrlMapping> findByShortCode(String shortCode);

    /**
     * Deduplication check: returns existing mapping if the long URL was already shortened.
     * Prevents duplicate rows for the same URL.
     */
    Optional<UrlMapping> findByLongUrl(String longUrl);

    /**
     * Atomic click count increment using a JPQL UPDATE.
     * Called asynchronously — does NOT block the redirect response.
     *
     * Interview tip: Using a direct UPDATE avoids the read-then-write (lost update) problem
     * that would occur if we used entity.setClickCount(count + 1).
     */
    @Modifying
    @Query("UPDATE UrlMapping u SET u.clickCount = u.clickCount + 1 WHERE u.shortCode = :shortCode")
    void incrementClickCount(@Param("shortCode") String shortCode);

    /**
     * Checks existence by short code — used by the Bloom Filter fallback validation.
     * More efficient than findByShortCode as it avoids hydrating the full entity.
     */
    boolean existsByShortCode(String shortCode);
}
