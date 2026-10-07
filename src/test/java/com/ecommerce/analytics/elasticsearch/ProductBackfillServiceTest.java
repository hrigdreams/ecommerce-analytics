package com.ecommerce.analytics.elasticsearch;

import com.ecommerce.analytics.entity.Product;
import com.ecommerce.analytics.repository.ProductRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.elasticsearch.core.mapping.IndexCoordinates;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ProductBackfillServiceTest {

    private ProductRepository productRepository;
    private ProductIndexingService productIndexingService;
    private ProductBackfillService service;

    @BeforeEach
    void setUp() {
        productRepository = mock(ProductRepository.class);
        productIndexingService = mock(ProductIndexingService.class);
        service = new ProductBackfillService(productRepository, productIndexingService);
    }

    private Product product(long id, String name) {
        Product p = mock(Product.class);
        when(p.getId()).thenReturn(id);
        when(p.getName()).thenReturn(name);
        when(p.getPrice()).thenReturn(10.0);
        when(p.getCategory()).thenReturn(null);
        return p;
    }

    @Test
    void run_shouldPageThroughAllProductsAndUpsertEachOne() {
        Pageable page0 = PageRequest.of(0, 500);
        Pageable page1 = PageRequest.of(1, 500);

        List<Product> firstPageContent = List.of(product(1L, "A"), product(2L, "B"));
        when(productRepository.findAll(page0)).thenReturn(new PageImpl<>(firstPageContent, page0, 502));
        when(productRepository.findAll(page1)).thenReturn(new PageImpl<>(List.of(), page1, 502));

        when(productIndexingService.upsertProduct(anyString(), any(), any(), any(), any(), any())).thenReturn(true);

        ProductBackfillService.BackfillResult result = service.run();

        assertEquals(2, result.productsIndexed());
        assertEquals("products", result.targetIndex());
        verify(productIndexingService, times(2)).upsertProduct(anyString(), any(), any(), any(), any(), any());
        // Default run() must NOT use the explicit-index overload - it targets the alias.
        verify(productIndexingService, never())
                .upsertProduct(anyString(), any(), any(), any(), any(), any(), any(IndexCoordinates.class));
    }

    @Test
    void run_withExplicitTargetIndex_shouldBypassTheAliasAndCallTheIndexCoordinatesOverload() {
        Pageable page0 = PageRequest.of(0, 500);
        Product product = product(1L, "A");
        PageImpl<Product> page = new PageImpl<>(List.of(product), page0, 1);

        when(productRepository.findAll(page0)).thenReturn(page);
        when(productIndexingService.upsertProduct(anyString(), any(), any(), any(), any(), any(), any(IndexCoordinates.class)))
                .thenReturn(true);

        ProductBackfillService.BackfillResult result = service.run("products-v2");

        assertEquals(1, result.productsIndexed());
        assertEquals("products-v2", result.targetIndex());
        verify(productIndexingService).upsertProduct(
                anyString(), any(), any(), any(), any(), any(),
                org.mockito.ArgumentMatchers.eq(IndexCoordinates.of("products-v2")));
        // Must NOT have gone through the alias-targeting (6-arg) overload.
        verify(productIndexingService, never()).upsertProduct(anyString(), any(), any(), any(), any(), any());
    }

    @Test
    void run_calledTwiceForTheSameCatalog_shouldBeIdempotent_sameProductsReindexedNotDuplicated() {
        Pageable page0 = PageRequest.of(0, 500);
        List<Product> content = List.of(product(1L, "A"), product(2L, "B"));
        when(productRepository.findAll(page0)).thenReturn(new PageImpl<>(content, page0, 2));
        when(productIndexingService.upsertProduct(anyString(), any(), any(), any(), any(), any())).thenReturn(true);

        ProductBackfillService.BackfillResult first = service.run();
        ProductBackfillService.BackfillResult second = service.run();

        // Each run reports the same count - re-running does not accumulate/duplicate,
        // because ProductDocument's id is the deterministic productId: re-upserting
        // product 1 twice always targets the exact same document.
        assertEquals(first.productsIndexed(), second.productsIndexed());
        verify(productIndexingService, times(4)).upsertProduct(anyString(), any(), any(), any(), any(), any()); // 2 products x 2 runs
    }

    @Test
    void run_shouldRejectAConcurrentSecondRun() {
        // Simulate "already running" by having the first upsert call block via a
        // CountDownLatch would be heavier than this test needs; instead verify the
        // guard directly: once running is set, a second run() call must fail fast.
        when(productRepository.findAll(any(Pageable.class)))
                .thenAnswer(invocation -> {
                    // While "inside" the first run, attempt a second one.
                    assertThrows(IllegalStateException.class, () -> service.run());
                    return new PageImpl<>(List.of(), PageRequest.of(0, 500), 0);
                });

        service.run(); // outer call completes normally after the inner assertion passes
    }
}
