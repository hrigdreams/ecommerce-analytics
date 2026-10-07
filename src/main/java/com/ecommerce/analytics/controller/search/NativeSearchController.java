// src/main/java/com/ecommerce/analytics/controller/search/NativeSearchController.java
package com.ecommerce.analytics.controller.search;

import com.ecommerce.analytics.dto.search.ProductSearchRequest;
import com.ecommerce.analytics.dto.search.ProductSearchResponse;
import com.ecommerce.analytics.elasticsearch.ProductSearchNativeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * Native (Elasticsearch Java API Client) product search: full-text relevance
 * search with boosted multi-match, exact/range filters, and bucket
 * aggregations. Falls under the existing /api/v1/search/** security rule
 * (SecurityConfig) - no additional permit mapping needed.
 */
@RestController
@RequestMapping("/api/v1/search/native")
@Tag(name = "Native Product Search", description = "Elasticsearch Java API Client-backed full-text search with bucket aggregations")
public class NativeSearchController {

    private final ProductSearchNativeService productSearchNativeService;

    public NativeSearchController(ProductSearchNativeService productSearchNativeService) {
        this.productSearchNativeService = productSearchNativeService;
    }

    @Operation(
            summary = "Full-text product search with category and price-range bucket aggregations",
            description = "Multi-match relevance search (name^3, categoryName^2) with fuzziness(AUTO), " +
                    "combinable with categoryId/price-range filters. Returns category_buckets and " +
                    "price_range_buckets alongside paginated hits."
    )
    @GetMapping("/products")
    public ResponseEntity<ProductSearchResponse> searchProducts(
            @Parameter(description = "Free-text query, matched against name and categoryName") @RequestParam(required = false) String q,
            @Parameter(description = "Exact category id") @RequestParam(required = false) Long categoryId,
            @Parameter(description = "Minimum price (inclusive)") @RequestParam(required = false) Double minPrice,
            @Parameter(description = "Maximum price (inclusive)") @RequestParam(required = false) Double maxPrice,
            @Parameter(description = "Zero-based page index") @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Page size (max " + ProductSearchNativeService.MAX_PAGE_SIZE + ")")
            @RequestParam(defaultValue = "" + ProductSearchNativeService.DEFAULT_PAGE_SIZE) int size
    ) {
        try {
            ProductSearchRequest request = new ProductSearchRequest(q, categoryId, minPrice, maxPrice, page, size);
            return ResponseEntity.ok(productSearchNativeService.search(request));
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
        }
    }
}