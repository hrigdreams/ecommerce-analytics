package com.ecommerce.analytics.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.converter.Converter;
import org.springframework.format.FormatterRegistry;
import org.springframework.lang.NonNull;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;

/**
 * PHASE 1 - TIMEZONE STANDARDIZATION, the "parse" half (JacksonConfig
 * handles "serialize"): {@code @RequestParam Instant} arguments
 * (AnalyticsController#getCustomerSummary's from/to, NativeSearchController,
 * etc.) are bound by Spring's default {@code String -> Instant} converter,
 * which only accepts a string with an explicit zone/offset
 * ({@code Instant.parse}, e.g. "2026-10-13T09:45:00Z" or
 * "...+05:45") and throws on a bare local timestamp like
 * "2026-10-13T15:30:00". That default is correct and is left alone for any
 * string that already carries an offset.
 * <p>
 * This registers one addition: when the incoming string has NO
 * offset/zone, it is interpreted as Asia/Kathmandu local time rather than
 * rejected -- so a frontend date-time picker that only knows about local
 * wall-clock time (no offset) still binds correctly, instead of needing to
 * manually append "+05:45" on every request.
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/v1/**")
                .allowedOrigins("http://localhost:5173")
                .allowedMethods("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS")
                .allowedHeaders("*")
                .allowCredentials(true);
    }

    @Override
    public void addFormatters(@NonNull FormatterRegistry registry) {
        registry.addConverter(new KathmanduLenientInstantConverter());
    }

    /**
     * Tries the standard offset-aware Instant.parse first (unchanged
     * behavior for any client that already sends a correct offset).
     * Only on failure does it retry as a zone-less local date-time,
     * anchored to {@link JacksonConfig#APP_ZONE}.
     */
    static final class KathmanduLenientInstantConverter implements Converter<String, Instant> {
        @Override
        public Instant convert(@NonNull String source) {
            String trimmed = source.trim();
            try {
                return Instant.parse(trimmed);
            } catch (DateTimeParseException offsetParseFailed) {
                return LocalDateTime.parse(trimmed).atZone(JacksonConfig.APP_ZONE).toInstant();
            }
        }
    }
}
