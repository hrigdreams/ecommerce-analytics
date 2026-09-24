package com.ecommerce.analytics.simulator.dto;

import java.time.Instant;
import java.util.Map;

/** Snapshot returned by GET /api/v1/simulator/status. */
public record SimulatorStatus(
        boolean running,
        boolean enabled,
        long actionsCompleted,
        long actionsFailed,
        int targetIterations,
        Instant historicalCutoff,
        Instant startedAt,
        Map<String, Long> actionCounts,
        String lastError
) {
}
