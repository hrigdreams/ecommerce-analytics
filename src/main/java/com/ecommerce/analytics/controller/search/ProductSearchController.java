package com.ecommerce.analytics.controller.search;

import com.ecommerce.analytics.dto.search.ProductSearchResponse;
import com.ecommerce.analytics.elasticsearch.ProductSearchService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * Elasticsearch-backed product search - filtering/faceting use case, kept
 * under its own /api/v1/search/* namespace rather than /api/v1/analytics/*
 * (deliberate: this is a search feature, not an analytics one - see
 * ARCHITECTURE_SPARK.md / the Unified Analytics decision on why
 * Elasticsearch was NOT folded into GET /api/v1/analytics/unified).
 */
@RestController
@RequestMapping("/api/v1/search")
@Tag(name = "Product Search", description = "Elasticsearch-backed product search and filtering")
public class ProductSearchController {

    private final ProductSearchService productSearchService;

    public ProductSearchController(ProductSearchService productSearchService) {
        this.productSearchService = productSearchService;
    }

    @Operation(
            summary = "Search and filter products",
            description = "Free-text match on product name, combinable with categoryId/price-range/minRating filters. " +
                    "With no filters at all, returns the full catalog, paginated. Results are deterministically " +
                    "ordered (relevance then id, or id alone with no text query) so pagination is stable. " +
                    "minPrice greater than maxPrice is rejected with 400."
    )
    @GetMapping("/products")
    public ProductSearchResponse searchProducts(
            @Parameter(description = "Free-text match against product name") @RequestParam(required = false) String q,
            @Parameter(description = "Exact category id") @RequestParam(required = false) Long categoryId,
            @Parameter(description = "Minimum price (inclusive)") @RequestParam(required = false) Double minPrice,
            @Parameter(description = "Maximum price (inclusive)") @RequestParam(required = false) Double maxPrice,
            @Parameter(description = "Minimum average rating (inclusive), e.g. 4.0") @RequestParam(required = false) Double minRating,
            @Parameter(description = "Zero-based page index") @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Page size (max " + ProductSearchService.MAX_PAGE_SIZE + ")")
            @RequestParam(defaultValue = "" + ProductSearchService.DEFAULT_PAGE_SIZE) int size
    ) {
        int safeSize = Math.min(Math.max(size, 1), ProductSearchService.MAX_PAGE_SIZE);
        int safePage = Math.max(page, 0);

        try {
            return productSearchService.search(q, categoryId, minPrice, maxPrice, minRating, safePage, safeSize);
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
        }
    }
}
