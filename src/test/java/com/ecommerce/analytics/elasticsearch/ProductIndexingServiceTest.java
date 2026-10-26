package com.ecommerce.analytics.elasticsearch;

import com.ecommerce.analytics.elasticsearch.document.ProductDocument;
import com.ecommerce.analytics.entity.Order;
import com.ecommerce.analytics.entity.OrderItem;
import com.ecommerce.analytics.entity.Product;
import com.ecommerce.analytics.event.payload.order.OrderItemEvent;
import com.ecommerce.analytics.repository.CategoryRepository;
import com.ecommerce.analytics.repository.EsProcessedEventRepository;
import com.ecommerce.analytics.repository.OrderRepository;
import com.ecommerce.analytics.repository.ReviewRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.mapping.IndexCoordinates;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class ProductIndexingServiceTest {

    private ElasticsearchOperations elasticsearchOperations;
    private CategoryRepository categoryRepository;
    private EsProcessedEventRepository esProcessedEventRepository;
    private ReviewRepository reviewRepository;
    private ProductRankingUpdateService productRankingUpdateService;
    private OrderRepository orderRepository;
    private ProductIndexingService service;

    private final Instant at = Instant.parse("2026-09-24T10:00:00Z");
    private final IndexCoordinates aliasIndex = IndexCoordinates.of("products");

    @BeforeEach
    void setUp() {
        elasticsearchOperations = mock(ElasticsearchOperations.class);
        categoryRepository = mock(CategoryRepository.class);
        esProcessedEventRepository = mock(EsProcessedEventRepository.class);
        reviewRepository = mock(ReviewRepository.class);
        productRankingUpdateService = mock(ProductRankingUpdateService.class);
        orderRepository = mock(OrderRepository.class);
        when(elasticsearchOperations.getIndexCoordinatesFor(ProductDocument.class)).thenReturn(aliasIndex);
        service = new ProductIndexingService(
                elasticsearchOperations, categoryRepository, esProcessedEventRepository, reviewRepository,
                productRankingUpdateService, orderRepository);
    }

    // ------------------------------------------------------------------
    // Ordering of operations (the critical fix): ES must succeed BEFORE
    // the processed-event marker is persisted.
    // ------------------------------------------------------------------

    @Test
    void upsertProduct_shouldOnlyMarkProcessedAfterElasticsearchWriteSucceeds() {
        when(esProcessedEventRepository.existsById("e1")).thenReturn(false);
        when(elasticsearchOperations.get(eq("10"), eq(ProductDocument.class), eq(aliasIndex))).thenReturn(null);

        boolean result = service.upsertProduct("e1", 10L, "Phone", 499.99, null, at);

        assertTrue(result);
        verify(elasticsearchOperations).save(any(ProductDocument.class), eq(aliasIndex));
        verify(esProcessedEventRepository).markProcessed("e1");
        var inOrder = org.mockito.Mockito.inOrder(elasticsearchOperations, esProcessedEventRepository);
        inOrder.verify(elasticsearchOperations).save(any(ProductDocument.class), eq(aliasIndex));
        inOrder.verify(esProcessedEventRepository).markProcessed("e1");
    }

    @Test
    void upsertProduct_whenElasticsearchFailsPermanently_shouldNeverPersistTheProcessedMarker() {
        when(esProcessedEventRepository.existsById("e1")).thenReturn(false);
        when(elasticsearchOperations.get(eq("10"), eq(ProductDocument.class), eq(aliasIndex)))
                .thenThrow(new IllegalArgumentException("malformed field"));

        assertThrows(RuntimeException.class, () -> service.upsertProduct("e1", 10L, "Phone", 499.99, null, at));

        verify(esProcessedEventRepository, never()).markProcessed(anyString());
    }

    @Test
    void upsertProduct_whenProcessedMarkerPersistenceFails_shouldPropagate_redeliveryStaysSafeViaDeterministicId() {
        when(esProcessedEventRepository.existsById("e1")).thenReturn(false);
        when(elasticsearchOperations.get(eq("10"), eq(ProductDocument.class), eq(aliasIndex))).thenReturn(null);
        doThrow(new DataAccessResourceFailureException("db down"))
                .when(esProcessedEventRepository).markProcessed("e1");

        assertThrows(DataAccessResourceFailureException.class,
                () -> service.upsertProduct("e1", 10L, "Phone", 499.99, null, at));

        verify(elasticsearchOperations).save(any(ProductDocument.class), eq(aliasIndex));
    }

    // ------------------------------------------------------------------
    // Idempotency
    // ------------------------------------------------------------------

    @Test
    void upsertProduct_shouldSkipDuplicateEvent() {
        when(esProcessedEventRepository.existsById("e1")).thenReturn(true);

        boolean result = service.upsertProduct("e1", 10L, "Phone", 499.99, 1L, at);

        assertFalse(result);
        verify(elasticsearchOperations, never()).save(any(), any(IndexCoordinates.class));
        verify(elasticsearchOperations, never()).get(anyString(), eq(ProductDocument.class), any());
    }

    // ------------------------------------------------------------------
    // Ordering / staleness, including the equal-timestamp tie-break
    // ------------------------------------------------------------------

    @Test
    void upsertProduct_shouldRejectEventStrictlyOlderThanCurrentDocument() {
        when(esProcessedEventRepository.existsById("e-late")).thenReturn(false);

        ProductDocument existing = new ProductDocument();
        existing.setId(10L);
        existing.setLastEventOccurredAt(Instant.parse("2026-09-24T12:00:00Z"));
        existing.setLastEventId("e-current");
        when(elasticsearchOperations.get(eq("10"), eq(ProductDocument.class), eq(aliasIndex))).thenReturn(existing);

        boolean result = service.upsertProduct("e-late", 10L, "Phone", 499.99, 1L, at);

        assertTrue(result);
        verify(elasticsearchOperations, never()).save(any(), any(IndexCoordinates.class));
        verify(esProcessedEventRepository).markProcessed("e-late");
    }

    @Test
    void upsertProduct_withEqualTimestamp_shouldApplyOnlyWhenEventIdWinsTheTieBreak() {
        when(esProcessedEventRepository.existsById(anyString())).thenReturn(false);

        ProductDocument existing = new ProductDocument();
        existing.setId(10L);
        existing.setLastEventOccurredAt(at);
        existing.setLastEventId("m");
        when(elasticsearchOperations.get(eq("10"), eq(ProductDocument.class), eq(aliasIndex))).thenReturn(existing);

        boolean losesTie = service.upsertProduct("a", 10L, "Phone", 1.0, null, at);
        assertTrue(losesTie);
        verify(elasticsearchOperations, never()).save(any(), any(IndexCoordinates.class));

        boolean winsTie = service.upsertProduct("z", 10L, "Phone", 1.0, null, at);
        assertTrue(winsTie);
        verify(elasticsearchOperations).save(any(ProductDocument.class), eq(aliasIndex));
    }

    @Test
    void upsertProduct_sameEventIdReplayed_shouldBeIdempotentNotRejectedAsStale() {
        when(esProcessedEventRepository.existsById("e1")).thenReturn(true);

        boolean result = service.upsertProduct("e1", 10L, "Phone", 499.99, null, at);

        assertFalse(result);
    }

    // ------------------------------------------------------------------
    // Delete
    // ------------------------------------------------------------------

    @Test
    void deleteProduct_shouldRemoveFromIndexThenMarkProcessed() {
        when(esProcessedEventRepository.existsById("e1")).thenReturn(false);

        boolean result = service.deleteProduct("e1", 10L, at);

        assertTrue(result);
        var inOrder = org.mockito.Mockito.inOrder(elasticsearchOperations, esProcessedEventRepository);
        inOrder.verify(elasticsearchOperations).delete("10", aliasIndex);
        inOrder.verify(esProcessedEventRepository).markProcessed("e1");
    }

    @Test
    void deleteProduct_shouldSkipDuplicateEvent() {
        when(esProcessedEventRepository.existsById("e1")).thenReturn(true);

        boolean result = service.deleteProduct("e1", 10L, at);

        assertFalse(result);
        verify(elasticsearchOperations, never()).delete(anyString(), any(IndexCoordinates.class));
    }

    // ------------------------------------------------------------------
    // Review aggregate recompute
    // ------------------------------------------------------------------

    @Test
    void recomputeReviewAggregate_shouldWriteFreshAverageAndCountFromPostgres() {
        when(esProcessedEventRepository.existsById("e1")).thenReturn(false);

        ProductDocument existing = new ProductDocument();
        existing.setId(10L);
        when(elasticsearchOperations.get(eq("10"), eq(ProductDocument.class), eq(aliasIndex))).thenReturn(existing);
        when(reviewRepository.findAverageRatingByProductId(10L)).thenReturn(4.3333);
        when(reviewRepository.countByProductId(10L)).thenReturn(3L);

        boolean result = service.recomputeReviewAggregate("e1", 10L, at);

        assertTrue(result);
        verify(elasticsearchOperations).save(org.mockito.ArgumentMatchers.argThat((ProductDocument doc) ->
                doc.getReviewCount() == 3 && doc.getAverageRating() == 4.33), eq(aliasIndex));
        verifyNoInteractions(productRankingUpdateService);
        verifyNoInteractions(orderRepository);
    }

    @Test
    void recomputeReviewAggregate_shouldSkipWhenProductNotYetIndexed() {
        when(esProcessedEventRepository.existsById("e1")).thenReturn(false);
        when(elasticsearchOperations.get(eq("10"), eq(ProductDocument.class), eq(aliasIndex))).thenReturn(null);

        boolean result = service.recomputeReviewAggregate("e1", 10L, at);

        assertTrue(result);
        verify(elasticsearchOperations, never()).save(any(), any(IndexCoordinates.class));
        verify(esProcessedEventRepository).markProcessed("e1");
    }

    // ------------------------------------------------------------------
    // Retry classification: transient vs permanent
    // ------------------------------------------------------------------

    @Test
    void upsertProduct_shouldRetryOnConnectionFailureThenSucceed() {
        when(esProcessedEventRepository.existsById("e1")).thenReturn(false);
        when(elasticsearchOperations.get(eq("10"), eq(ProductDocument.class), eq(aliasIndex)))
                .thenThrow(new DataAccessResourceFailureException("connection refused"))
                .thenReturn(null);

        boolean result = service.upsertProduct("e1", 10L, "Phone", 499.99, null, at);

        assertTrue(result);
        verify(elasticsearchOperations, times(2)).get(eq("10"), eq(ProductDocument.class), eq(aliasIndex));
        verify(elasticsearchOperations).save(any(ProductDocument.class), eq(aliasIndex));
        verify(esProcessedEventRepository).markProcessed("e1");
    }

    // ==================================================================
    // ORDER_CREATED -> processOrderCreated
    // ==================================================================

    private OrderItemEvent item(long orderItemId, long productId, int quantity) {
        return new OrderItemEvent(orderItemId, productId, "Product-" + productId, quantity, 9.99);
    }

    @Test
    void processOrderCreated_singleItem_shouldIncrementSalesCountThenMarkItemAndEventProcessed() {
        when(esProcessedEventRepository.existsById(anyString())).thenReturn(false);

        boolean result = service.processOrderCreated("evt-1", List.of(item(100L, 10L, 2)), at);

        assertTrue(result);
        verify(productRankingUpdateService).incrementSalesCount(10L, 2L);
        var inOrder = org.mockito.Mockito.inOrder(productRankingUpdateService, esProcessedEventRepository);
        inOrder.verify(productRankingUpdateService).incrementSalesCount(10L, 2L);
        inOrder.verify(esProcessedEventRepository).markProcessed("evt-1:item:100");
        inOrder.verify(esProcessedEventRepository).markProcessed("evt-1");
    }

    @Test
    void processOrderCreated_multipleItems_shouldIncrementEachProductByItsOwnQuantity() {
        when(esProcessedEventRepository.existsById(anyString())).thenReturn(false);

        List<OrderItemEvent> items = List.of(
                item(100L, 10L, 2),
                item(101L, 20L, 5),
                item(102L, 30L, 1));

        boolean result = service.processOrderCreated("evt-multi", items, at);

        assertTrue(result);
        verify(productRankingUpdateService).incrementSalesCount(10L, 2L);
        verify(productRankingUpdateService).incrementSalesCount(20L, 5L);
        verify(productRankingUpdateService).incrementSalesCount(30L, 1L);
        verify(esProcessedEventRepository).markProcessed("evt-multi:item:100");
        verify(esProcessedEventRepository).markProcessed("evt-multi:item:101");
        verify(esProcessedEventRepository).markProcessed("evt-multi:item:102");
        verify(esProcessedEventRepository).markProcessed("evt-multi");
    }

    @Test
    void processOrderCreated_duplicateWholeEventDelivery_shouldBeANoOp() {
        when(esProcessedEventRepository.existsById("evt-dup")).thenReturn(true);

        boolean result = service.processOrderCreated("evt-dup", List.of(item(100L, 10L, 2)), at);

        assertFalse(result);
        verifyNoInteractions(productRankingUpdateService);
    }

    @Test
    void processOrderCreated_partialRedelivery_shouldSkipAlreadyAppliedItemsAndOnlyRetryTheRest() {
        when(esProcessedEventRepository.existsById("evt-partial")).thenReturn(false);
        when(esProcessedEventRepository.existsById("evt-partial:item:100")).thenReturn(true);
        when(esProcessedEventRepository.existsById("evt-partial:item:101")).thenReturn(false);

        List<OrderItemEvent> items = List.of(item(100L, 10L, 2), item(101L, 20L, 3));

        boolean result = service.processOrderCreated("evt-partial", items, at);

        assertTrue(result);
        verify(productRankingUpdateService, never()).incrementSalesCount(eq(10L), anyLong());
        verify(productRankingUpdateService).incrementSalesCount(20L, 3L);
        verify(esProcessedEventRepository, never()).markProcessed("evt-partial:item:100");
        verify(esProcessedEventRepository).markProcessed("evt-partial:item:101");
        verify(esProcessedEventRepository).markProcessed("evt-partial");
    }

    @Test
    void processOrderCreated_productMissingFromElasticsearch_shouldNotMarkAnythingProcessed_soKafkaRedelivers() {
        when(esProcessedEventRepository.existsById(anyString())).thenReturn(false);
        doThrow(new IllegalStateException("document_missing_exception"))
                .when(productRankingUpdateService).incrementSalesCount(10L, 2L);

        assertThrows(RuntimeException.class,
                () -> service.processOrderCreated("evt-missing", List.of(item(100L, 10L, 2)), at));

        verify(esProcessedEventRepository, never()).markProcessed("evt-missing:item:100");
        verify(esProcessedEventRepository, never()).markProcessed("evt-missing");
    }

    @Test
    void processOrderCreated_transientElasticsearchFailure_shouldRetryThenSucceed() {
        when(esProcessedEventRepository.existsById(anyString())).thenReturn(false);
        doThrow(new DataAccessResourceFailureException("connection refused"))
                .doNothing()
                .when(productRankingUpdateService).incrementSalesCount(10L, 2L);

        boolean result = service.processOrderCreated("evt-retry", List.of(item(100L, 10L, 2)), at);

        assertTrue(result);
        verify(productRankingUpdateService, times(2)).incrementSalesCount(10L, 2L);
        verify(esProcessedEventRepository).markProcessed("evt-retry:item:100");
        verify(esProcessedEventRepository).markProcessed("evt-retry");
    }

    @Test
    void processOrderCreated_emptyItemList_shouldMarkEventProcessedWithoutCallingElasticsearch() {
        when(esProcessedEventRepository.existsById("evt-empty")).thenReturn(false);

        boolean result = service.processOrderCreated("evt-empty", List.of(), at);

        assertTrue(result);
        verifyNoInteractions(productRankingUpdateService);
        verify(esProcessedEventRepository).markProcessed("evt-empty");
    }

    // ==================================================================
    // ORDER_STATUS_CHANGED(CANCELLED) -> processOrderCancelled
    // ==================================================================

    private Order orderWithItems(Long orderId, OrderItem... items) {
        Order order = mock(Order.class);
        when(order.getId()).thenReturn(orderId);
        when(order.getItems()).thenReturn(List.of(items));
        return order;
    }

    private OrderItem orderItem(Long id, Long productId, int quantity) {
        OrderItem item = mock(OrderItem.class);
        Product product = mock(Product.class);
        when(product.getId()).thenReturn(productId);
        when(item.getId()).thenReturn(id);
        when(item.getProduct()).thenReturn(product);
        when(item.getQuantity()).thenReturn(quantity);
        return item;
    }

    @Test
    void processOrderCancelled_singleItemOrder_shouldDecrementSalesCountThenMarkItemAndEventProcessed() {
        when(esProcessedEventRepository.existsById(anyString())).thenReturn(false);
        Order mockOrder = orderWithItems(500L, orderItem(900L, 10L, 2));
        when(orderRepository.findById(500L)).thenReturn(Optional.of(mockOrder));

        boolean result = service.processOrderCancelled("evt-cancel-1", 500L, at);

        assertTrue(result);
        verify(productRankingUpdateService).decrementSalesCount(10L, 2L);
        var inOrder = org.mockito.Mockito.inOrder(productRankingUpdateService, esProcessedEventRepository);
        inOrder.verify(productRankingUpdateService).decrementSalesCount(10L, 2L);
        inOrder.verify(esProcessedEventRepository).markProcessed("evt-cancel-1:item:900");
        inOrder.verify(esProcessedEventRepository).markProcessed("evt-cancel-1");
    }

    @Test
    void processOrderCancelled_multiItemOrder_shouldDecrementEachProductByItsOwnQuantity() {
        when(esProcessedEventRepository.existsById(anyString())).thenReturn(false);
        Order mockOrder = orderWithItems(501L, orderItem(900L, 10L, 2), orderItem(901L, 20L, 5));
        when(orderRepository.findById(501L)).thenReturn(Optional.of(mockOrder));

        boolean result = service.processOrderCancelled("evt-cancel-2", 501L, at);

        assertTrue(result);
        verify(productRankingUpdateService).decrementSalesCount(10L, 2L);
        verify(productRankingUpdateService).decrementSalesCount(20L, 5L);
        verify(esProcessedEventRepository).markProcessed("evt-cancel-2");
    }

    @Test
    void processOrderCancelled_duplicateWholeEventDelivery_shouldBeANoOp() {
        when(esProcessedEventRepository.existsById("evt-cancel-dup")).thenReturn(true);

        boolean result = service.processOrderCancelled("evt-cancel-dup", 500L, at);

        assertFalse(result);
        verifyNoInteractions(productRankingUpdateService, orderRepository);
    }

    @Test
    void processOrderCancelled_orderNotFoundInPostgres_shouldMarkProcessedWithoutCallingElasticsearch() {
        when(esProcessedEventRepository.existsById("evt-cancel-missing")).thenReturn(false);
        when(orderRepository.findById(999L)).thenReturn(Optional.empty());

        boolean result = service.processOrderCancelled("evt-cancel-missing", 999L, at);

        assertTrue(result);
        verifyNoInteractions(productRankingUpdateService);
        verify(esProcessedEventRepository).markProcessed("evt-cancel-missing");
    }

    @Test
    void processOrderCancelled_partialRedelivery_shouldSkipAlreadyCompensatedItems() {
        when(esProcessedEventRepository.existsById("evt-cancel-partial")).thenReturn(false);
        when(esProcessedEventRepository.existsById("evt-cancel-partial:item:900")).thenReturn(true);
        when(esProcessedEventRepository.existsById("evt-cancel-partial:item:901")).thenReturn(false);
        Order mockOrder = orderWithItems(502L, orderItem(900L, 10L, 2), orderItem(901L, 20L, 5));
        when(orderRepository.findById(502L)).thenReturn(Optional.of(mockOrder));

        boolean result = service.processOrderCancelled("evt-cancel-partial", 502L, at);

        assertTrue(result);
        verify(productRankingUpdateService, never()).decrementSalesCount(eq(10L), anyLong());
        verify(productRankingUpdateService).decrementSalesCount(20L, 5L);
        verify(esProcessedEventRepository, never()).markProcessed("evt-cancel-partial:item:900");
        verify(esProcessedEventRepository).markProcessed("evt-cancel-partial:item:901");
    }

    @Test
    void processOrderCancelled_elasticsearchTransientFailure_shouldRetryThenSucceed() {
        when(esProcessedEventRepository.existsById(anyString())).thenReturn(false);
        Order mockOrder = orderWithItems(503L, orderItem(900L, 10L, 2));
        when(orderRepository.findById(503L)).thenReturn(Optional.of(mockOrder));
        doThrow(new DataAccessResourceFailureException("connection refused"))
                .doNothing()
                .when(productRankingUpdateService).decrementSalesCount(10L, 2L);

        boolean result = service.processOrderCancelled("evt-cancel-retry", 503L, at);

        assertTrue(result);
        verify(productRankingUpdateService, times(2)).decrementSalesCount(10L, 2L);
        verify(esProcessedEventRepository).markProcessed("evt-cancel-retry");
    }

    @Test
    void processOrderCancelled_usesTheOrderStatusChangedEventsOwnEventId_notTheOriginalOrderCreatedEventId() {
        when(esProcessedEventRepository.existsById(anyString())).thenReturn(false);
        Order mockOrder = orderWithItems(504L, orderItem(900L, 10L, 2));
        when(orderRepository.findById(504L)).thenReturn(Optional.of(mockOrder));

        service.processOrderCancelled("status-changed-event-id", 504L, at);

        verify(esProcessedEventRepository).markProcessed("status-changed-event-id:item:900");
        verify(esProcessedEventRepository, never()).markProcessed("original-order-created-event-id:item:900");
    }

    // ==================================================================
    // Product availability (inStock)
    // ==================================================================

    @Test
    void processProductAvailability_shouldSetInStockThenMarkProcessed() {
        when(esProcessedEventRepository.existsById("evt-avail-1")).thenReturn(false);

        boolean result = service.processProductAvailability("evt-avail-1", 10L, false);

        assertTrue(result);
        var inOrder = org.mockito.Mockito.inOrder(productRankingUpdateService, esProcessedEventRepository);
        inOrder.verify(productRankingUpdateService).setInStock(10L, false);
        inOrder.verify(esProcessedEventRepository).markProcessed("evt-avail-1");
    }

    @Test
    void processProductAvailability_shouldSkipDuplicateEvent() {
        when(esProcessedEventRepository.existsById("evt-avail-dup")).thenReturn(true);

        boolean result = service.processProductAvailability("evt-avail-dup", 10L, true);

        assertFalse(result);
        verifyNoInteractions(productRankingUpdateService);
    }

    @Test
    void processProductAvailability_whenElasticsearchFailsPermanently_shouldNeverMarkProcessed() {
        when(esProcessedEventRepository.existsById("evt-avail-fail")).thenReturn(false);
        doThrow(new IllegalStateException("document_missing_exception"))
                .when(productRankingUpdateService).setInStock(10L, false);

        assertThrows(RuntimeException.class, () -> service.processProductAvailability("evt-avail-fail", 10L, false));

        verify(esProcessedEventRepository, never()).markProcessed("evt-avail-fail");
    }
}