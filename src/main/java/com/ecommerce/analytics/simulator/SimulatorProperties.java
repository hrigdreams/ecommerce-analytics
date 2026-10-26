package com.ecommerce.analytics.simulator;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Instant;

/**
 * Configuration for the live traffic simulator. All properties are prefixed
 * app.simulator.* in application.properties;



 * The simulator is OFF by default on both switches (enabled=false,
 * autoStart=false) — it only ever runs when explicitly turned on, either by
 * setting app.simulator.enabled=true + app.simulator.auto-start=true, or by
 * calling POST /api/v1/simulator/start once enabled=true.
 *
 * PHASE 1, item 3 (continuous pulse mode): added the nested {@link Pulse}
 * properties below (app.simulator.pulse.*). These are additive to every
 * property already here -- the original burst/unbounded start/stop mode
 * (iterations, intervalMs, seed, ...) is completely unchanged and still
 * works exactly as before. Pulse mode is a second, independent way to run
 * the same simulator, controlled separately via
 * POST /api/v1/simulator/pulse/start and /pulse/stop.
 */


@ConfigurationProperties(prefix = "app.simulator")
public class SimulatorProperties {

    /** Master switch. When false, /simulator/start refuses to start anything. */
    private boolean enabled = false;

    /** If true (and enabled=true), the simulator starts automatically once the app is ready. */
    private boolean autoStart = false;

    /** Base URL of THIS application's own REST API, e.g. http://localhost:8080. */
    private String baseUrl = "http://localhost:8080";

    /** Delay between simulated actions, in milliseconds. */
    private long intervalMs = 500;

    /** Number of actions to run; 0 or negative means "run until stopped". */
    private int iterations = 200;

    /** Random seed for reproducible runs. Null/unset = a new random seed each run. */
    private Long seed;

    /** How many existing users to sample from (fetched once via GET /api/v1/users). */
    private int userPoolSize = 200;

    /** How many existing products to sample from (fetched via the paginated products endpoint). */
    private int productPoolSize = 300;

    /**
     * historical cutoff: events with occurredAt before this instant are the
     * DataGenerator's historical baseline; events at/after it are this live
     * simulator's activity.
     */
    private Instant historicalCutoff;

    /** PHASE 1, item 3: continuous "pulse" mode settings (app.simulator.pulse.*). */
    private Pulse pulse = new Pulse();

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }

    public boolean isAutoStart() { return autoStart; }
    public void setAutoStart(boolean autoStart) { this.autoStart = autoStart; }

    public String getBaseUrl() { return baseUrl; }
    public void setBaseUrl(String baseUrl) { this.baseUrl = baseUrl; }

    public long getIntervalMs() { return intervalMs; }
    public void setIntervalMs(long intervalMs) { this.intervalMs = intervalMs; }

    public int getIterations() { return iterations; }
    public void setIterations(int iterations) { this.iterations = iterations; }

    public Long getSeed() { return seed; }
    public void setSeed(Long seed) { this.seed = seed; }

    public int getUserPoolSize() { return userPoolSize; }
    public void setUserPoolSize(int userPoolSize) { this.userPoolSize = userPoolSize; }

    public int getProductPoolSize() { return productPoolSize; }
    public void setProductPoolSize(int productPoolSize) { this.productPoolSize = productPoolSize; }

    public Instant getHistoricalCutoff() { return historicalCutoff; }
    public void setHistoricalCutoff(Instant historicalCutoff) { this.historicalCutoff = historicalCutoff; }

    public Pulse getPulse() { return pulse; }
    public void setPulse(Pulse pulse) { this.pulse = pulse; }

    /**
     * PHASE 1, item 3. Binds app.simulator.pulse.interval-ms,
     * app.simulator.pulse.min-events-per-tick, app.simulator.pulse.max-events-per-tick
     * via Spring Boot's standard relaxed binding for nested
     * {@code @ConfigurationProperties} objects -- no extra annotation needed
     * beyond the enclosing class's own {@code @ConfigurationProperties}.
     */
    public static class Pulse {

        /** Delay between scheduled pulse ticks, in milliseconds. Default 12000 (12s, the midpoint of the requested 10-15s range). */
        private long intervalMs = 12000;

        /** Minimum number of simulated actions run on each tick. */
        private int minEventsPerTick = 2;

        /** Maximum number of simulated actions run on each tick (inclusive). */
        private int maxEventsPerTick = 5;

        public long getIntervalMs() { return intervalMs; }
        public void setIntervalMs(long intervalMs) { this.intervalMs = intervalMs; }

        public int getMinEventsPerTick() { return minEventsPerTick; }
        public void setMinEventsPerTick(int minEventsPerTick) { this.minEventsPerTick = minEventsPerTick; }

        public int getMaxEventsPerTick() { return maxEventsPerTick; }
        public void setMaxEventsPerTick(int maxEventsPerTick) { this.maxEventsPerTick = maxEventsPerTick; }
    }
}
