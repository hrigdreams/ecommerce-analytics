package com.ecommerce.analytics.config;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * PHASE 1, item 1 (timezone standardization) - covers WebConfig's
 * String -> Instant converter directly (no Spring context needed, it's a
 * plain Converter implementation).
 */
class WebConfigKathmanduConverterTest {

    private final WebConfig.KathmanduLenientInstantConverter converter =
            new WebConfig.KathmanduLenientInstantConverter();

    @Test
    void offsetAwareString_shouldParseExactlyAsInstantParseWould() {
        Instant result = converter.convert("2026-10-13T09:45:00Z");
        assertEquals(Instant.parse("2026-10-13T09:45:00Z"), result);
    }

    @Test
    void explicitKathmanduOffset_shouldParseExactlyAsInstantParseWould() {
        Instant result = converter.convert("2026-10-13T15:30:00+05:45");
        assertEquals(Instant.parse("2026-10-13T15:30:00+05:45"), result);
    }

    @Test
    void zoneLessLocalString_shouldBeInterpretedAsKathmanduLocalTime() {
        // 2026-10-13T15:30:00 local Kathmandu time == 2026-10-13T09:45:00Z
        // (Asia/Kathmandu is a fixed UTC+05:45 offset, no DST).
        Instant result = converter.convert("2026-10-13T15:30:00");
        assertEquals(Instant.parse("2026-10-13T09:45:00Z"), result);
    }

    @Test
    void garbageString_shouldStillThrow() {
        assertThrows(Exception.class, () -> converter.convert("not-a-timestamp"));
    }
}
