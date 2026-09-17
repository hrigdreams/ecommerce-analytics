package com.ecommerce.analytics.controller;

import com.ecommerce.analytics.dto.OrderRequest;
import com.ecommerce.analytics.dto.OrderResponse;
import com.ecommerce.analytics.entity.OrderStatus;
import com.ecommerce.analytics.service.OrderService;
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
@RequestMapping("/api/v1/orders")
@Tag(
        name = "Order",
        description = "APIs for creating and managing customer orders"
)
public class OrderController {

    private final OrderService orderService;

    public OrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    @Operation(
            summary = "Create an order",
            description = "Creates a new order for a user with one or more products."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Order created successfully",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = OrderResponse.class)
                    )
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "Invalid order request"
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "User or product not found"
            )
    })
    @PostMapping
    public OrderResponse createOrder(
            @Valid @RequestBody OrderRequest request
    ) {
        return orderService.createOrder(request);
    }


    @Operation(
            summary = "Get all orders",
            description = "Returns a list containing all orders."
    )
    @ApiResponse(
            responseCode = "200",
            description = "Orders retrieved successfully"
    )
    @GetMapping
    public List<OrderResponse> getAllOrders() {
        return orderService.getAllOrders();
    }


    @Operation(
            summary = "Get order by ID",
            description = "Returns detailed information about a specific order."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Order found",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = OrderResponse.class)
                    )
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "Order not found"
            )
    })
    @GetMapping("/{id}")
    public OrderResponse getOrderById(
            @Parameter(
                    description = "Unique ID of the order",
                    example = "1"
            )
            @PathVariable Long id
    ) {
        return orderService.getOrderById(id);
    }


    @Operation(
            summary = "Update order status",
            description = "Changes the current status of an existing order."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Order status updated successfully",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = OrderResponse.class)
                    )
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "Order not found"
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "Invalid order status"
            )
    })
    @PatchMapping("/{id}/status")
    public OrderResponse updateOrderStatus(

            @Parameter(
                    description = "Unique ID of the order",
                    example = "1"
            )
            @PathVariable Long id,

            @Parameter(
                    description = "New status for the order",
                    example = "CONFIRMED"
            )
            @RequestParam OrderStatus status
    ) {
        return orderService.updateOrderStatus(id, status.name());
    }


    @Operation(
            summary = "Delete an order",
            description = "Deletes an existing order using its ID."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Order deleted successfully"
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "Order not found"
            )
    })
    @DeleteMapping("/{id}")
    public void deleteOrder(
            @Parameter(
                    description = "Unique ID of the order",
                    example = "1"
            )
            @PathVariable Long id
    ) {
        orderService.deleteOrder(id);
    }
}