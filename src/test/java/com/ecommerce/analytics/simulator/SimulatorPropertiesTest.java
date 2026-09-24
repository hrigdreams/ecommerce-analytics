package com.ecommerce.analytics.simulator;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;

class SimulatorPropertiesTest {

    @Test
    void defaults_shouldBeSafeForLocalDev() {
        SimulatorProperties props = new SimulatorProperties();

        // Both switches default OFF - the simulator never runs unless explicitly enabled.
        assertFalse(props.isEnabled());
        assertFalse(props.isAutoStart());

        assertEquals("http://localhost:8080", props.getBaseUrl());
        assertEquals(500, props.getIntervalMs());
        assertEquals(200, props.getIterations());
        assertNull(props.getSeed());
        assertEquals(200, props.getUserPoolSize());
        assertEquals(300, props.getProductPoolSize());
        assertNull(props.getHistoricalCutoff());
    }

    @Test
    void settersShouldOverrideDefaults() {
        SimulatorProperties props = new SimulatorProperties();
        Instant cutoff = Instant.parse("2026-09-23T00:00:00Z");

        props.setEnabled(true);
        props.setAutoStart(true);
        props.setBaseUrl("http://localhost:9090");
        props.setIntervalMs(100);
        props.setIterations(50);
        props.setSeed(42L);
        props.setUserPoolSize(10);
        props.setProductPoolSize(20);
        props.setHistoricalCutoff(cutoff);

        assertEquals(true, props.isEnabled());
        assertEquals(true, props.isAutoStart());
        assertEquals("http://localhost:9090", props.getBaseUrl());
        assertEquals(100, props.getIntervalMs());
        assertEquals(50, props.getIterations());
        assertEquals(42L, props.getSeed());
        assertEquals(10, props.getUserPoolSize());
        assertEquals(20, props.getProductPoolSize());
        assertEquals(cutoff, props.getHistoricalCutoff());
    }
}
