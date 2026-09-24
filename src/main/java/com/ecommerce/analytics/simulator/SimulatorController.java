package com.ecommerce.analytics.simulator;

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

/**
 * Controls for the live traffic simulator. This
 * only starts/stops a background loop that calls the app's own REST API.
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

    @Operation(summary = "Get simulator status: running state, action counts, historical cutoff")
    @GetMapping("/status")
    public SimulatorStatus status() {
        return simulator.status();
    }
}
