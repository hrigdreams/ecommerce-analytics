package com.ecommerce.analytics.controller;

import com.ecommerce.analytics.config.CacheConfig;
import com.ecommerce.analytics.dto.analytics.CartAnalyticsResponse;
import com.ecommerce.analytics.dto.analytics.CartItemAnalyticsResponse;
import com.ecommerce.analytics.dto.analytics.CategoryAnalyticsResponse;
import com.ecommerce.analytics.dto.analytics.CustomerAnalyticsResponse;
import com.ecommerce.analytics.dto.analytics.GeoAnalyticsResponse;
import com.ecommerce.analytics.dto.analytics.OrderAnalyticsResponse;
import com.ecommerce.analytics.dto.analytics.OrderItemAnalyticsResponse;
import com.ecommerce.analytics.dto.analytics.ProductAnalyticsResponse;
import com.ecommerce.analytics.dto.analytics.TimeAnalyticsResponse;
import com.ecommerce.analytics.entity.CartAnalytics;
import com.ecommerce.analytics.repository.CartAnalyticsRepository;
import com.ecommerce.analytics.repository.CartItemAnalyticsRepository;
import com.ecommerce.analytics.repository.CategoryAnalyticsRepository;
import com.ecommerce.analytics.repository.CustomerAnalyticsRepository;
import com.ecommerce.analytics.repository.GeoAnalyticsRepository;
import com.ecommerce.analytics.repository.OrderAnalyticsRepository;
import com.ecommerce.analytics.repository.OrderItemAnalyticsRepository;
import com.ecommerce.analytics.repository.ProductAnalyticsRepository;
import com.ecommerce.analytics.repository.TimeAnalyticsRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Read-only endpoints over the analytics read models built by the Kafka
 * consumer pipeline. Every response here is a DTO (record), never a JPA
 * entity directly, so the entity's internal shape (columns, indexes, JPA
 * annotations) can change without breaking API consumers.
 *
 * These are all populated asynchronously from domain events, so results
 * reflect the latest event the consumer has processed, not necessarily the
 * latest write to the transactional tables (orders, products, ...).
 */
@RestController
@RequestMapping("/api/v1/analytics")
@Tag(
        name = "Analytics",
        description = "Read-only analytics/reporting APIs backed by the event-driven read models"
)
public class AnalyticsController {

    private final OrderAnalyticsRepository orderAnalyticsRepository;
    private final OrderItemAnalyticsRepository orderItemAnalyticsRepository;
    private final ProductAnalyticsRepository productAnalyticsRepository;
    private final CartAnalyticsRepository cartAnalyticsRepository;
    private final CartItemAnalyticsRepository cartItemAnalyticsRepository;
    private final CustomerAnalyticsRepository customerAnalyticsRepository;
    private final CategoryAnalyticsRepository categoryAnalyticsRepository;
    private final TimeAnalyticsRepository timeAnalyticsRepository;
    private final GeoAnalyticsRepository geoAnalyticsRepository;

    public AnalyticsController(
            OrderAnalyticsRepository orderAnalyticsRepository,
            OrderItemAnalyticsRepository orderItemAnalyticsRepository,
            ProductAnalyticsRepository productAnalyticsRepository,
            CartAnalyticsRepository cartAnalyticsRepository,
            CartItemAnalyticsRepository cartItemAnalyticsRepository,
            CustomerAnalyticsRepository customerAnalyticsRepository,
            CategoryAnalyticsRepository categoryAnalyticsRepository,
            TimeAnalyticsRepository timeAnalyticsRepository,
            GeoAnalyticsRepository geoAnalyticsRepository
    ) {
        this.orderAnalyticsRepository = orderAnalyticsRepository;
        this.orderItemAnalyticsRepository = orderItemAnalyticsRepository;
        this.productAnalyticsRepository = productAnalyticsRepository;
        this.cartAnalyticsRepository = cartAnalyticsRepository;
        this.cartItemAnalyticsRepository = cartItemAnalyticsRepository;
        this.customerAnalyticsRepository = customerAnalyticsRepository;
        this.categoryAnalyticsRepository = categoryAnalyticsRepository;
        this.timeAnalyticsRepository = timeAnalyticsRepository;
        this.geoAnalyticsRepository = geoAnalyticsRepository;
    }

    // ------------------------------------------------------------------
    // Order + Payment
    // ------------------------------------------------------------------

