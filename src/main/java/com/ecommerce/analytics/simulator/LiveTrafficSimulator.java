package com.ecommerce.analytics.simulator;

import com.ecommerce.analytics.simulator.dto.SimulatorStartRequest;
import com.ecommerce.analytics.simulator.dto.SimulatorStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientResponseException;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Generates realistic live ecommerce traffic by calling application's own REST API — never PostgreSQL or Kafka directly:
 *  historicalCutoff defaults to the instant this simulator
 * */
@Component
@EnableConfigurationProperties(SimulatorProperties.class)
public class LiveTrafficSimulator {

    private static final Logger log = LoggerFactory.getLogger(LiveTrafficSimulator.class);

    private final SimulatorProperties properties;

    private final AtomicBoolean running = new AtomicBoolean(false);
    private final AtomicBoolean stopRequested = new AtomicBoolean(false);
    private final AtomicLong actionsCompleted = new AtomicLong();
    private final AtomicLong actionsFailed = new AtomicLong();
    private final Map<String, AtomicLong> actionCounts = new ConcurrentHashMap<>();
    private final AtomicReference<String> lastError = new AtomicReference<>();
    private final AtomicReference<Instant> startedAt = new AtomicReference<>();
    private final AtomicReference<Instant> historicalCutoff = new AtomicReference<>();
    private volatile int targetIterations;

    // In-memory session state: resources this run has created, so later
    // actions reference REAL generated IDs instead of guessing/hardcoding.
    private final List<Long> userPool = new CopyOnWriteArrayList<>();
    private final List<Long> productPool = new CopyOnWriteArrayList<>();
    private final Map<Long, Long> cartIdByUser = new ConcurrentHashMap<>();
    private final Map<Long, List<TrackedCartItem>> itemsByCart = new ConcurrentHashMap<>();
    private final List<TrackedOrder> orders = new CopyOnWriteArrayList<>();
    private final List<TrackedReview> reviews = new CopyOnWriteArrayList<>();

    private record TrackedCartItem(long itemId, long productId) {}

    private static final class TrackedOrder {
        final long orderId;
        final BigDecimal totalAmount;
        volatile String status;
        volatile boolean paid;

        TrackedOrder(long orderId, BigDecimal totalAmount, String status) {
            this.orderId = orderId;
            this.totalAmount = totalAmount;
            this.status = status;
        }
    }

    private record TrackedReview(long reviewId, long userId, long productId, int rating, String comment) {}

    public LiveTrafficSimulator(SimulatorProperties properties) {
        this.properties = properties;
    }
    /**
     * Auto-start hook: only fires when BOTH app.simulator.enabled=true AND
     * app.simulator.auto-start=true.  **/

    @org.springframework.context.event.EventListener(org.springframework.boot.context.event.ApplicationReadyEvent.class)
    public void onApplicationReady() {
        if (properties.isEnabled() && properties.isAutoStart()) {
            log.info("[sim] app.simulator.auto-start=true - starting automatically");
            try {
                start(null);
            } catch (Exception e) {
                log.error("[sim] auto-start failed: {}", e.getMessage());
            }
        }
    }


    // Lifecycle


    public synchronized void start(SimulatorStartRequest overrides) {
        if (!properties.isEnabled()) {
            throw new IllegalStateException(
                    "Simulator is disabled. Set app.simulator.enabled=true to allow starting it."
            );
        }
        if (running.get()) {
            throw new IllegalStateException("Simulator is already running.");
        }

        int iterations = (overrides != null && overrides.getIterations() != null)
                ? overrides.getIterations() : properties.getIterations();
        long intervalMs = (overrides != null && overrides.getIntervalMs() != null)
                ? overrides.getIntervalMs() : properties.getIntervalMs();
        long seed = (overrides != null && overrides.getSeed() != null)
                ? overrides.getSeed()
                : (properties.getSeed() != null ? properties.getSeed() : System.nanoTime());

        actionsCompleted.set(0);
        actionsFailed.set(0);
        actionCounts.clear();
        lastError.set(null);
        cartIdByUser.clear();
        itemsByCart.clear();
        orders.clear();
        reviews.clear();
        userPool.clear();
        productPool.clear();

        this.targetIterations = iterations;
        this.startedAt.set(Instant.now());
        this.historicalCutoff.set(
                properties.getHistoricalCutoff() != null ? properties.getHistoricalCutoff() : Instant.now()
        );
        this.stopRequested.set(false);
        this.running.set(true);

        log.info("[sim] starting: iterations={} intervalMs={} seed={} baseUrl={} historicalCutoff={}",
                iterations <= 0 ? "unbounded" : iterations, intervalMs, seed,
                properties.getBaseUrl(), historicalCutoff.get());

        Thread worker = new Thread(() -> runLoop(iterations, intervalMs, seed), "live-traffic-simulator");
        worker.setDaemon(true);
        worker.start();
    }

