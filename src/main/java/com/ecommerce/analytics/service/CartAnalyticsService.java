package com.ecommerce.analytics.service;

import com.ecommerce.analytics.entity.CartAnalytics;
import com.ecommerce.analytics.entity.CartItemAnalytics;
import com.ecommerce.analytics.event.payload.cart.CartCreatedEvent;
import com.ecommerce.analytics.event.payload.cart.CartDeletedEvent;
import com.ecommerce.analytics.event.payload.cart.CartItemAddedEvent;
import com.ecommerce.analytics.event.payload.cart.CartItemRemovedEvent;
import com.ecommerce.analytics.event.payload.cart.CartItemUpdatedEvent;
import com.ecommerce.analytics.event.payload.cart.CartUpdatedEvent;
import com.ecommerce.analytics.repository.CartAnalyticsRepository;
import com.ecommerce.analytics.repository.CartItemAnalyticsRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
public class CartAnalyticsService {

    private static final Logger log = LoggerFactory.getLogger(CartAnalyticsService.class);

    private final CartAnalyticsRepository cartRepository;
    private final CartItemAnalyticsRepository itemRepository;
    private final ProductAnalyticsService productAnalyticsService;
    private final FunnelAnalyticsService funnelAnalyticsService;

    public CartAnalyticsService(
            CartAnalyticsRepository cartRepository,
            CartItemAnalyticsRepository itemRepository,
            ProductAnalyticsService productAnalyticsService,
            FunnelAnalyticsService funnelAnalyticsService
    ) {
        this.cartRepository = cartRepository;
        this.itemRepository = itemRepository;
        this.productAnalyticsService = productAnalyticsService;
        this.funnelAnalyticsService = funnelAnalyticsService;
    }

    private CartAnalytics getOrCreateCart(Long cartId, Long userId, Instant at) {
        return cartRepository.findById(cartId).orElseGet(() -> {
            CartAnalytics cart = new CartAnalytics();
            cart.setCartId(cartId);
            cart.setUserId(userId);
            cart.setCreatedAt(at);
            return cart;
        });
    }

    /** CART_CREATED. */
    @Transactional
    public void recordCartCreated(CartCreatedEvent event, Instant occurredAt) {
        if (cartRepository.existsById(event.getCartId())) {
            return;
        }
        Instant at = occurredAt != null ? occurredAt : Instant.now();

        CartAnalytics cart = new CartAnalytics();
        cart.setCartId(event.getCartId());
        cart.setUserId(event.getUserId());
        cart.setActive(true);
        cart.setCreatedAt(at);
        cart.setUpdatedAt(at);
        cart.setLastActivityAt(at);
        cartRepository.save(cart);
    }

    /** CART_UPDATED. */
    @Transactional
    public void recordCartUpdated(CartUpdatedEvent event, Instant occurredAt) {
        Instant at = occurredAt != null ? occurredAt : Instant.now();
        CartAnalytics cart = getOrCreateCart(event.getCartId(), event.getUserId(), at);
        cart.setUpdatedAt(at);
        cart.setLastActivityAt(at);
        cartRepository.save(cart);
    }

    /** CART_DELETED. Kept as an inactive row so cart history stays queryable. */
    @Transactional
    public void recordCartDeleted(CartDeletedEvent event, Instant occurredAt) {
        Instant at = occurredAt != null ? occurredAt : Instant.now();
        CartAnalytics cart = getOrCreateCart(event.getCartId(), event.getUserId(), at);
        cart.setActive(false);
        cart.setDeletedAt(at);
        cart.setUpdatedAt(at);
        cartRepository.save(cart);
    }

    /** CART_ITEM_ADDED. */
    @Transactional
    public void recordItemAdded(CartItemAddedEvent event, Instant occurredAt) {
        Instant at = occurredAt != null ? occurredAt : Instant.now();

        CartAnalytics cart = getOrCreateCart(event.getCartId(), event.getUserId(), at);
        cart.setItemCount(cart.getItemCount() + 1);
        cart.setActive(true);
        cart.setUpdatedAt(at);
        cart.setLastActivityAt(at);
        cartRepository.save(cart);

        int quantity = event.getQuantity() != null ? event.getQuantity() : 0;

        CartItemAnalytics item = new CartItemAnalytics();
        item.setCartId(event.getCartId());
        item.setCartItemId(event.getCartItemId());
        item.setProductId(event.getProductId());
        item.setProductName(event.getProductName());
        item.setQuantity(quantity);
        item.setActive(true);
        item.setAddedAt(at);
        item.setUpdatedAt(at);
        itemRepository.save(item);

        productAnalyticsService.recordCartAdd(event.getProductId(), event.getProductName(), at);
        funnelAnalyticsService.recordCartItemAdded(at);
    }

    /** CART_ITEM_UPDATED (quantity change). */
    @Transactional
    public void recordItemUpdated(CartItemUpdatedEvent event, Instant occurredAt) {
        Instant at = occurredAt != null ? occurredAt : Instant.now();

        CartItemAnalytics item = itemRepository.findByCartItemId(event.getCartItemId()).orElse(null);
        if (item == null) {
            log.warn("Cart item {} not in analytics; update skipped", event.getCartItemId());
            return;
        }

        if (event.getQuantity() != null) {
            item.setQuantity(event.getQuantity());
        }
        item.setUpdatedAt(at);
        itemRepository.save(item);

        cartRepository.findById(event.getCartId()).ifPresent(cart -> {
            cart.setUpdatedAt(at);
            cart.setLastActivityAt(at);
            cartRepository.save(cart);
        });
    }

    /** CART_ITEM_REMOVED. */
    @Transactional
    public void recordItemRemoved(CartItemRemovedEvent event, Instant occurredAt) {
        Instant at = occurredAt != null ? occurredAt : Instant.now();

        CartItemAnalytics item = itemRepository.findByCartItemId(event.getCartItemId()).orElse(null);
        if (item == null || !item.isActive()) {
            log.warn("Cart item {} not active in analytics; removal skipped", event.getCartItemId());
            return;
        }

        item.setActive(false);
        item.setRemovedAt(at);
        item.setUpdatedAt(at);
        itemRepository.save(item);

        cartRepository.findById(event.getCartId()).ifPresent(cart -> {
            cart.setItemCount(cart.getItemCount() - 1);
            cart.setUpdatedAt(at);
            cart.setLastActivityAt(at);
            cartRepository.save(cart);
        });

        productAnalyticsService.recordCartRemove(item.getProductId(), at);
    }
}
