package com.ecommerce.analytics.controller;

import com.ecommerce.analytics.dto.ReviewRequest;
import com.ecommerce.analytics.dto.ReviewResponse;
import com.ecommerce.analytics.service.ReviewService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/reviews")
@Tag(
        name = "Reviews",
        description = "APIs for creating, retrieving, updating, and deleting product reviews"
)
public class ReviewController {

    private final ReviewService reviewService;

    public ReviewController(ReviewService reviewService) {
        this.reviewService = reviewService;
    }


    // ============================================================
    // CREATE REVIEW
    // ============================================================

    @PostMapping
    @Operation(
            summary = "Create a review",
            description = "Creates a new product review."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Review created successfully"
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "Invalid review data"
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "User or product not found"
            )
    })
    public ReviewResponse createReview(
            @Valid @RequestBody ReviewRequest request
    ) {
        return reviewService.createReview(request);
    }


    // ============================================================
    // GET ALL REVIEWS
    // ============================================================

    @GetMapping
    @Operation(
            summary = "Get all reviews",
            description = "Returns all reviews."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Reviews retrieved successfully"
            )
    })
    public List<ReviewResponse> getAllReviews() {
        return reviewService.getAllReviews();
    }


    // ============================================================
    // GET REVIEW BY ID
    // ============================================================

    @GetMapping("/{id}")
    @Operation(
            summary = "Get review by ID",
            description = "Retrieves a review using its unique ID."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Review retrieved successfully"
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "Review not found"
            )
    })
    public ReviewResponse getReviewById(
            @Parameter(
                    description = "Unique review ID",
                    example = "1"
            )
            @PathVariable Long id
    ) {
        return reviewService.getReviewById(id);
    }


    // ============================================================
    // UPDATE REVIEW
    // ============================================================

    @PutMapping("/{id}")
    @Operation(
            summary = "Update a review",
            description = "Updates an existing review using its unique ID."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Review updated successfully"
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "Invalid review data"
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "Review not found"
            )
    })
    public ReviewResponse updateReview(
            @Parameter(
                    description = "Unique review ID",
                    example = "1"
            )
            @PathVariable Long id,

            @Valid @RequestBody ReviewRequest request
    ) {
        return reviewService.updateReview(id, request);
    }


    // ============================================================
    // DELETE REVIEW
    // ============================================================

    @DeleteMapping("/{id}")
    @Operation(
            summary = "Delete a review",
            description = "Deletes an existing review using its unique ID."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Review deleted successfully"
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "Review not found"
            )
    })
    public void deleteReview(
            @Parameter(
                    description = "Unique review ID",
                    example = "1"
            )
            @PathVariable Long id
    ) {
        reviewService.deleteReview(id);
    }
}
