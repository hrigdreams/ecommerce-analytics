package com.ecommerce.analytics.controller;

import com.ecommerce.analytics.entity.CartAnalytics;
import com.ecommerce.analytics.entity.CartItemAnalytics;
import com.ecommerce.analytics.entity.CategoryAnalytics;
import com.ecommerce.analytics.entity.CustomerAnalytics;
import com.ecommerce.analytics.entity.GeoAnalytics;
import com.ecommerce.analytics.entity.OrderAnalytics;
import com.ecommerce.analytics.entity.OrderItemAnalytics;
import com.ecommerce.analytics.entity.ProductAnalytics;
import com.ecommerce.analytics.entity.TimeAnalytics;
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
 * consumer pipeline (OrderAnalyticsService, ProductAnalyticsService,
 * CartAnalyticsService, ReviewAnalyticsService, CustomerAnalyticsService,
 * CategoryAnalyticsService, TimeAnalyticsService, GeoAnalyticsService).
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
    public OrderAnalytics getOrderAnalytics(@PathVariable Long orderId) {
        return orderAnalyticsRepository.findById(orderId)
                .orElseThrow(() -> notFound("Order analytics not found for order " + orderId));
    }

    @Operation(summary = "Get order line items", description = "Line items recorded for a single order.")
    @GetMapping("/orders/{orderId}/items")
    public List<OrderItemAnalytics> getOrderItems(@PathVariable Long orderId) {
        return orderItemAnalyticsRepository.findByOrderId(orderId);
    }

    // ------------------------------------------------------------------
    // Product
    // ------------------------------------------------------------------

    @Operation(summary = "Get product analytics", description = "Views, units sold, revenue, cart frequency, and rating for a product.")
    @GetMapping("/products/{productId}")
    public ProductAnalytics getProductAnalytics(@PathVariable Long productId) {
        return productAnalyticsRepository.findById(productId)
                .orElseThrow(() -> notFound("Product analytics not found for product " + productId));
    }

    @Operation(summary = "List product analytics", description = "All product analytics rows.")
    @GetMapping("/products")
    public List<ProductAnalytics> getAllProductAnalytics() {
        return productAnalyticsRepository.findAll();
    }

    // ------------------------------------------------------------------
    // Cart
    // ------------------------------------------------------------------

    @Operation(summary = "Get cart analytics", description = "Item count / active status for a single cart.")
    @GetMapping("/carts/{cartId}")
    public CartAnalytics getCartAnalytics(@PathVariable Long cartId) {
        return cartAnalyticsRepository.findById(cartId)
                .orElseThrow(() -> notFound("Cart analytics not found for cart " + cartId));
    }

    @Operation(summary = "Get cart items", description = "All items (active and removed) ever added to a cart.")
    @GetMapping("/carts/{cartId}/items")
    public List<CartItemAnalytics> getCartItems(@PathVariable Long cartId) {
        return cartItemAnalyticsRepository.findByCartId(cartId);
    }

    @Operation(
            summary = "List abandoned carts",
            description = "Active carts with items whose last activity is older than the given number of minutes (default 60)."
    )
    @GetMapping("/carts/abandoned")
    public List<CartAnalytics> getAbandonedCarts(
            @Parameter(description = "Minutes of inactivity before a cart counts as abandoned", example = "60")
            @RequestParam(name = "staleMinutes", defaultValue = "60") long staleMinutes
    ) {
        Instant staleBefore = Instant.now().minus(staleMinutes, ChronoUnit.MINUTES);
        return cartAnalyticsRepository.findAbandoned(staleBefore);
    }

    // ------------------------------------------------------------------
    // Customer
    // ------------------------------------------------------------------

    @Operation(summary = "Get customer analytics", description = "Order counts, total spend, and average order value for one customer.")
    @GetMapping("/customers/{userId}")
    public CustomerAnalytics getCustomerAnalytics(@PathVariable Long userId) {
        return customerAnalyticsRepository.findById(userId)
                .orElseThrow(() -> notFound("Customer analytics not found for user " + userId));
    }

    @Operation(
            summary = "Customer summary",
            description = "Total customers, customers with at least one paid order (returning-capable), and new customers whose first order fell in [from, to)."
    )
    @GetMapping("/customers/summary")
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
    public CategoryAnalytics getCategoryAnalytics(@PathVariable Long categoryId) {
        return categoryAnalyticsRepository.findById(categoryId)
                .orElseThrow(() -> notFound("Category analytics not found for category " + categoryId));
    }

    @Operation(summary = "List category analytics", description = "All category analytics rows.")
    @GetMapping("/categories")
    public List<CategoryAnalytics> getAllCategoryAnalytics() {
        return categoryAnalyticsRepository.findAll();
    }

    // ------------------------------------------------------------------
    // Time
    // ------------------------------------------------------------------

    @Operation(
            summary = "Time-bucketed order/revenue rollups",
            description = "Orders, paid orders, revenue, units sold, and average order value bucketed by HOUR, DAY, WEEK, MONTH, or YEAR."
    )
    @GetMapping("/time")
    public List<TimeAnalytics> getTimeAnalytics(
            @Parameter(description = "HOUR, DAY, WEEK, MONTH, or YEAR", example = "DAY")
            @RequestParam(defaultValue = "DAY") String bucket,
            @Parameter(description = "Max number of buckets to return, most recent first")
            @RequestParam(defaultValue = "30") int limit
    ) {
        String bucketType = bucket.toUpperCase();
        List<TimeAnalytics> rows = timeAnalyticsRepository.findByBucketTypeOrderByBucketStartDesc(bucketType);
        return rows.size() > limit ? rows.subList(0, limit) : rows;
    }

    // ------------------------------------------------------------------
    // Geography
    // ------------------------------------------------------------------

    @Operation(summary = "Geography rollups", description = "Orders, paid orders, and revenue by country/city.")
    @GetMapping("/geography")
    public List<GeoAnalytics> getGeoAnalytics() {
        return geoAnalyticsRepository.findAll();
    }

    private ResponseStatusException notFound(String message) {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, message);
    }
}
