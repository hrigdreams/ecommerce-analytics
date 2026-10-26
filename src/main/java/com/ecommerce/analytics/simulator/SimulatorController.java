package com.ecommerce.analytics.simulator;

import com.ecommerce.analytics.simulator.dto.RecentSimEvent;
import com.ecommerce.analytics.simulator.dto.SimulatorStartRequest;
import com.ecommerce.analytics.simulator.dto.SimulatorStatus;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

/**
 * Controls for the live traffic simulator. This
 * only starts/stops a background loop that calls the app's own REST API.
 * <p>
 * PHASE 1, item 3: added /pulse/start and /pulse/stop, controlling the new
 * continuous pulse mode independently of the original /start and /stop
 * (those two are unchanged and still control burst/unbounded mode only).
 * <p>
 * PHASE 3A, item 3: added /events/recent, exposing the bounded in-memory
 * log of raw events the simulator has successfully generated (burst and
 * pulse mode both write to the same log) so the frontend can build a live
 * streaming terminal/activity log instead of having no visibility into
 * what the simulator is actually doing beyond the aggregate counts in
 * /status.
 */
@RestController
@RequestMapping("/api/v1/simulator")
@Tag(name = "Simulator", description = "Live traffic simulator controls (dev/test tool, not part of the analytics API)")
public class SimulatorController {

    private final LiveTrafficSimulator simulator;

    public SimulatorController(LiveTrafficSimulator simulator) {
        this.simulator = simulator;
    }

    @Operation(
            summary = "Start the live traffic simulator",
            description = "Requires app.simulator.enabled=true. Body is optional; any field left out falls back to the app.simulator.* config default."
    )
    @PostMapping("/start")
    public SimulatorStatus start(@RequestBody(required = false) SimulatorStartRequest request) {
        try {
            simulator.start(request);
            return simulator.status();
        } catch (IllegalStateException e) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, e.getMessage());
        }
    }

    @Operation(summary = "Stop the live traffic simulator")
    @PostMapping("/stop")
    public SimulatorStatus stop() {
        try {
            simulator.stop();
            return simulator.status();
        } catch (IllegalStateException e) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, e.getMessage());
        }
    }

    @Operation(summary = "Get simulator status: running state, action counts, historical cutoff, pulse mode state")
    @GetMapping("/status")
    public SimulatorStatus status() {
        return simulator.status();
    }

    @Operation(
            summary = "Start the continuous background pulse",
            description = "Requires app.simulator.enabled=true. Independent of /start: trickles " +
                    "app.simulator.pulse.min-events-per-tick..max-events-per-tick random actions into " +
                    "Kafka every app.simulator.pulse.interval-ms (default 12s), continuously, until " +
                    "/pulse/stop is called."
    )
    @PostMapping("/pulse/start")
    public SimulatorStatus pulseStart() {
        try {
            simulator.pulseStart();
            return simulator.status();
        } catch (IllegalStateException e) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, e.getMessage());
        }
    }

    @Operation(summary = "Stop the continuous background pulse")
    @PostMapping("/pulse/stop")
    public SimulatorStatus pulseStop() {
        try {
            simulator.pulseStop();
            return simulator.status();
        } catch (IllegalStateException e) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, e.getMessage());
        }
    }

    @Operation(
            summary = "Recent raw simulator events (live activity log)",
            description = "Up to the last 100 successfully-generated actions, newest first, from BOTH burst "
                    + "and pulse mode (each entry's \"mode\" field says which). In-memory only, scoped to this "
                    + "running instance - not persisted, not a substitute for the real analytics pipeline "
                    + "(Kafka -> Spark/Elasticsearch), just raw operational visibility into what the simulator "
                    + "is doing right now."
    )
    @GetMapping("/events/recent")
    public List<RecentSimEvent> recentEvents() {
        return simulator.recentEvents();
    }
}