    @Operation(summary = "Get order analytics", description = "Read-model row for a single order (status, payment, total, paid flag).")
    @GetMapping("/orders/{orderId}")
    public OrderAnalyticsResponse getOrderAnalytics(@PathVariable Long orderId) {
        return orderAnalyticsRepository.findById(orderId)
                .map(OrderAnalyticsResponse::from)
                .orElseThrow(() -> notFound("Order analytics not found for order " + orderId));
    }

    @Operation(summary = "Get order line items", description = "Line items recorded for a single order.")
    @GetMapping("/orders/{orderId}/items")
    public List<OrderItemAnalyticsResponse> getOrderItems(@PathVariable Long orderId) {
        return orderItemAnalyticsRepository.findByOrderId(orderId).stream()
                .map(OrderItemAnalyticsResponse::from)
                .toList();
    }

    // ------------------------------------------------------------------
    // Product
    // ------------------------------------------------------------------

    @Operation(summary = "Get product analytics", description = "Views, units sold, revenue, cart frequency, and rating for a product.")
    @GetMapping("/products/{productId}")
    public ProductAnalyticsResponse getProductAnalytics(@PathVariable Long productId) {
        return productAnalyticsRepository.findById(productId)
                .map(ProductAnalyticsResponse::from)
                .orElseThrow(() -> notFound("Product analytics not found for product " + productId));
    }

    @Operation(
            summary = "List product analytics (paginated)",
            description = "Product analytics rows, newest-updated first by default. Use page/size to page through them."
    )
    @GetMapping("/products")
    public Page<ProductAnalyticsResponse> getAllProductAnalytics(
            @Parameter(description = "Zero-based page index") @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Page size (max 100)") @RequestParam(defaultValue = "20") int size
    ) {
        Pageable pageable = boundedPageRequest(page, size, "updatedAt");
        return productAnalyticsRepository.findAll(pageable).map(ProductAnalyticsResponse::from);
    }

    // ------------------------------------------------------------------
    // Cart
    // ------------------------------------------------------------------

    @Operation(summary = "Get cart analytics", description = "Item count / active status for a single cart.")
    @GetMapping("/carts/{cartId}")
    public CartAnalyticsResponse getCartAnalytics(@PathVariable Long cartId) {
        return cartAnalyticsRepository.findById(cartId)
                .map(CartAnalyticsResponse::from)
                .orElseThrow(() -> notFound("Cart analytics not found for cart " + cartId));
    }

    @Operation(summary = "Get cart items", description = "All items (active and removed) ever added to a cart.")
    @GetMapping("/carts/{cartId}/items")
    public List<CartItemAnalyticsResponse> getCartItems(@PathVariable Long cartId) {
        return cartItemAnalyticsRepository.findByCartId(cartId).stream()
                .map(CartItemAnalyticsResponse::from)
                .toList();
    }

    @Operation(
            summary = "List abandoned carts",
            description = "Active carts with items whose last activity is older than the given number of minutes (default 60)."
    )
    @GetMapping("/carts/abandoned")
    public List<CartAnalyticsResponse> getAbandonedCarts(
            @Parameter(description = "Minutes of inactivity before a cart counts as abandoned", example = "60")
            @RequestParam(name = "staleMinutes", defaultValue = "60") long staleMinutes
    ) {
        Instant staleBefore = Instant.now().minus(staleMinutes, ChronoUnit.MINUTES);
        List<CartAnalytics> abandoned = cartAnalyticsRepository.findAbandoned(staleBefore);
        return abandoned.stream().map(CartAnalyticsResponse::from).toList();
    }

    // ------------------------------------------------------------------
    // Customer
    // ------------------------------------------------------------------

    @Operation(summary = "Get customer analytics", description = "Order counts, total spend, and average order value for one customer.")
    @GetMapping("/customers/{userId}")
    public CustomerAnalyticsResponse getCustomerAnalytics(@PathVariable Long userId) {
        return customerAnalyticsRepository.findById(userId)
                .map(CustomerAnalyticsResponse::from)
                .orElseThrow(() -> notFound("Customer analytics not found for user " + userId));
    }

