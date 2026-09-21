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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@SuppressWarnings({"rawtypes", "unchecked"})
class CartServiceTest {

    @Mock
    private CartRepository cartRepository;

    @Mock
    private CartItemRepository cartItemRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private ProductRepository productRepository;

    @Mock
    private EventEnvelopeFactory eventEnvelopeFactory;

    @Mock
    private DomainEventPublisher domainEventPublisher;

    @Mock
    private EventEnvelope<CartItemAddedEvent> cartItemAddedEvent;

    /*
     * Raw EventEnvelope is intentional here.
     *
     * EventEnvelopeFactory.create(...) is resolved by Mockito
     * as EventEnvelope<Map>, while CartService creates
     * EventEnvelope<Map<String, Object>>.
     *
     * Using a raw mock avoids the generic mismatch in the test.
     */
    @Mock
    private EventEnvelope cartEvent;

    @Mock
    private User user;

    @Mock
    private Cart cart;

    @Mock
    private Product product;

    @Mock
    private CartItem cartItem;

    @InjectMocks
    private CartService cartService;

    // =========================================================
    // CREATE CART
    // =========================================================

    @Test
    void createCart_shouldCreateCartSuccessfully() {

        CartRequest request = new CartRequest(1L);

        when(userRepository.findById(1L))
                .thenReturn(Optional.of(user));

        when(cartRepository.findByUserId(1L))
                .thenReturn(Optional.empty());

        when(cartRepository.save(any(Cart.class)))
                .thenReturn(cart);

        when(cart.getId())
                .thenReturn(1L);

        when(cart.getUser())
                .thenReturn(user);

        when(user.getId())
                .thenReturn(1L);

        when(eventEnvelopeFactory.create(
                eq(EventType.CART_CREATED),
                eq("CART"),
                eq(1L),
                any(Map.class)
        )).thenReturn(cartEvent);

        CartResponse response =
                cartService.createCart(request);

        assertNotNull(response);
        assertEquals(1L, response.getId());
        assertEquals(1L, response.getUserId());

        verify(userRepository).findById(1L);
        verify(cartRepository).findByUserId(1L);
        verify(cartRepository).save(any(Cart.class));

        verify(eventEnvelopeFactory).create(
                eq(EventType.CART_CREATED),
                eq("CART"),
                eq(1L),
                any(Map.class)
        );

        verify(domainEventPublisher).publish(cartEvent);
    }

    @Test
    void createCart_shouldReturnExistingCartWithoutCreatingEvent() {

        CartRequest request = new CartRequest(1L);

        when(userRepository.findById(1L))
                .thenReturn(Optional.of(user));

        when(cartRepository.findByUserId(1L))
                .thenReturn(Optional.of(cart));

        when(cartRepository.save(cart))
                .thenReturn(cart);

        when(cart.getId())
                .thenReturn(1L);

        when(cart.getUser())
                .thenReturn(user);

        when(user.getId())
                .thenReturn(1L);

        CartResponse response =
                cartService.createCart(request);

        assertNotNull(response);
        assertEquals(1L, response.getId());
        assertEquals(1L, response.getUserId());

        verify(userRepository).findById(1L);
        verify(cartRepository).findByUserId(1L);
        verify(cartRepository).save(cart);

        verify(eventEnvelopeFactory, never()).create(
                eq(EventType.CART_CREATED),
                anyString(),
                anyLong(),
                any()
        );

        verify(domainEventPublisher, never()).publish(any());
    }

    @Test
    void createCart_shouldThrowUserNotFoundException() {

        CartRequest request = new CartRequest(999L);

        when(userRepository.findById(999L))
                .thenReturn(Optional.empty());

        UserNotFoundException exception =
                assertThrows(
                        UserNotFoundException.class,
                        () -> cartService.createCart(request)
                );

        assertEquals(
                "User not found with id: 999",
                exception.getMessage()
        );

        verify(userRepository).findById(999L);
        verify(cartRepository, never()).findByUserId(anyLong());
        verify(cartRepository, never()).save(any(Cart.class));
        verify(domainEventPublisher, never()).publish(any());
    }

    // =========================================================
    // GET CART
    // =========================================================

