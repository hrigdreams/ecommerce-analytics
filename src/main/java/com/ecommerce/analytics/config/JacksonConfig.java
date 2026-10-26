package com.ecommerce.analytics.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.ZoneId;
import java.util.TimeZone;

/**
 * This {@code ObjectMapper} bean is what every REST response/request in the
 * app goes through -- Spring Boot's JacksonAutoConfiguration backs off and
 * reuses it (its own auto-configured ObjectMapper is
 * {@code @ConditionalOnMissingBean}), so this is the single place that
 * controls JSON date/time formatting for the whole API.
 * <p>
 * PHASE 1 - TIMEZONE STANDARDIZATION (Asia/Kathmandu, UTC+05:45):
 * <p>
 * {@code setTimeZone(...)} alone does NOT change how a plain
 * {@code java.time.Instant} is serialized -- an Instant has no zone of its
 * own, and jackson-datatype-jsr310's default InstantSerializer always
 * writes it as a UTC "Z" string regardless of the mapper's configured
 * TimeZone. That default is exactly why charts/payloads were showing
 * UTC-offset timestamps instead of Kathmandu time. The fix is
 * {@link SerializationFeature#WRITE_DATES_WITH_CONTEXT_TIME_ZONE}: once
 * enabled, the JSR-310 serializers (Instant included) use the mapper's
 * configured TimeZone to render the offset, instead of always UTC. So every
 * {@code Instant} field in every DTO in this project (OrderResponse,
 * UnifiedAnalyticsResponse, SimulatorStatus, ProductDocument, ...) now
 * serializes as {@code 2026-10-13T15:30:00.000+05:45} rather than
 * {@code 2026-10-13T09:45:00.000Z} -- same instant in time, correct local
 * offset, no manual conversion needed on the frontend.
 * <p>
 * Deliberately scoped to the HTTP/JSON boundary only -- this bean has no
 * effect on:
 * <ul>
 *   <li>Kafka event payloads ({@code EventEnvelope}/{@code JacksonJsonSerializer}) --
 *       those go through their own serializer, configured separately in
 *       application.properties, and are machine-to-machine wire format, not
 *       something a person reads directly. They stay UTC "Z", which is the
 *       correct, unambiguous choice for inter-service data.</li>
 *   <li>Spark's internal computation timezone ({@code spark.sql.session.timeZone=UTC},
 *       pinned in SparkBatchAnalyticsJob/SparkStreamingAnalyticsJob/SparkTestSupport) --
 *       that pin exists so hour-of-day bucketing and day bucketing are
 *       deterministic regardless of which machine runs the job, and changing
 *       it risks silently shifting every existing Spark test's expected
 *       values. Spark keeps computing in UTC; this bean only changes how the
 *       resulting values are *presented* once they reach a REST response.</li>
 * </ul>
 * Values already stored or computed in UTC are unaffected in meaning -- an
 * Instant is a point on the timeline, not a formatted string, so re-rendering
 * it in Asia/Kathmandu changes its text representation, never what moment it
 * refers to.
 */
@Configuration
public class JacksonConfig {

    public static final ZoneId APP_ZONE = ZoneId.of("Asia/Kathmandu");

    @Bean
    public ObjectMapper objectMapper() {
        ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
        mapper.setTimeZone(TimeZone.getTimeZone(APP_ZONE));
        mapper.enable(SerializationFeature.WRITE_DATES_WITH_CONTEXT_TIME_ZONE);
        return mapper;
    }
}
