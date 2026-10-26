package com.ecommerce.analytics.simulator.dto;

import java.time.Instant;

/**
 * PHASE 3A, item 3 (operational visibility). One row in the bounded
 * recent-events log returned by GET /api/v1/simulator/events/recent - see
 * LiveTrafficSimulator's {@code recentEvents} deque for how these are
 * produced and retained.
 *
 * @param eventType  the simulator action's own name (e.g. "CREATE_ORDER",
 *                    "ADD_CART_ITEM" - the same names already used in
 *                    SimulatorStatus.actionCounts, not a separate taxonomy).
 * @param occurredAt  when the action completed successfully.
 * @param mode        "BURST" (the original start()/stop() loop) or "PULSE"
 *                    (the continuous pulse()/pulseStop() mode) - which of
 *                    the two independent simulator modes produced this
 *                    event, since both can run at the same time and share
 *                    one event log.
 * @param detail      a short, human-readable summary of what happened (the
 *                    same information already written to the application
 *                    log for this action, e.g. "orderId=482 userId=17
 *                    total=129.50 status=PENDING") - included so the
 *                    frontend's live terminal log is actually readable
 *                    without cross-referencing application logs.
 */
public record RecentSimEvent(
        String eventType,
        Instant occurredAt,
        String mode,
        String detail
) {
}