    @Test
    void getCartById_shouldReturnCartSuccessfully() {

        when(cartRepository.findById(1L))
                .thenReturn(Optional.of(cart));

        when(cart.getId())
                .thenReturn(1L);

        when(cart.getUser())
                .thenReturn(user);

        when(user.getId())
                .thenReturn(1L);

        CartResponse response =
                cartService.getCartById(1L);

        assertNotNull(response);
        assertEquals(1L, response.getId());
        assertEquals(1L, response.getUserId());

        verify(cartRepository).findById(1L);
    }

    @Test
    void getCartById_shouldThrowCartNotFoundException() {

        when(cartRepository.findById(999L))
                .thenReturn(Optional.empty());

        CartNotFoundException exception =
                assertThrows(
                        CartNotFoundException.class,
                        () -> cartService.getCartById(999L)
                );

        assertEquals(
                "Cart not found with id: 999",
                exception.getMessage()
        );

        verify(cartRepository).findById(999L);
    }

    // =========================================================
    // GET ALL CARTS
    // =========================================================

    @Test
    void getAllCarts_shouldReturnAllCarts() {

        Cart cart1 = mock(Cart.class);
        Cart cart2 = mock(Cart.class);

        User user1 = mock(User.class);
        User user2 = mock(User.class);

        when(cart1.getId()).thenReturn(1L);
        when(cart1.getUser()).thenReturn(user1);
        when(user1.getId()).thenReturn(1L);

        when(cart2.getId()).thenReturn(2L);
        when(cart2.getUser()).thenReturn(user2);
        when(user2.getId()).thenReturn(2L);

        when(cartRepository.findAll())
                .thenReturn(List.of(cart1, cart2));

        List<CartResponse> responses =
                cartService.getAllCarts();

        assertNotNull(responses);
        assertEquals(2, responses.size());

        assertEquals(1L, responses.get(0).getId());
        assertEquals(1L, responses.get(0).getUserId());

        assertEquals(2L, responses.get(1).getId());
        assertEquals(2L, responses.get(1).getUserId());

        verify(cartRepository).findAll();
    }

    // =========================================================
    // UPDATE CART
    // =========================================================

    @Test
    void updateCart_shouldUpdateCartSuccessfully() {

        CartRequest request = new CartRequest(2L);

        User newUser = mock(User.class);

        when(cartRepository.findById(1L))
                .thenReturn(Optional.of(cart));

        when(userRepository.findById(2L))
                .thenReturn(Optional.of(newUser));

        when(cartRepository.save(cart))
                .thenReturn(cart);

        when(cart.getId())
                .thenReturn(1L);

        when(cart.getUser())
                .thenReturn(newUser);

        when(newUser.getId())
                .thenReturn(2L);

        when(eventEnvelopeFactory.create(
                eq(EventType.CART_UPDATED),
                eq("CART"),
                eq(1L),
                any(Map.class)
        )).thenReturn(cartEvent);

        CartResponse response =
                cartService.updateCart(1L, request);

        assertNotNull(response);
        assertEquals(1L, response.getId());
        assertEquals(2L, response.getUserId());

        verify(cartRepository).findById(1L);
        verify(userRepository).findById(2L);
        verify(cart).setUser(newUser);
        verify(cartRepository).save(cart);

        verify(eventEnvelopeFactory).create(
                eq(EventType.CART_UPDATED),
                eq("CART"),
                eq(1L),
                any(Map.class)
        );

        verify(domainEventPublisher).publish(cartEvent);
    }

    @Test
    void updateCart_shouldThrowCartNotFoundException() {

        CartRequest request = new CartRequest(2L);

        when(cartRepository.findById(999L))
                .thenReturn(Optional.empty());

        CartNotFoundException exception =
                assertThrows(
                        CartNotFoundException.class,
                        () -> cartService.updateCart(999L, request)
                );

        assertEquals(
                "Cart not found with id: 999",
                exception.getMessage()
        );

        verify(cartRepository).findById(999L);
        verify(userRepository, never()).findById(anyLong());
        verify(cartRepository, never()).save(any(Cart.class));
        verify(domainEventPublisher, never()).publish(any());
    }

    @Test
    void updateCart_shouldThrowUserNotFoundException() {

        CartRequest request = new CartRequest(999L);

        when(cartRepository.findById(1L))
                .thenReturn(Optional.of(cart));

        when(userRepository.findById(999L))
                .thenReturn(Optional.empty());

        UserNotFoundException exception =
                assertThrows(
                        UserNotFoundException.class,
                        () -> cartService.updateCart(1L, request)
                );

        assertEquals(
                "User not found with id: 999",
                exception.getMessage()
        );

        verify(cartRepository).findById(1L);
        verify(userRepository).findById(999L);
        verify(cart, never()).setUser(any(User.class));
        verify(cartRepository, never()).save(any(Cart.class));
        verify(domainEventPublisher, never()).publish(any());
    }

