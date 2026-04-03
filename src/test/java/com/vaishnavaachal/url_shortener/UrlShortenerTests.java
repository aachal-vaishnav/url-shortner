package com.vaishnavaachal.url_shortener;

import com.vaishnavaachal.url_shortener.dto.UrlDtos.*;
import com.vaishnavaachal.url_shortener.exception.UrlExceptions.*;
import com.vaishnavaachal.url_shortener.model.UrlMapping;
import com.vaishnavaachal.url_shortener.repository.UrlMappingRepository;
import com.vaishnavaachal.url_shortener.service.UrlShortenerService;
import com.vaishnavaachal.url_shortener.util.Base62Util;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.context.properties.bind.Nested;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;


/**
 * Unit Tests — URL Shortener
 *
 * Tests are grouped by component:
 *   1. Base62UtilTest         → encoding, decoding, edge cases
 *   2. UrlShortenerServiceTest → service logic with mocked dependencies
 *
 * Framework: JUnit 5 + Mockito + AssertJ
 *   - @ExtendWith(MockitoExtension.class) enables Mockito injection
 *   - AssertJ provides fluent assertions (more readable than JUnit's assertEquals)
 */
public class UrlShortenerTests {

    // ══════════════════════════════════════════════════════════════════════
    // 1. BASE62 UTILITY TESTS
    // ══════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Base62Util Tests")
    class Base62UtilTest {

        private final Base62Util base62Util = new Base62Util();

        @Test
        @DisplayName("Encode ID 1 produces a 7-character string starting with zeros")
        void encode_idOne_returns7CharCode() {
            String code = base62Util.encode(1L);
            assertThat(code).hasSize(7);
            assertThat(code).isEqualTo("0000001");
        }

        @Test
        @DisplayName("Encode and decode are inverse operations")
        void encodeAndDecode_areInverse() {
            long originalId = 999_999L;
            String encoded = base62Util.encode(originalId);
            long decoded = base62Util.decode(encoded);
            assertThat(decoded).isEqualTo(originalId);
        }

        @Test
        @DisplayName("Different IDs produce different codes (no collisions)")
        void encode_differentIds_differentCodes() {
            String code1 = base62Util.encode(1L);
            String code2 = base62Util.encode(2L);
            String code3 = base62Util.encode(100_000L);
            assertThat(code1).isNotEqualTo(code2);
            assertThat(code2).isNotEqualTo(code3);
        }

        @Test
        @DisplayName("Encode maximum 7-char capacity: 62^7 - 1")
        void encode_maxCapacity_stillSevenChars() {
            long maxId = (long) Math.pow(62, 7) - 1;   // 3,521,614,606,206
            String code = base62Util.encode(maxId);
            assertThat(code).hasSize(7);
        }

        @Test
        @DisplayName("Encode with non-positive ID throws IllegalArgumentException")
        void encode_nonPositiveId_throwsException() {
            assertThatThrownBy(() -> base62Util.encode(0L))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("positive");

            assertThatThrownBy(() -> base62Util.encode(-5L))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("isValidCode returns false for wrong length or invalid chars")
        void isValidCode_invalidInput_returnsFalse() {
            assertThat(base62Util.isValidCode("abc")).isFalse();        // Too short
            assertThat(base62Util.isValidCode("abc!@#$")).isFalse();    // Invalid chars
            assertThat(base62Util.isValidCode(null)).isFalse();         // Null
            assertThat(base62Util.isValidCode("0000001")).isTrue();     // Valid
        }
    }

    // ══════════════════════════════════════════════════════════════════════
    // 2. URL SHORTENER SERVICE TESTS
    // ══════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("UrlShortenerService Tests")
    @ExtendWith(MockitoExtension.class)
    class UrlShortenerServiceTest {

        @Mock private UrlMappingRepository repository;
        @Mock private RedisTemplate<String, String> redisTemplate;
        @Mock private ValueOperations<String, String> valueOps;
        @InjectMocks private UrlShortenerService service;

        private final Base62Util base62Util = new Base62Util();

        @BeforeEach
        void setup() {
            // Inject real Base62Util (no need to mock a pure utility)
            ReflectionTestUtils.setField(service, "base62Util", base62Util);
            ReflectionTestUtils.setField(service, "baseUrl", "http://short.ly");

            when(redisTemplate.opsForValue()).thenReturn(valueOps);
        }

