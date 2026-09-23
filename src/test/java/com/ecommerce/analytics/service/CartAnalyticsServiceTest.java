package com.ecommerce.analytics.service;

import com.ecommerce.analytics.entity.CartAnalytics;
import com.ecommerce.analytics.entity.CartItemAnalytics;
import com.ecommerce.analytics.event.payload.cart.CartCreatedEvent;
import com.ecommerce.analytics.event.payload.cart.CartItemAddedEvent;
import com.ecommerce.analytics.event.payload.cart.CartItemRemovedEvent;
import com.ecommerce.analytics.event.payload.cart.CartItemUpdatedEvent;
import com.ecommerce.analytics.repository.CartAnalyticsRepository;
import com.ecommerce.analytics.repository.CartItemAnalyticsRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CartAnalyticsServiceTest {

    private CartAnalyticsRepository cartRepository;
    private CartItemAnalyticsRepository itemRepository;
    private ProductAnalyticsService productAnalyticsService;
    private FunnelAnalyticsService funnelAnalyticsService;
    private CartAnalyticsService service;

    private final Instant at = Instant.parse("2026-09-21T10:00:00Z");

    @BeforeEach
    void setUp() {
        cartRepository = mock(CartAnalyticsRepository.class);
        itemRepository = mock(CartItemAnalyticsRepository.class);
        productAnalyticsService = mock(ProductAnalyticsService.class);
        funnelAnalyticsService = mock(FunnelAnalyticsService.class);
        service = new CartAnalyticsService(cartRepository, itemRepository, productAnalyticsService, funnelAnalyticsService);
    }

    @Test
    void recordCartCreated_shouldSaveNewActiveCart() {
        when(cartRepository.existsById(1L)).thenReturn(false);

        service.recordCartCreated(new CartCreatedEvent(1L, 7L), at);

        verify(cartRepository).save(argThatCart(c ->
                c.getCartId().equals(1L) && c.getUserId().equals(7L) && c.isActive()));
    }

    @Test
    void recordCartCreated_shouldSkipWhenAlreadyExists() {
        when(cartRepository.existsById(1L)).thenReturn(true);

        service.recordCartCreated(new CartCreatedEvent(1L, 7L), at);

        verify(cartRepository, never()).save(any());
    }

    @Test
    void recordItemAdded_shouldIncrementItemCountAndCreateItemRow() {
        CartAnalytics cart = new CartAnalytics();
        cart.setCartId(1L);
        cart.setItemCount(2);
        when(cartRepository.findById(1L)).thenReturn(Optional.of(cart));

        CartItemAddedEvent event =
                new CartItemAddedEvent(1L, 55L, 7L, 10L, "Phone", 3);

        service.recordItemAdded(event, at);

        assertEquals(3, cart.getItemCount());
        verify(itemRepository).save(argThatItem(i ->
                i.getCartItemId().equals(55L) && i.getQuantity() == 3 && i.isActive()));
        verify(productAnalyticsService).recordCartAdd(10L, "Phone", at);
        verify(funnelAnalyticsService).recordCartItemAdded(at);
    }

    @Test
    void recordItemUpdated_shouldChangeQuantityOnExistingItem() {
        CartItemAnalytics item = new CartItemAnalytics();
        item.setCartItemId(55L);
        item.setQuantity(1);
        when(itemRepository.findByCartItemId(55L)).thenReturn(Optional.of(item));
        when(cartRepository.findById(1L)).thenReturn(Optional.empty());

        service.recordItemUpdated(new CartItemUpdatedEvent(1L, 55L, 10L, 5), at);

        assertEquals(5, item.getQuantity());
        verify(itemRepository).save(item);
    }

    @Test
    void recordItemUpdated_shouldSkipWhenItemUnknown() {
        when(itemRepository.findByCartItemId(55L)).thenReturn(Optional.empty());

        service.recordItemUpdated(new CartItemUpdatedEvent(1L, 55L, 10L, 5), at);

        verify(itemRepository, never()).save(any());
    }

    @Test
    void recordItemRemoved_shouldDeactivateItemAndDecrementCartCount() {
        CartItemAnalytics item = new CartItemAnalytics();
        item.setCartItemId(55L);
        item.setProductId(10L);
        item.setActive(true);
        when(itemRepository.findByCartItemId(55L)).thenReturn(Optional.of(item));

        CartAnalytics cart = new CartAnalytics();
        cart.setCartId(1L);
        cart.setItemCount(3);
        when(cartRepository.findById(1L)).thenReturn(Optional.of(cart));

        service.recordItemRemoved(new CartItemRemovedEvent(1L, 55L), at);

        assertFalse(item.isActive());
        assertEquals(2, cart.getItemCount());
        verify(productAnalyticsService).recordCartRemove(10L, at);
    }

    @Test
    void recordItemRemoved_shouldSkipWhenItemAlreadyInactive() {
        CartItemAnalytics item = new CartItemAnalytics();
        item.setActive(false);
        when(itemRepository.findByCartItemId(55L)).thenReturn(Optional.of(item));

        service.recordItemRemoved(new CartItemRemovedEvent(1L, 55L), at);

        verify(productAnalyticsService, never()).recordCartRemove(any(), any());
    }

    private static CartAnalytics argThatCart(java.util.function.Predicate<CartAnalytics> p) {
        return org.mockito.ArgumentMatchers.argThat(p::test);
    }

    private static CartItemAnalytics argThatItem(java.util.function.Predicate<CartItemAnalytics> p) {
        return org.mockito.ArgumentMatchers.argThat(p::test);
    }
}
