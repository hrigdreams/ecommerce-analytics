package com.ecommerce.analytics.simulator;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Thin wrapper around the app's OWN REST API.
 * controller -> service -> Postgres -> DomainEventPublisher -> Kafka chain
 */
class SimulatorClient {

    private static final Logger log = LoggerFactory.getLogger(SimulatorClient.class);

    private final RestClient restClient;

    SimulatorClient(String baseUrl) {
        this.restClient = RestClient.builder().baseUrl(baseUrl).build();
    }

    // ------------------------------------------------------------------
    // Reads: sampling pools of EXISTING seeded resources
    // ------------------------------------------------------------------

    @SuppressWarnings("unchecked")
    List<Long> fetchUserIds(int max) {
        List<Map<String, Object>> users = restClient.get()
                .uri("/api/v1/users")
                .retrieve()
                .body(List.class);

        if (users == null) return List.of();

        return users.stream()
                .map(u -> ((Number) u.get("id")).longValue())
                .limit(max)
                .toList();
    }

    @SuppressWarnings("unchecked")
    List<Long> fetchProductIds(int max) {
        // Uses the paginated endpoint (GET /api/v1/products?page=...) so a large
        // catalog isn't pulled in one unbounded response.
        int pageSize = Math.min(Math.max(max, 1), 100);
        Map<String, Object> page = restClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/api/v1/products")
                        .queryParam("page", 0)
                        .queryParam("size", pageSize)
                        .build())
                .retrieve()
                .body(Map.class);

        if (page == null || page.get("content") == null) return List.of();

        List<Map<String, Object>> content = (List<Map<String, Object>>) page.get("content");
        return content.stream()
                .map(p -> ((Number) p.get("id")).longValue())
                .limit(max)
                .toList();
    }

    // ------------------------------------------------------------------
    // Cart
    // ------------------------------------------------------------------

    /** POST /api/v1/carts is get-or-create per user (CartService), so this is always safe to call. */
    long createOrGetCart(long userId) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("userId", userId);

        Map<String, Object> response = restClient.post()
                .uri("/api/v1/carts")
                .body(body)
                .retrieve()
                .body(Map.class);

        return ((Number) response.get("id")).longValue();
    }

    long addCartItem(long cartId, long productId, int quantity) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("productId", productId);
        body.put("quantity", quantity);

        Map<String, Object> response = restClient.post()
                .uri("/api/v1/carts/{cartId}/items", cartId)
                .body(body)
                .retrieve()
                .body(Map.class);

        return ((Number) response.get("id")).longValue();
    }

    void updateCartItem(long cartId, long itemId, long productId, int quantity) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("productId", productId); // required by CartItemRequest validation; keep the same product
        body.put("quantity", quantity);

        restClient.put()
                .uri("/api/v1/carts/{cartId}/items/{itemId}", cartId, itemId)
                .body(body)
                .retrieve()
                .toBodilessEntity();
    }

    void removeCartItem(long cartId, long itemId) {
        restClient.delete()
                .uri("/api/v1/carts/{cartId}/items/{itemId}", cartId, itemId)
                .retrieve()
                .toBodilessEntity();
    }

    // ------------------------------------------------------------------
    // Orders
    // ------------------------------------------------------------------

    record CreatedOrder(long orderId, BigDecimal totalAmount, String status) {}

    CreatedOrder createOrder(long userId, List<Map<String, Object>> items) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("userId", userId);
        body.put("items", items);

        Map<String, Object> response = restClient.post()
                .uri("/api/v1/orders")
                .body(body)
                .retrieve()
                .body(Map.class);

        long orderId = ((Number) response.get("id")).longValue();
        BigDecimal total = new BigDecimal(response.get("totalAmount").toString());
        String status = (String) response.get("status");
        return new CreatedOrder(orderId, total, status);
    }

    void changeOrderStatus(long orderId, String newStatus) {
        restClient.patch()
                .uri(uriBuilder -> uriBuilder
                        .path("/api/v1/orders/{id}/status")
                        .queryParam("status", newStatus)
                        .build(orderId))
                .retrieve()
                .toBodilessEntity();
    }

    // ------------------------------------------------------------------
    // Payments
    // ------------------------------------------------------------------

    void createPayment(long orderId, BigDecimal amount, String status, String paymentMethod) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("orderId", orderId);
        body.put("paymentMethod", paymentMethod);
        body.put("status", status);
        // PaymentService requires this to exactly equal the order's total amount
        // (Double.compare), so callers must pass the order's own totalAmount back.
        body.put("amount", amount.doubleValue());

        restClient.post()
                .uri("/api/v1/payments")
                .body(body)
                .retrieve()
                .toBodilessEntity();
    }

    // ------------------------------------------------------------------
    // Reviews
    // ------------------------------------------------------------------

    long createReview(long userId, long productId, int rating, String comment) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("userId", userId);
        body.put("productId", productId);
        body.put("rating", rating);
        body.put("comment", comment);

        Map<String, Object> response = restClient.post()
                .uri("/api/v1/reviews")
                .body(body)
                .retrieve()
                .body(Map.class);

        return ((Number) response.get("id")).longValue();
    }

    void updateReview(long reviewId, long userId, long productId, int rating, String comment) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("userId", userId);
        body.put("productId", productId);
        body.put("rating", rating);
        body.put("comment", comment);

        restClient.put()
                .uri("/api/v1/reviews/{id}", reviewId)
                .body(body)
                .retrieve()
                .toBodilessEntity();
    }
}