    // =========================================================
    // DELETE CART
    // =========================================================

    @Test
    void deleteCart_shouldDeleteCartSuccessfully() {

        when(cartRepository.findById(1L))
                .thenReturn(Optional.of(cart));

        when(eventEnvelopeFactory.create(
                eq(EventType.CART_DELETED),
                eq("CART"),
                eq(1L),
                any(Map.class)
        )).thenReturn(cartEvent);

        cartService.deleteCart(1L);

        verify(cartRepository).findById(1L);
        verify(cartRepository).delete(cart);

        verify(eventEnvelopeFactory).create(
                eq(EventType.CART_DELETED),
                eq("CART"),
                eq(1L),
                any(Map.class)
        );

        verify(domainEventPublisher).publish(cartEvent);
    }

    @Test
    void deleteCart_shouldThrowCartNotFoundException() {

        when(cartRepository.findById(999L))
                .thenReturn(Optional.empty());

        CartNotFoundException exception =
                assertThrows(
                        CartNotFoundException.class,
                        () -> cartService.deleteCart(999L)
                );

        assertEquals(
                "Cart not found with id: 999",
                exception.getMessage()
        );

        verify(cartRepository).findById(999L);
        verify(cartRepository, never()).delete(any(Cart.class));
        verify(domainEventPublisher, never()).publish(any());
    }

    // =========================================================
    // ADD CART ITEM
    // =========================================================

    @Test
    void addItemToCart_shouldAddItemSuccessfully() {

        CartItemRequest request =
                new CartItemRequest(10L, 3);

        when(cartRepository.findById(1L))
                .thenReturn(Optional.of(cart));

        when(productRepository.findById(10L))
                .thenReturn(Optional.of(product));

        when(cartItemRepository.save(any(CartItem.class)))
                .thenReturn(cartItem);

        when(cartItem.getId())
                .thenReturn(100L);

        when(cartItem.getCart())
                .thenReturn(cart);

        when(cartItem.getProduct())
                .thenReturn(product);

        when(cartItem.getQuantity())
                .thenReturn(3);

        when(cart.getId())
                .thenReturn(1L);

        when(cart.getUser())
                .thenReturn(user);

        when(user.getId())
                .thenReturn(1L);

        when(product.getId())
                .thenReturn(10L);

        when(product.getName())
                .thenReturn("Laptop");

        when(eventEnvelopeFactory.create(
                eq(EventType.CART_ITEM_ADDED),
                eq("CART"),
                eq(1L),
                any(CartItemAddedEvent.class)
        )).thenReturn(cartItemAddedEvent);

        CartItemResponse response =
                cartService.addItemToCart(1L, request);

        assertNotNull(response);
        assertEquals(100L, response.getId());
        assertEquals(1L, response.getCartId());
        assertEquals(10L, response.getProductId());
        assertEquals("Laptop", response.getProductName());
        assertEquals(3, response.getQuantity());

        verify(cartRepository).findById(1L);
        verify(productRepository).findById(10L);
        verify(cartItemRepository).save(any(CartItem.class));

        verify(eventEnvelopeFactory).create(
                eq(EventType.CART_ITEM_ADDED),
                eq("CART"),
                eq(1L),
                any(CartItemAddedEvent.class)
        );

        verify(domainEventPublisher).publish(cartItemAddedEvent);
    }

    @Test
    void addItemToCart_shouldThrowCartNotFoundException() {

        CartItemRequest request =
                new CartItemRequest(10L, 3);

        when(cartRepository.findById(999L))
                .thenReturn(Optional.empty());

        CartNotFoundException exception =
                assertThrows(
                        CartNotFoundException.class,
                        () -> cartService.addItemToCart(999L, request)
                );

        assertEquals(
                "Cart not found with id: 999",
                exception.getMessage()
        );

        verify(cartRepository).findById(999L);
        verify(productRepository, never()).findById(anyLong());
        verify(cartItemRepository, never()).save(any(CartItem.class));
        verify(domainEventPublisher, never()).publish(any());
    }

