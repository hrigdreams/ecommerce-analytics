package com.ecommerce.analytics.controller.search;

import com.ecommerce.analytics.dto.search.ProductSearchResponse;
import com.ecommerce.analytics.elasticsearch.ProductSearchService;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ProductSearchControllerTest {

    private final ProductSearchService service = mock(ProductSearchService.class);
    private final ProductSearchController controller = new ProductSearchController(service);

    @Test
    void shouldClampSizeToMaxOneHundred() {
        when(service.search(null, null, null, null, null, 0, 100))
                .thenReturn(new ProductSearchResponse(List.of(), 0, 0, 100));

        controller.searchProducts(null, null, null, null, null, 0, 999);

        verify(service).search(null, null, null, null, null, 0, 100);
    }

    @Test
    void shouldClampNegativePageToZero() {
        when(service.search(null, null, null, null, null, 0, 20))
                .thenReturn(new ProductSearchResponse(List.of(), 0, 0, 20));

        controller.searchProducts(null, null, null, null, null, -5, 20);

        verify(service).search(null, null, null, null, null, 0, 20);
    }

    @Test
    void shouldPassThroughAllFilters() {
        when(service.search("phone", 1L, 100.0, 500.0, 4.0, 0, 20))
                .thenReturn(new ProductSearchResponse(List.of(), 0, 0, 20));

        controller.searchProducts("phone", 1L, 100.0, 500.0, 4.0, 0, 20);

        verify(service).search("phone", 1L, 100.0, 500.0, 4.0, 0, 20);
    }

    @Test
    void shouldTranslateInvalidPriceRangeIntoA400NotA500() {
        when(service.search(null, null, 500.0, 100.0, null, 0, 20))
                .thenThrow(new IllegalArgumentException("minPrice (500.0) must not be greater than maxPrice (100.0)"));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> controller.searchProducts(null, null, 500.0, 100.0, null, 0, 20));

        assertEquals(400, ex.getStatusCode().value());
    }
}
