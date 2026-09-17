package com.ecommerce.analytics.controller;

import com.ecommerce.analytics.dto.PaymentRequest;
import com.ecommerce.analytics.dto.PaymentResponse;
import com.ecommerce.analytics.service.PaymentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/payments")
@Tag(
        name = "Payments",
        description = "APIs for creating, retrieving, and deleting payments"
)
public class PaymentController {

    private final PaymentService paymentService;

    public PaymentController(PaymentService paymentService) {
        this.paymentService = paymentService;
    }


    // ============================================================
    // CREATE PAYMENT
    // ============================================================

    @PostMapping
    @Operation(
            summary = "Create a payment",
            description = "Creates a new payment for an order."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Payment created successfully"
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "Invalid payment data"
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "Order or related resource not found"
            )
    })
    public PaymentResponse createPayment(
            @Valid @RequestBody PaymentRequest request
    ) {
        return paymentService.createPayment(request);
    }


    // ============================================================
    // GET ALL PAYMENTS
    // ============================================================

    @GetMapping
    @Operation(
            summary = "Get all payments",
            description = "Returns all payments."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Payments retrieved successfully"
            )
    })
    public List<PaymentResponse> getAllPayments() {
        return paymentService.getAllPayments();
    }


    // ============================================================
    // GET PAYMENT BY ID
    // ============================================================

    @GetMapping("/{id}")
    @Operation(
            summary = "Get payment by ID",
            description = "Retrieves a payment using its unique ID."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Payment retrieved successfully"
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "Payment not found"
            )
    })
    public PaymentResponse getPaymentById(
            @Parameter(
                    description = "Unique payment ID",
                    example = "1"
            )
            @PathVariable Long id
    ) {
        return paymentService.getPaymentById(id);
    }


    // ============================================================
    // DELETE PAYMENT
    // ============================================================

    @DeleteMapping("/{id}")
    @Operation(
            summary = "Delete a payment",
            description = "Deletes an existing payment using its unique ID."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Payment deleted successfully"
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "Payment not found"
            )
    })
    public void deletePayment(
            @Parameter(
                    description = "Unique payment ID",
                    example = "1"
            )
            @PathVariable Long id
    ) {
        paymentService.deletePayment(id);
    }
}