    public synchronized void stop() {
        if (!running.get()) {
            throw new IllegalStateException("Simulator is not running.");
        }
        stopRequested.set(true);
        log.info("[sim] stop requested");
    }

    public SimulatorStatus status() {
        Map<String, Long> counts = new java.util.TreeMap<>();
        actionCounts.forEach((k, v) -> counts.put(k, v.get()));

        return new SimulatorStatus(
                running.get(),
                properties.isEnabled(),
                actionsCompleted.get(),
                actionsFailed.get(),
                targetIterations,
                historicalCutoff.get(),
                startedAt.get(),
                counts,
                lastError.get()
        );
    }

    // ------------------------------------------------------------------
    // Main loop
    // ------------------------------------------------------------------

    private void runLoop(int iterations, long intervalMs, long seed) {
        Random random = new Random(seed);
        SimulatorClient client = new SimulatorClient(properties.getBaseUrl());

        try {
            userPool.addAll(client.fetchUserIds(properties.getUserPoolSize()));
            productPool.addAll(client.fetchProductIds(properties.getProductPoolSize()));
        } catch (Exception e) {
            log.error("[sim] failed to load user/product pools from {} - is the app reachable? {}",
                    properties.getBaseUrl(), e.getMessage());
            lastError.set("pool load failed: " + e.getMessage());
            running.set(false);
            return;
        }

        if (userPool.isEmpty() || productPool.isEmpty()) {
            log.warn("[sim] user pool ({}) or product pool ({}) is empty - has DataGenerator run yet? Stopping.",
                    userPool.size(), productPool.size());
            lastError.set("empty user/product pool - seed data first");
            running.set(false);
            return;
        }
        log.info("[sim] pools loaded: {} users, {} products", userPool.size(), productPool.size());

        int i = 0;
        while (!stopRequested.get() && (iterations <= 0 || i < iterations)) {
            SimAction action = pickAction(random);
            try {
                action.run(client, random);
                actionsCompleted.incrementAndGet();
            } catch (RestClientResponseException e) {
                actionsFailed.incrementAndGet();
                lastError.set(action.name() + ": HTTP " + e.getStatusCode());
                log.warn("[sim] {} failed: HTTP {} {}", action.name(), e.getStatusCode(), shorten(e.getResponseBodyAsString()));
            } catch (Exception e) {
                actionsFailed.incrementAndGet();
                lastError.set(action.name() + ": " + e.getMessage());
                log.warn("[sim] {} failed: {}", action.name(), e.getMessage());
            }
            actionCounts.computeIfAbsent(action.name(), k -> new AtomicLong()).incrementAndGet();

            i++;
            if (intervalMs > 0) {
                try {
                    Thread.sleep(intervalMs);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }

        running.set(false);
        log.info("[sim] stopped after {} actions ({} failed). Counts: {}",
                actionsCompleted.get(), actionsFailed.get(), actionCounts);
    }

    private static String shorten(String s) {
        if (s == null) return "";
        return s.length() > 160 ? s.substring(0, 160) + "..." : s;
    }

    // ------------------------------------------------------------------
    // Action selection
    // ------------------------------------------------------------------

    @FunctionalInterface
    private interface SimAction {
        void run(SimulatorClient client, Random random) throws Exception;

        default String name() {
            return this.getClass().getSimpleName();
        }
    }

    /** Weighted random pick of the next action. Weights favor browsing/cart activity over checkout. */
    private SimAction pickAction(Random random) {
        record Choice(String name, int weight, SimAction action) {}

        List<Choice> choices = List.of(
                new Choice("CREATE_CART", 12, this::actionCreateCart),
                new Choice("ADD_CART_ITEM", 22, this::actionAddCartItem),
                new Choice("UPDATE_CART_ITEM", 8, this::actionUpdateCartItem),
                new Choice("REMOVE_CART_ITEM", 6, this::actionRemoveCartItem),
                new Choice("CREATE_ORDER", 16, this::actionCreateOrder),
                new Choice("CREATE_PAYMENT", 13, this::actionCreatePayment),
                new Choice("CHANGE_ORDER_STATUS", 12, this::actionChangeOrderStatus),
                new Choice("CREATE_REVIEW", 7, this::actionCreateReview),
                new Choice("UPDATE_REVIEW", 4, this::actionUpdateReview)
        );

        int total = choices.stream().mapToInt(Choice::weight).sum();
        int target = random.nextInt(total);
        int cumulative = 0;
        for (Choice c : choices) {
            cumulative += c.weight();
            if (target < cumulative) {
                SimAction inner = c.action();
                return new SimAction() {
                    public void run(SimulatorClient client, Random rnd) throws Exception { inner.run(client, rnd); }
                    public String name() { return c.name(); }
                };
            }
        }
        Choice fallback = choices.get(0);
        return new SimAction() {
            public void run(SimulatorClient client, Random rnd) throws Exception { fallback.action().run(client, rnd); }
            public String name() { return fallback.name(); }
        };
    }

    private long randomUser(Random random) {
        return userPool.get(random.nextInt(userPool.size()));
    }

    private long randomProduct(Random random) {
        return productPool.get(random.nextInt(productPool.size()));
    }

    // ------------------------------------------------------------------
    // Actions
    // ------------------------------------------------------------------

    private void actionCreateCart(SimulatorClient client, Random random) {
        long userId = randomUser(random);
        long cartId = client.createOrGetCart(userId);
        cartIdByUser.put(userId, cartId);
        itemsByCart.putIfAbsent(cartId, new CopyOnWriteArrayList<>());
        log.info("[sim] CREATE_CART POST /api/v1/carts -> cartId={} userId={}", cartId, userId);
    }

    private void actionAddCartItem(SimulatorClient client, Random random) {
        long userId = randomUser(random);
        long cartId = cartIdByUser.computeIfAbsent(userId, client::createOrGetCart);
        long productId = randomProduct(random);
        int quantity = 1 + random.nextInt(3);

        long itemId = client.addCartItem(cartId, productId, quantity);
        itemsByCart.computeIfAbsent(cartId, k -> new CopyOnWriteArrayList<>())
                .add(new TrackedCartItem(itemId, productId));

        log.info("[sim] ADD_CART_ITEM POST /api/v1/carts/{}/items -> itemId={} productId={} qty={}",
                cartId, itemId, productId, quantity);
    }

    private void actionUpdateCartItem(SimulatorClient client, Random random) {
        var candidate = anyCartWithItems();
        if (candidate == null) {
            actionAddCartItem(client, random); // nothing to update yet - seed one instead
            return;
        }
        long cartId = candidate.getKey();
        TrackedCartItem item = candidate.getValue();
        int quantity = 1 + random.nextInt(4);

        client.updateCartItem(cartId, item.itemId(), item.productId(), quantity);
        log.info("[sim] UPDATE_CART_ITEM PUT /api/v1/carts/{}/items/{} -> qty={}", cartId, item.itemId(), quantity);
    }

    private void actionRemoveCartItem(SimulatorClient client, Random random) {
        var candidate = anyCartWithItems();
        if (candidate == null) {
            actionAddCartItem(client, random); // nothing to remove yet - seed one instead
            return;
        }
        long cartId = candidate.getKey();
        TrackedCartItem item = candidate.getValue();

        client.removeCartItem(cartId, item.itemId());
        itemsByCart.getOrDefault(cartId, List.of()).remove(item);
        log.info("[sim] REMOVE_CART_ITEM DELETE /api/v1/carts/{}/items/{}", cartId, item.itemId());
    }

    private Map.Entry<Long, TrackedCartItem> anyCartWithItems() {
        for (Map.Entry<Long, List<TrackedCartItem>> e : itemsByCart.entrySet()) {
            if (!e.getValue().isEmpty()) {
                TrackedCartItem item = e.getValue().get(0);
                return Map.entry(e.getKey(), item);
            }
        }
        return null;
    }

    private void actionCreateOrder(SimulatorClient client, Random random) {
        long userId = randomUser(random);
        int lineCount = 1 + random.nextInt(3);
        List<Map<String, Object>> items = new ArrayList<>();
        for (int i = 0; i < lineCount; i++) {
            items.add(Map.of(
                    "productId", randomProduct(random),
                    "quantity", 1 + random.nextInt(3)
            ));
        }

        SimulatorClient.CreatedOrder created = client.createOrder(userId, items);
        orders.add(new TrackedOrder(created.orderId(), created.totalAmount(), created.status()));

        log.info("[sim] CREATE_ORDER POST /api/v1/orders -> orderId={} userId={} total={} status={}",
                created.orderId(), userId, created.totalAmount(), created.status());
    }

    private void actionCreatePayment(SimulatorClient client, Random random) {
        TrackedOrder order = orders.stream().filter(o -> !o.paid).findAny().orElse(null);
        if (order == null) {
            actionCreateOrder(client, random); // nothing to pay for yet - seed one instead
            return;
        }

        String status = random.nextInt(100) < 88 ? "PAID" : (random.nextBoolean() ? "FAILED" : "REFUNDED");
        String method = List.of("CARD", "PAYPAL", "UPI", "WALLET", "BANK_TRANSFER").get(random.nextInt(5));

        client.createPayment(order.orderId, order.totalAmount, status, method);
        order.paid = true;

        log.info("[sim] CREATE_PAYMENT POST /api/v1/payments -> orderId={} amount={} status={}",
                order.orderId, order.totalAmount, status);
    }

    private static final List<String> STATUS_PROGRESSION =
            List.of("PENDING", "CONFIRMED", "PROCESSING", "SHIPPED", "DELIVERED");

    private void actionChangeOrderStatus(SimulatorClient client, Random random) {
        TrackedOrder order = orders.stream()
                .filter(o -> !o.status.equals("DELIVERED") && !o.status.equals("CANCELLED"))
                .findAny().orElse(null);
        if (order == null) {
            actionCreateOrder(client, random); // nothing eligible yet - seed one instead
            return;
        }

        String next;
        int currentIdx = STATUS_PROGRESSION.indexOf(order.status);
        boolean cancel = currentIdx >= 0 && currentIdx < 2 && random.nextInt(100) < 8; // small chance to cancel early
        if (cancel) {
            next = "CANCELLED";
        } else if (currentIdx >= 0 && currentIdx < STATUS_PROGRESSION.size() - 1) {
            next = STATUS_PROGRESSION.get(currentIdx + 1);
        } else {
            next = "CONFIRMED"; // unknown current status - move it forward from the start
        }

        client.changeOrderStatus(order.orderId, next);
        order.status = next;

        log.info("[sim] CHANGE_ORDER_STATUS PATCH /api/v1/orders/{}/status?status={} -> orderId={}",
                order.orderId, next, order.orderId);
    }

    private static final List<String> REVIEW_COMMENTS = List.of(
            "Works exactly as described.", "Good value for the price.", "Arrived quickly, well packaged.",
            "Decent, but I expected a bit more.", "Would buy again.", "Not what I expected, a bit disappointed.",
            "Excellent quality, highly recommend.", "Average product, does the job."
    );

    private void actionCreateReview(SimulatorClient client, Random random) {
        long userId = randomUser(random);
        long productId = randomProduct(random);
        int rating = 1 + random.nextInt(5);
        String comment = REVIEW_COMMENTS.get(random.nextInt(REVIEW_COMMENTS.size()));

        long reviewId = client.createReview(userId, productId, rating, comment);
        reviews.add(new TrackedReview(reviewId, userId, productId, rating, comment));

        log.info("[sim] CREATE_REVIEW POST /api/v1/reviews -> reviewId={} productId={} rating={}",
                reviewId, productId, rating);
    }

    private void actionUpdateReview(SimulatorClient client, Random random) {
        if (reviews.isEmpty()) {
            actionCreateReview(client, random); // nothing to update yet - seed one instead
            return;
        }
        TrackedReview review = reviews.get(random.nextInt(reviews.size()));
        int newRating = 1 + random.nextInt(5);
        String newComment = REVIEW_COMMENTS.get(random.nextInt(REVIEW_COMMENTS.size()));

        client.updateReview(review.reviewId(), review.userId(), review.productId(), newRating, newComment);

        log.info("[sim] UPDATE_REVIEW PUT /api/v1/reviews/{} -> rating={}", review.reviewId(), newRating);
    }
}