    @Test
    void addItemToCart_shouldThrowProductNotFoundException() {

        CartItemRequest request =
                new CartItemRequest(999L, 3);

        when(cartRepository.findById(1L))
                .thenReturn(Optional.of(cart));

        when(productRepository.findById(999L))
                .thenReturn(Optional.empty());

        ProductNotFoundException exception =
                assertThrows(
                        ProductNotFoundException.class,
                        () -> cartService.addItemToCart(1L, request)
                );

        assertEquals(
                "Product not found with id: 999",
                exception.getMessage()
        );

        verify(cartRepository).findById(1L);
        verify(productRepository).findById(999L);
        verify(cartItemRepository, never()).save(any(CartItem.class));
        verify(domainEventPublisher, never()).publish(any());
    }

    // =========================================================
    // UPDATE CART ITEM
    // =========================================================

    @Test
    void updateCartItem_shouldUpdateItemSuccessfully() {

        CartItemRequest request =
                new CartItemRequest(20L, 5);

        Product newProduct = mock(Product.class);

        when(cartRepository.findById(1L))
                .thenReturn(Optional.of(cart));

        when(cartItemRepository.findByIdAndCartId(100L, 1L))
                .thenReturn(Optional.of(cartItem));

        when(productRepository.findById(20L))
                .thenReturn(Optional.of(newProduct));

        when(cartItemRepository.save(cartItem))
                .thenReturn(cartItem);

        when(cart.getId())
                .thenReturn(1L);

        when(cartItem.getId())
                .thenReturn(100L);

        when(cartItem.getCart())
                .thenReturn(cart);

        when(cartItem.getProduct())
                .thenReturn(newProduct);

        when(cartItem.getQuantity())
                .thenReturn(5);

        when(newProduct.getId())
                .thenReturn(20L);

        when(newProduct.getName())
                .thenReturn("Phone");

        when(eventEnvelopeFactory.create(
                eq(EventType.CART_ITEM_UPDATED),
                eq("CART"),
                eq(1L),
                any(Map.class)
        )).thenReturn(cartEvent);

        CartItemResponse response =
                cartService.updateCartItem(
                        1L,
                        100L,
                        request
                );

        assertNotNull(response);
        assertEquals(100L, response.getId());
        assertEquals(1L, response.getCartId());
        assertEquals(20L, response.getProductId());
        assertEquals("Phone", response.getProductName());
        assertEquals(5, response.getQuantity());

        verify(cartRepository).findById(1L);
        verify(cartItemRepository)
                .findByIdAndCartId(100L, 1L);
        verify(productRepository).findById(20L);

        verify(cartItem).setProduct(newProduct);
        verify(cartItem).setQuantity(5);

        verify(cartItemRepository).save(cartItem);

        verify(eventEnvelopeFactory).create(
                eq(EventType.CART_ITEM_UPDATED),
                eq("CART"),
                eq(1L),
                any(Map.class)
        );

        verify(domainEventPublisher).publish(cartEvent);
    }

    @Test
    void updateCartItem_shouldThrowCartNotFoundExceptionWhenCartDoesNotExist() {

        CartItemRequest request =
                new CartItemRequest(20L, 5);

        when(cartRepository.findById(999L))
                .thenReturn(Optional.empty());

        CartNotFoundException exception =
                assertThrows(
                        CartNotFoundException.class,
                        () -> cartService.updateCartItem(
                                999L,
                                100L,
                                request
                        )
                );

        assertEquals(
                "Cart not found with id: 999",
                exception.getMessage()
        );

        verify(cartRepository).findById(999L);
        verify(cartItemRepository, never())
                .findByIdAndCartId(anyLong(), anyLong());
        verify(productRepository, never()).findById(anyLong());
        verify(domainEventPublisher, never()).publish(any());
    }

    @Test
    void updateCartItem_shouldThrowCartNotFoundExceptionWhenItemDoesNotExist() {

        CartItemRequest request =
                new CartItemRequest(20L, 5);

        when(cartRepository.findById(1L))
                .thenReturn(Optional.of(cart));

        when(cartItemRepository.findByIdAndCartId(999L, 1L))
                .thenReturn(Optional.empty());

        CartNotFoundException exception =
                assertThrows(
                        CartNotFoundException.class,
                        () -> cartService.updateCartItem(
                                1L,
                                999L,
                                request
                        )
                );

        assertEquals(
                "Cart item not found with id: 999 for cart id: 1",
                exception.getMessage()
        );

        verify(cartRepository).findById(1L);
        verify(cartItemRepository)
                .findByIdAndCartId(999L, 1L);

        verify(productRepository, never()).findById(anyLong());
        verify(cartItemRepository, never()).save(any(CartItem.class));
        verify(domainEventPublisher, never()).publish(any());
    }

