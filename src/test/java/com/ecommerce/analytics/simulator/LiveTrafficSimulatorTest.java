package com.ecommerce.analytics.simulator;

import com.ecommerce.analytics.simulator.dto.SimulatorStatus;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Covers the simulator's lifecycle guards. Does not exercise the background
 * traffic-generation loop itself (that needs the full app + Postgres + Kafka
 * running - see scripts/data-generator-style manual verification instead).
 */
class LiveTrafficSimulatorTest {

    @Test
    void start_shouldRefuseWhenDisabled() {
        SimulatorProperties props = new SimulatorProperties();
        props.setEnabled(false);
        LiveTrafficSimulator simulator = new LiveTrafficSimulator(props);

        IllegalStateException ex = assertThrows(
                IllegalStateException.class, () -> simulator.start(null));

        assertEquals(true, ex.getMessage().contains("disabled"));
        assertFalse(simulator.status().running());
    }

    @Test
    void stop_shouldRefuseWhenNotRunning() {
        SimulatorProperties props = new SimulatorProperties();
        LiveTrafficSimulator simulator = new LiveTrafficSimulator(props);

        IllegalStateException ex = assertThrows(
                IllegalStateException.class, simulator::stop);

        assertEquals(true, ex.getMessage().contains("not running"));
    }

    @Test
    void status_beforeStart_shouldReportNotRunningAndNoActivity() {
        SimulatorProperties props = new SimulatorProperties();
        LiveTrafficSimulator simulator = new LiveTrafficSimulator(props);

        SimulatorStatus status = simulator.status();

        assertFalse(status.running());
        assertEquals(0, status.actionsCompleted());
        assertEquals(0, status.actionsFailed());
        assertEquals(0, status.actionCounts().size());
    }

    @Test
    void onApplicationReady_shouldNotAutoStartWhenAutoStartIsFalse() {
        SimulatorProperties props = new SimulatorProperties();
        props.setEnabled(true);
        props.setAutoStart(false);
        LiveTrafficSimulator simulator = new LiveTrafficSimulator(props);

        simulator.onApplicationReady();

        assertFalse(simulator.status().running());
    }

    @Test
    void onApplicationReady_shouldNotAutoStartWhenDisabledEvenIfAutoStartTrue() {
        SimulatorProperties props = new SimulatorProperties();
        props.setEnabled(false);
        props.setAutoStart(true);
        LiveTrafficSimulator simulator = new LiveTrafficSimulator(props);

        simulator.onApplicationReady();

        assertFalse(simulator.status().running());
    }
}
