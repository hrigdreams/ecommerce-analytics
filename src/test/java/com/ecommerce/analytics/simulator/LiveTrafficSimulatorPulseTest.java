package com.ecommerce.analytics.simulator;

import com.ecommerce.analytics.simulator.dto.SimulatorStatus;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * PHASE 1, item 3 - covers the new pulse-mode lifecycle guards only
 * (mirrors the existing LiveTrafficSimulatorTest's style/scope). Does not
 * exercise an actual scheduled tick firing or hitting the REST API - that
 * needs the full app + Postgres running, same limitation the original
 * burst-mode test already documents for runLoop().
 */
class LiveTrafficSimulatorPulseTest {

    @Test
    void pulseStart_shouldRefuseWhenDisabled() {
        SimulatorProperties props = new SimulatorProperties();
        props.setEnabled(false);
        LiveTrafficSimulator simulator = new LiveTrafficSimulator(props);

        IllegalStateException ex = assertThrows(
                IllegalStateException.class, simulator::pulseStart);

        assertEquals(true, ex.getMessage().contains("disabled"));
        assertFalse(simulator.status().pulseRunning());
    }

    @Test
    void pulseStop_shouldRefuseWhenNotRunning() {
        SimulatorProperties props = new SimulatorProperties();
        LiveTrafficSimulator simulator = new LiveTrafficSimulator(props);

        IllegalStateException ex = assertThrows(
                IllegalStateException.class, simulator::pulseStop);

        assertEquals(true, ex.getMessage().contains("not running"));
    }

    @Test
    void status_beforePulseStart_shouldReportPulseNotRunning() {
        SimulatorProperties props = new SimulatorProperties();
        LiveTrafficSimulator simulator = new LiveTrafficSimulator(props);

        SimulatorStatus status = simulator.status();

        assertFalse(status.pulseRunning());
        // Burst-mode field is untouched by pulse mode's existence.
        assertFalse(status.running());
    }

    @Test
    void pulseTick_shouldBeNoOpWhenPulseNotStarted() {
        SimulatorProperties props = new SimulatorProperties();
        props.setEnabled(true);
        LiveTrafficSimulator simulator = new LiveTrafficSimulator(props);

        // No network available in this test env; pulseTick() must return
        // immediately without attempting any HTTP call when pulseRunning is
        // false, which is the state right after construction.
        simulator.pulseTick();

        assertFalse(simulator.status().pulseRunning());
        assertEquals(0, simulator.status().actionsCompleted());
    }

    @Test
    void pulseDefaults_shouldMatchRequestedRange() {
        SimulatorProperties props = new SimulatorProperties();

        assertEquals(12000, props.getPulse().getIntervalMs());
        assertEquals(2, props.getPulse().getMinEventsPerTick());
        assertEquals(5, props.getPulse().getMaxEventsPerTick());
    }

    /**
     * PHASE 3A, item 3 (operational visibility). Exercising an actual
     * successful action and its recentEvents() entry needs a reachable REST
     * API (same limitation runLoop()/pulseTick() already document for this
     * test class - no network in this env) - this just covers the
     * zero-events starting state, so the deque/accessor wiring itself is at
     * least compiled and exercised.
     */
    @Test
    void recentEvents_shouldStartEmpty() {
        SimulatorProperties props = new SimulatorProperties();
        LiveTrafficSimulator simulator = new LiveTrafficSimulator(props);

        assertEquals(0, simulator.recentEvents().size());
    }
}
