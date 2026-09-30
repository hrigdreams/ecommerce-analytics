package com.ecommerce.analytics.controller;

import com.ecommerce.analytics.dto.analytics.unified.UnifiedAnalyticsResponse;
import com.ecommerce.analytics.service.UnifiedAnalyticsService;
import org.junit.jupiter.api.Test;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Plain unit test (no MockMvc/Spring context - keeps this fast and matches
 * how the rest of this project tests controllers by exercising the Java
 * method directly) covering parameter clamping, the one piece of real logic
 * in this controller.
 */
class UnifiedAnalyticsControllerTest {

    private final UnifiedAnalyticsService service = mock(UnifiedAnalyticsService.class);
    private final UnifiedAnalyticsController controller = new UnifiedAnalyticsController(service);

    @Test
    void shouldPassThroughValidParametersUnchanged() {
        when(service.get(30, 10, 10)).thenReturn(mock(UnifiedAnalyticsResponse.class));

        controller.getUnifiedAnalytics(30, 10, 10);

        verify(service).get(30, 10, 10);
    }

    @Test
    void shouldClampTrendDaysAndTopNAndLiveWindowsToTheirValidRanges() {
        when(service.get(365, 100, 100)).thenReturn(mock(UnifiedAnalyticsResponse.class));

        // Way over every max - each should be clamped to its documented ceiling, not passed through raw.
        controller.getUnifiedAnalytics(999999, 999999, 999999);

        verify(service).get(365, 100, 100);
    }

    @Test
    void shouldClampNonPositiveValuesUpToOne() {
        when(service.get(1, 1, 1)).thenReturn(mock(UnifiedAnalyticsResponse.class));

        controller.getUnifiedAnalytics(0, -5, -100);

        verify(service).get(1, 1, 1);
    }
}
