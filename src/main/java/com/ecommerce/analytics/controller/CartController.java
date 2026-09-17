package com.ecommerce.analytics.controller;

import com.ecommerce.analytics.dto.CartItemRequest;
import com.ecommerce.analytics.dto.CartItemResponse;
import com.ecommerce.analytics.dto.CartRequest;
import com.ecommerce.analytics.dto.CartResponse;
import com.ecommerce.analytics.service.CartService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/carts")
@Tag(
        name = "Cart",
        description = "APIs for managing shopping carts and cart items"
)
public class CartController {

    private final CartService cartService;

    public CartController(CartService cartService) {
        this.cartService = cartService;
    }

    @Operation(
            summary = "Create a cart",
            description = "Creates a new shopping cart for a user."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Cart created successfully",
                    content = @Content(
                            schema = @Schema(implementation = CartResponse.class)
                    )
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "Invalid request"
            )
    })
    @PostMapping
    public CartResponse createCart(
            @Valid @RequestBody CartRequest request
    ) {
        return cartService.createCart(request);
    }


    @Operation(
            summary = "Get all carts",
            description = "Returns all shopping carts."
    )
    @ApiResponse(
            responseCode = "200",
            description = "Carts retrieved successfully"
    )
    @GetMapping
    public List<CartResponse> getAllCarts() {
        return cartService.getAllCarts();
    }


    @Operation(
            summary = "Get cart by ID",
            description = "Returns a shopping cart using its ID."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Cart found",
                    content = @Content(
                            schema = @Schema(implementation = CartResponse.class)
                    )
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "Cart not found"
            )
    })
    @GetMapping("/{id}")
    public CartResponse getCartById(
            @Parameter(
                    description = "ID of the cart",
                    example = "1"
            )
            @PathVariable Long id
    ) {
        return cartService.getCartById(id);
    }


    @Operation(
            summary = "Update a cart",
            description = "Updates the cart information."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Cart updated successfully",
                    content = @Content(
                            schema = @Schema(implementation = CartResponse.class)
                    )
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "Invalid request"
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "Cart not found"
            )
    })
    @PutMapping("/{id}")
    public CartResponse updateCart(
            @Parameter(
                    description = "ID of the cart",
                    example = "1"
            )
            @PathVariable Long id,

            @Valid @RequestBody CartRequest request
    ) {
        return cartService.updateCart(id, request);
    }


    @Operation(
            summary = "Delete a cart",
            description = "Deletes a shopping cart using its ID."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Cart deleted successfully"
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "Cart not found"
            )
    })
    @DeleteMapping("/{id}")
    public void deleteCart(
            @Parameter(
                    description = "ID of the cart",
                    example = "1"
            )
            @PathVariable Long id
    ) {
        cartService.deleteCart(id);
    }


    @Operation(
            summary = "Add item to cart",
            description = "Adds a product with a specified quantity to a shopping cart."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Item added successfully",
                    content = @Content(
                            schema = @Schema(implementation = CartItemResponse.class)
                    )
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "Invalid request"
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "Cart or product not found"
            )
    })
    @PostMapping("/{cartId}/items")
    public CartItemResponse addItemToCart(
            @Parameter(
                    description = "ID of the cart",
                    example = "1"
            )
            @PathVariable Long cartId,

            @Valid @RequestBody CartItemRequest request
    ) {
        return cartService.addItemToCart(cartId, request);
    }


    @Operation(
            summary = "Update cart item",
            description = "Updates the quantity of an item already present in the cart."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Cart item updated successfully",
                    content = @Content(
                            schema = @Schema(implementation = CartItemResponse.class)
                    )
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "Invalid request"
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "Cart or cart item not found"
            )
    })
    @PutMapping("/{cartId}/items/{itemId}")
    public CartItemResponse updateCartItem(
            @Parameter(
                    description = "ID of the cart",
                    example = "1"
            )
            @PathVariable Long cartId,

            @Parameter(
                    description = "ID of the cart item",
                    example = "1"
            )
            @PathVariable Long itemId,

            @Valid @RequestBody CartItemRequest request
    ) {
        return cartService.updateCartItem(cartId, itemId, request);
    }


    @Operation(
            summary = "Delete cart item",
            description = "Removes an item from a shopping cart."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Cart item deleted successfully"
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "Cart or cart item not found"
            )
    })
    @DeleteMapping("/{cartId}/items/{itemId}")
    public void deleteCartItem(
            @Parameter(
                    description = "ID of the cart",
                    example = "1"
            )
            @PathVariable Long cartId,

            @Parameter(
                    description = "ID of the cart item",
                    example = "1"
            )
            @PathVariable Long itemId
    ) {
        cartService.deleteCartItem(cartId, itemId);
    }
}