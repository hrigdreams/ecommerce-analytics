package com.ecommerce.analytics.service;

import com.ecommerce.analytics.dto.CartItemRequest;
import com.ecommerce.analytics.dto.CartItemResponse;
import com.ecommerce.analytics.dto.CartRequest;
import com.ecommerce.analytics.dto.CartResponse;
import com.ecommerce.analytics.entity.Cart;
import com.ecommerce.analytics.entity.CartItem;
import com.ecommerce.analytics.entity.Product;
import com.ecommerce.analytics.entity.User;
import com.ecommerce.analytics.event.EventEnvelope;
import com.ecommerce.analytics.event.EventEnvelopeFactory;
import com.ecommerce.analytics.event.EventType;
import com.ecommerce.analytics.event.payload.cart.CartItemAddedEvent;
import com.ecommerce.analytics.event.producer.DomainEventPublisher;
import com.ecommerce.analytics.exception.CartNotFoundException;
import com.ecommerce.analytics.exception.ProductNotFoundException;
import com.ecommerce.analytics.exception.UserNotFoundException;
import com.ecommerce.analytics.repository.CartItemRepository;
import com.ecommerce.analytics.repository.CartRepository;
import com.ecommerce.analytics.repository.ProductRepository;
import com.ecommerce.analytics.repository.UserRepository;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class CartService {

    private final CartRepository cartRepository;
    private final CartItemRepository cartItemRepository;
    private final UserRepository userRepository;
    private final ProductRepository productRepository;
    private final EventEnvelopeFactory eventEnvelopeFactory;
    private final DomainEventPublisher domainEventPublisher;

    public CartService(
            CartRepository cartRepository,
            CartItemRepository cartItemRepository,
            UserRepository userRepository,
            ProductRepository productRepository,
            EventEnvelopeFactory eventEnvelopeFactory,
            DomainEventPublisher domainEventPublisher
    ) {
        this.cartRepository = cartRepository;
        this.cartItemRepository = cartItemRepository;
        this.userRepository = userRepository;
        this.productRepository = productRepository;
        this.eventEnvelopeFactory = eventEnvelopeFactory;
        this.domainEventPublisher = domainEventPublisher;
    }

    /**
     * Creates a cart for a user if one does not already exist.
     *
     * Publishes:
     * CART_CREATED
     */
    public CartResponse createCart(CartRequest request) {

        User user = userRepository.findById(request.getUserId())
                .orElseThrow(() ->
                        new UserNotFoundException(
                                "User not found with id: " + request.getUserId()
                        )
                );

        var existingCart = cartRepository.findByUserId(request.getUserId());

        Cart cart;
        boolean created = false;

        if (existingCart.isPresent()) {
            cart = existingCart.get();
        } else {
            cart = new Cart(user);
            created = true;
        }

        Cart saved = cartRepository.save(cart);

        if (created) {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("cartId", saved.getId());
            payload.put(
                    "userId",
                    saved.getUser() != null ? saved.getUser().getId() : null
            );

            EventEnvelope<Map<String, Object>> event =
                    eventEnvelopeFactory.create(
                            EventType.CART_CREATED,
                            "CART",
                            saved.getId(),
                            payload
                    );

            domainEventPublisher.publish(event);
        }

        return mapToResponse(saved);
    }

    public CartResponse getCartById(Long id) {

        Cart cart = cartRepository.findById(id)
                .orElseThrow(() ->
                        new CartNotFoundException(
                                "Cart not found with id: " + id
                        )
                );

        return mapToResponse(cart);
    }

    public List<CartResponse> getAllCarts() {

        return cartRepository.findAll()
                .stream()
                .map(this::mapToResponse)
                .toList();
    }

    /**
     * Updates the cart owner.
     *
     * Publishes:
     * CART_UPDATED
     */
    public CartResponse updateCart(Long id, CartRequest request) {

        Cart cart = cartRepository.findById(id)
                .orElseThrow(() ->
                        new CartNotFoundException(
                                "Cart not found with id: " + id
                        )
                );

        User user = userRepository.findById(request.getUserId())
                .orElseThrow(() ->
                        new UserNotFoundException(
                                "User not found with id: " + request.getUserId()
                        )
                );

        cart.setUser(user);

        Cart updated = cartRepository.save(cart);

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("cartId", updated.getId());
        payload.put(
                "userId",
                updated.getUser() != null
                        ? updated.getUser().getId()
                        : null
        );

        EventEnvelope<Map<String, Object>> event =
                eventEnvelopeFactory.create(
                        EventType.CART_UPDATED,
                        "CART",
                        updated.getId(),
                        payload
                );

        domainEventPublisher.publish(event);

        return mapToResponse(updated);
    }

    /**
     * Deletes a cart.
     *
     * Publishes:
     * CART_DELETED
     */
    public void deleteCart(Long id) {

        Cart cart = cartRepository.findById(id)
                .orElseThrow(() ->
                        new CartNotFoundException(
                                "Cart not found with id: " + id
                        )
                );

        Long userId = cart.getUser() != null
                ? cart.getUser().getId()
                : null;

        cartRepository.delete(cart);

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("cartId", id);
        payload.put("userId", userId);

        EventEnvelope<Map<String, Object>> event =
                eventEnvelopeFactory.create(
                        EventType.CART_DELETED,
                        "CART",
                        id,
                        payload
                );

        domainEventPublisher.publish(event);
    }

    /**
     * Adds an item to a cart.
     *
     * Publishes:
     * CART_ITEM_ADDED
     */
    public CartItemResponse addItemToCart(
            Long cartId,
            CartItemRequest request
    ) {

        Cart cart = cartRepository.findById(cartId)
                .orElseThrow(() ->
                        new CartNotFoundException(
                                "Cart not found with id: " + cartId
                        )
                );

        Product product = productRepository.findById(request.getProductId())
                .orElseThrow(() ->
                        new ProductNotFoundException(
                                "Product not found with id: "
                                        + request.getProductId()
                        )
                );

        CartItem item = new CartItem(
                product,
                request.getQuantity()
        );

        item.setCart(cart);

        CartItem saved = cartItemRepository.save(item);

        /*
         * Keep the existing strongly typed CartItemAddedEvent
         * because this event is already implemented in the project.
         */
        CartItemAddedEvent payload = new CartItemAddedEvent(
                cart.getId(),
                saved.getId(),
                cart.getUser() != null
                        ? cart.getUser().getId()
                        : null,
                product.getId(),
                product.getName(),
                saved.getQuantity()
        );

        EventEnvelope<CartItemAddedEvent> event =
                eventEnvelopeFactory.create(
                        EventType.CART_ITEM_ADDED,
                        "CART",
                        cart.getId(),
                        payload
                );

        domainEventPublisher.publish(event);

        return mapItemToResponse(saved);
    }

    /**
     * Updates a cart item.
     *
     * Publishes:
     * CART_ITEM_UPDATED
     */
    public CartItemResponse updateCartItem(
            Long cartId,
            Long itemId,
            CartItemRequest request
    ) {

        Cart cart = cartRepository.findById(cartId)
                .orElseThrow(() ->
                        new CartNotFoundException(
                                "Cart not found with id: " + cartId
                        )
                );

        CartItem item = cartItemRepository
                .findByIdAndCartId(itemId, cartId)
                .orElseThrow(() ->
                        new CartNotFoundException(
                                "Cart item not found with id: "
                                        + itemId
                                        + " for cart id: "
                                        + cartId
                        )
                );

        Product product = productRepository.findById(
                        request.getProductId()
                )
                .orElseThrow(() ->
                        new ProductNotFoundException(
                                "Product not found with id: "
                                        + request.getProductId()
                        )
                );

        item.setProduct(product);
        item.setQuantity(request.getQuantity());

        CartItem updated = cartItemRepository.save(item);

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("cartId", cart.getId());
        payload.put("cartItemId", updated.getId());
        payload.put("productId", updated.getProduct().getId());
        payload.put("quantity", updated.getQuantity());

        EventEnvelope<Map<String, Object>> event =
                eventEnvelopeFactory.create(
                        EventType.CART_ITEM_UPDATED,
                        "CART",
                        cart.getId(),
                        payload
                );

        domainEventPublisher.publish(event);

        return mapItemToResponse(updated);
    }

    /**
     * Removes an item from a cart.
     *
     * Publishes:
     * CART_ITEM_REMOVED
     */
    public void deleteCartItem(Long cartId, Long itemId) {

        Cart cart = cartRepository.findById(cartId)
                .orElseThrow(() ->
                        new CartNotFoundException(
                                "Cart not found with id: " + cartId
                        )
                );

        CartItem item = cartItemRepository
                .findByIdAndCartId(itemId, cartId)
                .orElseThrow(() ->
                        new CartNotFoundException(
                                "Cart item not found with id: "
                                        + itemId
                                        + " for cart id: "
                                        + cartId
                        )
                );

        cartItemRepository.delete(item);

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("cartId", cart.getId());
        payload.put("cartItemId", item.getId());

        EventEnvelope<Map<String, Object>> event =
                eventEnvelopeFactory.create(
                        EventType.CART_ITEM_REMOVED,
                        "CART",
                        cart.getId(),
                        payload
                );

        domainEventPublisher.publish(event);
    }

    private CartResponse mapToResponse(Cart cart) {

        return new CartResponse(
                cart.getId(),
                cart.getUser() != null
                        ? cart.getUser().getId()
                        : null
        );
    }

    private CartItemResponse mapItemToResponse(CartItem item) {

        return new CartItemResponse(
                item.getId(),
                item.getCart().getId(),
                item.getProduct().getId(),
                item.getProduct().getName(),
                item.getQuantity()
        );
    }
}