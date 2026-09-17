package com.ecommerce.analytics.service;

import com.ecommerce.analytics.dto.CartRequest;
import com.ecommerce.analytics.dto.CartItemRequest;
import com.ecommerce.analytics.dto.CartItemResponse;
import com.ecommerce.analytics.dto.CartResponse;
import com.ecommerce.analytics.entity.Cart;
import com.ecommerce.analytics.entity.CartItem;
import com.ecommerce.analytics.entity.Product;
import com.ecommerce.analytics.entity.User;
import com.ecommerce.analytics.exception.CartNotFoundException;
import com.ecommerce.analytics.exception.ProductNotFoundException;
import com.ecommerce.analytics.exception.UserNotFoundException;
import com.ecommerce.analytics.repository.CartItemRepository;
import com.ecommerce.analytics.repository.CartRepository;
import com.ecommerce.analytics.repository.ProductRepository;
import com.ecommerce.analytics.repository.UserRepository;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class CartService {

    private final CartRepository cartRepository;
    private final CartItemRepository cartItemRepository;
    private final UserRepository userRepository;
    private final ProductRepository productRepository;

    public CartService(
            CartRepository cartRepository,
            CartItemRepository cartItemRepository,
            UserRepository userRepository,
            ProductRepository productRepository
    ) {
        this.cartRepository = cartRepository;
        this.cartItemRepository = cartItemRepository;
        this.userRepository = userRepository;
        this.productRepository = productRepository;
    }

    public CartResponse createCart(CartRequest request) {
        User user = userRepository.findById(request.getUserId())
                .orElseThrow(() -> new UserNotFoundException("User not found with id: " + request.getUserId()));

        Cart cart = cartRepository.findByUserId(request.getUserId())
                .orElse(new Cart(user));

        Cart saved = cartRepository.save(cart);
        return mapToResponse(saved);
    }

    public CartResponse getCartById(Long id) {
        Cart cart = cartRepository.findById(id)
                .orElseThrow(() -> new CartNotFoundException("Cart not found with id: " + id));
        return mapToResponse(cart);
    }

    public List<CartResponse> getAllCarts() {
        return cartRepository.findAll().stream().map(this::mapToResponse).toList();
    }

    public CartResponse updateCart(Long id, CartRequest request) {
        Cart cart = cartRepository.findById(id)
                .orElseThrow(() -> new CartNotFoundException("Cart not found with id: " + id));

        User user = userRepository.findById(request.getUserId())
                .orElseThrow(() -> new UserNotFoundException("User not found with id: " + request.getUserId()));

        cart.setUser(user);
        Cart updated = cartRepository.save(cart);
        return mapToResponse(updated);
    }

    public void deleteCart(Long id) {
        Cart cart = cartRepository.findById(id)
                .orElseThrow(() -> new CartNotFoundException("Cart not found with id: " + id));
        cartRepository.delete(cart);
    }

    public CartItemResponse addItemToCart(Long cartId, CartItemRequest request) {
        Cart cart = cartRepository.findById(cartId)
                .orElseThrow(() -> new CartNotFoundException("Cart not found with id: " + cartId));

        Product product = productRepository.findById(request.getProductId())
                .orElseThrow(() -> new ProductNotFoundException("Product not found with id: " + request.getProductId()));

        CartItem item = new CartItem(product, request.getQuantity());
        item.setCart(cart);
        CartItem saved = cartItemRepository.save(item);

        return mapItemToResponse(saved);
    }

    public CartItemResponse updateCartItem(Long cartId, Long itemId, CartItemRequest request) {
        cartRepository.findById(cartId)
                .orElseThrow(() -> new CartNotFoundException("Cart not found with id: " + cartId));

        CartItem item = cartItemRepository.findByIdAndCartId(itemId, cartId)
                .orElseThrow(() -> new CartNotFoundException("Cart item not found with id: " + itemId + " for cart id: " + cartId));

        Product product = productRepository.findById(request.getProductId())
                .orElseThrow(() -> new ProductNotFoundException("Product not found with id: " + request.getProductId()));

        item.setProduct(product);
        item.setQuantity(request.getQuantity());
        CartItem updated = cartItemRepository.save(item);
        return mapItemToResponse(updated);
    }

    public void deleteCartItem(Long cartId, Long itemId) {
        cartRepository.findById(cartId)
                .orElseThrow(() -> new CartNotFoundException("Cart not found with id: " + cartId));

        CartItem item = cartItemRepository.findByIdAndCartId(itemId, cartId)
                .orElseThrow(() -> new CartNotFoundException("Cart item not found with id: " + itemId + " for cart id: " + cartId));

        cartItemRepository.delete(item);
    }

    private CartResponse mapToResponse(Cart cart) {
        return new CartResponse(cart.getId(), cart.getUser() != null ? cart.getUser().getId() : null);
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