    /**
     * Cached for 30s: this does 3 full-table-ish aggregate queries, and the
     * underlying counts only change as the Kafka consumer processes events,
     * so re-running it on every dashboard poll is wasted work. Cache key is
     * the (from, to) pair, so different date ranges get separate entries.
     */
    @Operation(
            summary = "Customer summary",
            description = "Total customers, customers with at least one paid order (returning-capable), and new customers whose first order fell in [from, to). Cached for 30 seconds."
    )
    @GetMapping("/customers/summary")
    @Cacheable(cacheNames = CacheConfig.CUSTOMER_SUMMARY_CACHE, key = "#from + '_' + #to")
    public Map<String, Object> getCustomerSummary(
            @Parameter(description = "ISO-8601 instant, inclusive. Defaults to 30 days ago.")
            @RequestParam(required = false) Instant from,
            @Parameter(description = "ISO-8601 instant, exclusive. Defaults to now.")
            @RequestParam(required = false) Instant to
    ) {
        Instant rangeTo = to != null ? to : Instant.now();
        Instant rangeFrom = from != null ? from : rangeTo.minus(30, ChronoUnit.DAYS);

        Map<String, Object> summary = new HashMap<>();
        summary.put("totalCustomers", customerAnalyticsRepository.count());
        summary.put("customersWithPaidOrders", customerAnalyticsRepository.countByPaidOrdersGreaterThan(0));
        summary.put("newCustomers", customerAnalyticsRepository.countByFirstOrderAtBetween(rangeFrom, rangeTo));
        summary.put("from", rangeFrom);
        summary.put("to", rangeTo);
        return summary;
    }

    // ------------------------------------------------------------------
    // Category
    // ------------------------------------------------------------------

    @Operation(summary = "Get category analytics", description = "Product count, units sold, revenue, and average rating for a category.")
    @GetMapping("/categories/{categoryId}")
    public CategoryAnalyticsResponse getCategoryAnalytics(@PathVariable Long categoryId) {
        return categoryAnalyticsRepository.findById(categoryId)
                .map(CategoryAnalyticsResponse::from)
                .orElseThrow(() -> notFound("Category analytics not found for category " + categoryId));
    }

    @Operation(
            summary = "List category analytics (paginated)",
            description = "Category analytics rows. Use page/size to page through them."
    )
    @GetMapping("/categories")
    public Page<CategoryAnalyticsResponse> getAllCategoryAnalytics(
            @Parameter(description = "Zero-based page index") @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Page size (max 100)") @RequestParam(defaultValue = "20") int size
    ) {
        Pageable pageable = boundedPageRequest(page, size, "updatedAt");
        return categoryAnalyticsRepository.findAll(pageable).map(CategoryAnalyticsResponse::from);
    }

    // ------------------------------------------------------------------
    // Time
    // ------------------------------------------------------------------

    /** Cached for 30s per (bucket, limit) key — same reasoning as the customer summary. */
    @Operation(
            summary = "Time-bucketed order/revenue rollups",
            description = "Orders, paid orders, revenue, units sold, and average order value bucketed by HOUR, DAY, WEEK, MONTH, or YEAR. Cached for 30 seconds."
    )
    @GetMapping("/time")
    @Cacheable(cacheNames = CacheConfig.TIME_ANALYTICS_CACHE, key = "#bucket + '_' + #limit")
    public List<TimeAnalyticsResponse> getTimeAnalytics(
            @Parameter(description = "HOUR, DAY, WEEK, MONTH, or YEAR", example = "DAY")
            @RequestParam(defaultValue = "DAY") String bucket,
            @Parameter(description = "Max number of buckets to return, most recent first")
            @RequestParam(defaultValue = "30") int limit
    ) {
        String bucketType = bucket.toUpperCase();
        var rows = timeAnalyticsRepository.findByBucketTypeOrderByBucketStartDesc(bucketType);
        var bounded = rows.size() > limit ? rows.subList(0, limit) : rows;
        return bounded.stream().map(TimeAnalyticsResponse::from).toList();
    }

    // ------------------------------------------------------------------
    // Geography
    // ------------------------------------------------------------------

    @Operation(summary = "Geography rollups", description = "Orders, paid orders, and revenue by country/city.")
    @GetMapping("/geography")
    public List<GeoAnalyticsResponse> getGeoAnalytics() {
        return geoAnalyticsRepository.findAll().stream().map(GeoAnalyticsResponse::from).toList();
    }

    // ------------------------------------------------------------------

    private ResponseStatusException notFound(String message) {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, message);
    }

    /** Caps page size so nobody can request size=1000000 and blow up the DB/response. */
    private Pageable boundedPageRequest(int page, int size, String sortBy) {
        int safePage = Math.max(page, 0);
        int safeSize = Math.min(Math.max(size, 1), 100);
        return PageRequest.of(safePage, safeSize, org.springframework.data.domain.Sort.by(sortBy).descending());
    }
}
