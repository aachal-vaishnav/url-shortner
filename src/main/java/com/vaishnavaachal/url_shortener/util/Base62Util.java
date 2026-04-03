package com.vaishnavaachal.url_shortener.util;

import org.springframework.stereotype.Component;

/**
 * Base62 Encoding/Decoding Utility
 *
 * ════════════════════════════════════════════════════════════════
 * HOW BASE62 WORKS (Interview Explanation)
 * ════════════════════════════════════════════════════════════════
 *
 * Alphabet: 62 characters → [0-9][a-z][A-Z]
 *   Position: 0-9=digits, 10-35=lowercase, 36-61=uppercase
 *
 * Encoding: Convert a base-10 number to base-62 (like how we convert
 *           decimal to binary, but with base 62).
 *
 * Example:  id = 125
 *   125 / 62 = 2 remainder 1  → ALPHABET[1] = '1'
 *     2 / 62 = 0 remainder 2  → ALPHABET[2] = '2'
 *   Result (reversed) = "21"  → padded to 7 chars = "0000021"
 *
 * Capacity: 62^7 = 3,521,614,606,207 unique codes (~3.5 trillion)
 *           This handles any real-world URL shortener scale.
 *
 * Collision-free guarantee: Because each DB row gets a unique
 *   auto-increment ID, and we encode THAT ID, two different URLs
 *   can never receive the same short code.
 *
 * ════════════════════════════════════════════════════════════════
 */
@Component
public class Base62Util {

    private static final String ALPHABET = "0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ";
    private static final int    BASE     = ALPHABET.length(); // 62
    private static final int    LENGTH   = 7;                 // Target code length

    /**
     * Encodes a long ID into a 7-character Base62 string.
     *
     * @param id  auto-increment primary key from MySQL (must be > 0)
     * @return    7-character Base62 short code, left-padded with '0'
     * @throws    IllegalArgumentException if id is non-positive
     */
    public String encode(long id) {
        if (id <= 0) {
            throw new IllegalArgumentException("ID must be a positive number. Received: " + id);
        }

        StringBuilder sb = new StringBuilder();
        long remaining = id;

        // Core Base62 conversion: repeatedly divide by 62, collect remainders
        while (remaining > 0) {
            sb.append(ALPHABET.charAt((int) (remaining % BASE)));
            remaining /= BASE;
        }

        // Reverse because remainders are collected least-significant-digit first
        sb.reverse();

        // Left-pad with '0' to ensure consistent 7-character length
        while (sb.length() < LENGTH) {
            sb.insert(0, '0');
        }

        return sb.toString();
    }

    /**
     * Decodes a Base62 short code back to its original numeric ID.
     * Useful for analytics, debugging, or admin lookups.
     *
     * @param code  7-character Base62 short code
     * @return      original database ID
     * @throws      IllegalArgumentException if code contains invalid characters
     */
    public long decode(String code) {
        if (code == null || code.isBlank()) {
            throw new IllegalArgumentException("Short code must not be null or blank.");
        }

        long result = 0;
        for (char c : code.toCharArray()) {
            int charIndex = ALPHABET.indexOf(c);
            if (charIndex == -1) {
                throw new IllegalArgumentException(
                        "Invalid character '" + c + "' in short code: " + code
                );
            }
            // Horner's method: result = result * base + digit
            result = result * BASE + charIndex;
        }

        return result;
    }

    /**
     * Validates that a string is a well-formed Base62 code of the expected length.
     * Used to fail fast before hitting Redis or MySQL with garbage input.
     */
    public boolean isValidCode(String code) {
        if (code == null || code.length() != LENGTH) return false;
        for (char c : code.toCharArray()) {
            if (ALPHABET.indexOf(c) == -1) return false;
        }
        return true;
    }
}
