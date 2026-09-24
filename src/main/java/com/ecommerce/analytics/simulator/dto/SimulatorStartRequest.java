package com.ecommerce.analytics.simulator.dto;

/**
 * Optional per-run overrides for POST /api/v1/simulator/start. Any field left
 * null falls back to the app.simulator.* configuration default.
 */
public class SimulatorStartRequest {

    private Integer iterations;
    private Long intervalMs;
    private Long seed;

    public Integer getIterations() { return iterations; }
    public void setIterations(Integer iterations) { this.iterations = iterations; }

    public Long getIntervalMs() { return intervalMs; }
    public void setIntervalMs(Long intervalMs) { this.intervalMs = intervalMs; }

    public Long getSeed() { return seed; }
    public void setSeed(Long seed) { this.seed = seed; }
}