        @Test
        @DisplayName("shorten() saves new URL and returns ShortenResponse with short URL")
        void shorten_newUrl_returnsResponse() {
            ShortenRequest request = new ShortenRequest("https://www.example.com/very/long/path", null);

            UrlMapping savedMapping = UrlMapping.builder()
                    .id(1L)
                    .longUrl("https://www.example.com/very/long/path")
                    .shortCode("0000001")
                    .createdAt(LocalDateTime.now())
                    .build();

            when(repository.findByLongUrl(anyString())).thenReturn(Optional.empty());
            when(repository.save(any(UrlMapping.class)))
                    .thenAnswer(inv -> {
                        UrlMapping m = inv.getArgument(0);
                        m.setId(1L);
                        return m;
                    })
                    .thenReturn(savedMapping);

            ShortenResponse response = service.shorten(request);

            assertThat(response.getShortCode()).isEqualTo("0000001");
            assertThat(response.getShortUrl()).isEqualTo("http://short.ly/0000001");
            assertThat(response.getLongUrl()).isEqualTo("https://www.example.com/very/long/path");
            verify(repository, times(2)).save(any(UrlMapping.class));
        }

        @Test
        @DisplayName("shorten() returns existing response on duplicate URL (deduplication)")
        void shorten_duplicateUrl_returnsCachedMapping() {
            UrlMapping existing = UrlMapping.builder()
                    .id(5L).shortCode("0000005")
                    .longUrl("https://duplicate.com")
                    .createdAt(LocalDateTime.now())
                    .build();

            when(repository.findByLongUrl("https://duplicate.com")).thenReturn(Optional.of(existing));

            ShortenResponse response = service.shorten(new ShortenRequest("https://duplicate.com", null));

            assertThat(response.getShortCode()).isEqualTo("0000005");
            verify(repository, never()).save(any()); // No new row should be inserted
        }

        @Test
        @DisplayName("resolve() returns long URL from Redis cache (cache HIT)")
        void resolve_cacheHit_returnsCachedUrl() {
            when(valueOps.get("url:0000001")).thenReturn("https://www.example.com");

            String result = service.resolve("0000001");

            assertThat(result).isEqualTo("https://www.example.com");
            verifyNoInteractions(repository);  // DB should NOT be called on cache hit
        }

        @Test
        @DisplayName("resolve() queries MySQL on cache MISS and populates Redis")
        void resolve_cacheMiss_queriesMysqlAndCachesResult() {
            UrlMapping mapping = UrlMapping.builder()
                    .id(1L).shortCode("0000001")
                    .longUrl("https://from-db.com")
                    .createdAt(LocalDateTime.now())
                    .build();

            when(valueOps.get("url:0000001")).thenReturn(null);           // Cache miss
            when(repository.findByShortCode("0000001")).thenReturn(Optional.of(mapping));

            String result = service.resolve("0000001");

            assertThat(result).isEqualTo("https://from-db.com");
            verify(valueOps).set(eq("url:0000001"), eq("https://from-db.com"), any()); // Cache populated
        }

        @Test
        @DisplayName("resolve() throws UrlNotFoundException for unknown short code")
        void resolve_unknownCode_throwsNotFoundException() {
            when(valueOps.get(anyString())).thenReturn(null);
            when(repository.findByShortCode("zzzzzzz")).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.resolve("zzzzzzz"))
                    .isInstanceOf(UrlNotFoundException.class)
                    .hasMessageContaining("zzzzzzz");
        }

        @Test
        @DisplayName("resolve() throws InvalidUrlException for malformed short code (Bloom Filter)")
        void resolve_malformedCode_throwsInvalidUrlException() {
            assertThatThrownBy(() -> service.resolve("!INVALID"))
                    .isInstanceOf(InvalidUrlException.class)
                    .hasMessageContaining("Invalid short code format");

            verifyNoInteractions(repository);  // DB never touched for garbage input
        }

        @Test
        @DisplayName("resolve() throws UrlExpiredException for an expired URL")
        void resolve_expiredUrl_throwsExpiredException() {
            UrlMapping expired = UrlMapping.builder()
                    .id(2L).shortCode("0000002")
                    .longUrl("https://expired.com")
                    .expiresAt(LocalDateTime.now().minusHours(1))  // Expired 1 hour ago
                    .createdAt(LocalDateTime.now().minusDays(2))
                    .build();

            when(valueOps.get("url:0000002")).thenReturn(null);
            when(repository.findByShortCode("0000002")).thenReturn(Optional.of(expired));

            assertThatThrownBy(() -> service.resolve("0000002"))
                    .isInstanceOf(UrlExpiredException.class)
                    .hasMessageContaining("expired");
        }
    }
}
