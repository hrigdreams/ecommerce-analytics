package com.ecommerce.analytics.simulator.dto;

import java.time.Instant;
import java.util.Map;

/**
 * Snapshot returned by GET /api/v1/simulator/status.
 * <p>
 * PHASE 1, item 3: added {@code pulseRunning} as a new, final record
 * component -- purely additive. The original {@code running} component is
 * unchanged and still reflects the original burst/unbounded start/stop mode
 * only; {@code pulseRunning} reflects the new, independent continuous pulse
 * mode. The two can be true at the same time (nothing stops both modes
 * running together, since they share the same underlying action pool/state).
 */
public record SimulatorStatus(
        boolean running,
        boolean enabled,
        long actionsCompleted,
        long actionsFailed,
        int targetIterations,
        Instant historicalCutoff,
        Instant startedAt,
        Map<String, Long> actionCounts,
        String lastError,
        boolean pulseRunning
) {
}