    @Test
    void updateCartItem_shouldThrowProductNotFoundException() {

        CartItemRequest request =
                new CartItemRequest(999L, 5);

        when(cartRepository.findById(1L))
                .thenReturn(Optional.of(cart));

        when(cartItemRepository.findByIdAndCartId(100L, 1L))
                .thenReturn(Optional.of(cartItem));

        when(productRepository.findById(999L))
                .thenReturn(Optional.empty());

        ProductNotFoundException exception =
                assertThrows(
                        ProductNotFoundException.class,
                        () -> cartService.updateCartItem(
                                1L,
                                100L,
                                request
                        )
                );

        assertEquals(
                "Product not found with id: 999",
                exception.getMessage()
        );

        verify(cartRepository).findById(1L);
        verify(cartItemRepository)
                .findByIdAndCartId(100L, 1L);
        verify(productRepository).findById(999L);

        verify(cartItem, never()).setProduct(any(Product.class));
        verify(cartItem, never()).setQuantity(anyInt());
        verify(cartItemRepository, never()).save(any(CartItem.class));
        verify(domainEventPublisher, never()).publish(any());
    }

    // =========================================================
    // DELETE CART ITEM
    // =========================================================

    @Test
    void deleteCartItem_shouldDeleteItemSuccessfully() {

        when(cartRepository.findById(1L))
                .thenReturn(Optional.of(cart));

        when(cartItemRepository.findByIdAndCartId(100L, 1L))
                .thenReturn(Optional.of(cartItem));

        when(cart.getId())
                .thenReturn(1L);

        when(cartItem.getId())
                .thenReturn(100L);

        when(eventEnvelopeFactory.create(
                eq(EventType.CART_ITEM_REMOVED),
                eq("CART"),
                eq(1L),
                any(Map.class)
        )).thenReturn(cartEvent);

        cartService.deleteCartItem(1L, 100L);

        verify(cartRepository).findById(1L);
        verify(cartItemRepository)
                .findByIdAndCartId(100L, 1L);
        verify(cartItemRepository).delete(cartItem);

        verify(eventEnvelopeFactory).create(
                eq(EventType.CART_ITEM_REMOVED),
                eq("CART"),
                eq(1L),
                any(Map.class)
        );

        verify(domainEventPublisher).publish(cartEvent);
    }

    @Test
    void deleteCartItem_shouldThrowCartNotFoundExceptionWhenCartDoesNotExist() {

        when(cartRepository.findById(999L))
                .thenReturn(Optional.empty());

        CartNotFoundException exception =
                assertThrows(
                        CartNotFoundException.class,
                        () -> cartService.deleteCartItem(999L, 100L)
                );

        assertEquals(
                "Cart not found with id: 999",
                exception.getMessage()
        );

        verify(cartRepository).findById(999L);
        verify(cartItemRepository, never())
                .findByIdAndCartId(anyLong(), anyLong());
        verify(cartItemRepository, never())
                .delete(any(CartItem.class));
        verify(domainEventPublisher, never()).publish(any());
    }

    @Test
    void deleteCartItem_shouldThrowCartNotFoundExceptionWhenItemDoesNotExist() {

        when(cartRepository.findById(1L))
                .thenReturn(Optional.of(cart));

        when(cartItemRepository.findByIdAndCartId(999L, 1L))
                .thenReturn(Optional.empty());

        CartNotFoundException exception =
                assertThrows(
                        CartNotFoundException.class,
                        () -> cartService.deleteCartItem(1L, 999L)
                );

        assertEquals(
                "Cart item not found with id: 999 for cart id: 1",
                exception.getMessage()
        );

        verify(cartRepository).findById(1L);
        verify(cartItemRepository)
                .findByIdAndCartId(999L, 1L);
        verify(cartItemRepository, never())
                .delete(any(CartItem.class));
        verify(domainEventPublisher, never()).publish(any());
    }
}