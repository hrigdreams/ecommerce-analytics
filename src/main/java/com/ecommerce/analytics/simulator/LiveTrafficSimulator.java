package com.ecommerce.analytics.simulator;

import com.ecommerce.analytics.simulator.dto.RecentSimEvent;
import com.ecommerce.analytics.simulator.dto.SimulatorStartRequest;
import com.ecommerce.analytics.simulator.dto.SimulatorStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientResponseException;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Generates realistic live ecommerce traffic by calling application's own REST API — never PostgreSQL or Kafka directly:
 *  historicalCutoff defaults to the instant this simulator
 *
 * PHASE 1, item 3 (continuous background pulse): this class now supports
 * TWO independent ways to run, sharing the same action pool/state
 * (userPool, productPool, cartIdByUser, orders, reviews, action counters):
 * <ol>
 *   <li><b>Burst/unbounded mode</b> (original, unchanged): {@link #start(SimulatorStartRequest)}
 *       / {@link #stop()}, a dedicated daemon Thread running {@link #runLoop}
 *       for a fixed {@code iterations} count (or unbounded if <= 0), firing
 *       one action every {@code intervalMs}. Kept exactly as it was so the
 *       existing "Start simulator" button/tests/iterations-burst behavior
 *       keeps working unchanged.</li>
 *   <li><b>Pulse mode</b> (new): {@link #pulseStart()} / {@link #pulseStop()}
 *       turn on/off a genuine {@code @Scheduled} tick ({@link #pulseTick()}),
 *       which fires every {@code app.simulator.pulse.interval-ms} and runs a
 *       random number of actions (between {@code min-events-per-tick} and
 *       {@code max-events-per-tick}) each time it fires — a slow, steady
 *       trickle of activity instead of one big burst. This is additive: nothing
 *       about burst mode was removed, per the project's established pattern of
 *       flagging a literal "replace" in the request as a reasoned, documented
 *       deviation when replacing would break existing behavior/tests.</li>
 * </ol>
 */
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

    // PHASE 1, item 3: pulse-mode state. Deliberately separate from `running`/
    // `stopRequested` above (those remain burst-mode-only) so the two modes
    // can be queried and toggled independently via SimulatorStatus.
    private final AtomicBoolean pulseRunning = new AtomicBoolean(false);
    private final Random pulseRandom = new Random();
    private volatile SimulatorClient pulseClient;

    // In-memory session state: resources this run has created, so later
    // actions reference REAL generated IDs instead of guessing/hardcoding.
    // Shared between burst mode and pulse mode on purpose -- both modes call
    // the same actionXxx() methods below, and sharing this state means a cart
    // pulse mode created can be added to/checked out by burst mode and vice
    // versa, instead of maintaining two disconnected, half-populated pools.
    private final List<Long> userPool = new CopyOnWriteArrayList<>();
    private final List<Long> productPool = new CopyOnWriteArrayList<>();
    private final Map<Long, Long> cartIdByUser = new ConcurrentHashMap<>();
    private final Map<Long, List<TrackedCartItem>> itemsByCart = new ConcurrentHashMap<>();
    private final List<TrackedOrder> orders = new CopyOnWriteArrayList<>();
    private final List<TrackedReview> reviews = new CopyOnWriteArrayList<>();

    /**
     * PHASE 3A, item 3 (operational visibility): bounded in-memory log of
     * the raw events this simulator has successfully generated, newest
     * first, for GET /api/v1/simulator/events/recent to build a live
     * streaming terminal log in the UI. Deliberately in-memory only (not
     * persisted, not published anywhere) - this is a debugging/visibility
     * aid scoped to "what has this running instance done recently", not an
     * analytics source; the real analytics pipeline is Kafka -> Spark/
     * Elasticsearch, unaffected by this. ConcurrentLinkedDeque because both
     * burst mode (its own daemon Thread) and pulse mode (a @Scheduled tick)
     * can record events concurrently with no external synchronization, and
     * a caller can safely iterate a snapshot (see recentEvents()) while a
     * writer is still adding to the head.
     */
    private static final int RECENT_EVENTS_CAPACITY = 100;
    private final Deque<RecentSimEvent> recentEvents = new ConcurrentLinkedDeque<>();

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


    // Lifecycle (burst/unbounded mode - unchanged from before Phase 1)


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
                lastError.get(),
                pulseRunning.get()
        );
    }

    /**
     * PHASE 3A, item 3: snapshot of the recent-events log, newest first,
     * most-recent-{@value #RECENT_EVENTS_CAPACITY} only. Returns an
     * immutable copy (not a live view over the deque) so the controller can
     * serialize it without racing a concurrent writer mid-iteration.
     */
    public List<RecentSimEvent> recentEvents() {
        return List.copyOf(recentEvents);
    }

    /**
     * Records one successfully-completed action into the bounded recent-
     * events log, evicting the oldest entry once at capacity. Called only
     * from {@link #executeAction} after a successful run - per this task's
     * own scope ("events it successfully generates"), a failed action is
     * NOT logged here (it is already captured by actionsFailed/lastError in
     * {@link #status()}).
     */
    private void recordRecentEvent(String eventType, String mode, String detail) {
        recentEvents.addFirst(new RecentSimEvent(eventType, Instant.now(), mode, detail));
        while (recentEvents.size() > RECENT_EVENTS_CAPACITY) {
            recentEvents.removeLast();
        }
    }

    // ------------------------------------------------------------------
    // Lifecycle (PHASE 1, item 3 - continuous pulse mode, additive)
    // ------------------------------------------------------------------

    /**
     * Turns on the continuous pulse: from this call onward, {@link #pulseTick()}
     * (already running on a schedule for every instance, gated by
     * {@link #pulseRunning}) starts actually executing actions. Loads the
     * user/product pools itself if they are still empty, so pulse mode works
     * standing alone without ever calling {@link #start} first.
     */
    public synchronized void pulseStart() {
        if (!properties.isEnabled()) {
            throw new IllegalStateException(
                    "Simulator is disabled. Set app.simulator.enabled=true to allow starting it."
            );
        }
        if (pulseRunning.get()) {
            throw new IllegalStateException("Simulator pulse is already running.");
        }

        if (pulseClient == null) {
            pulseClient = new SimulatorClient(properties.getBaseUrl());
        }
        ensurePoolsLoaded(pulseClient);

        if (startedAt.get() == null) {
            startedAt.set(Instant.now());
        }
        if (historicalCutoff.get() == null) {
            historicalCutoff.set(
                    properties.getHistoricalCutoff() != null ? properties.getHistoricalCutoff() : Instant.now()
            );
        }

        pulseRunning.set(true);
        log.info("[sim] pulse mode started: intervalMs={} eventsPerTick={}-{}",
                properties.getPulse().getIntervalMs(),
                properties.getPulse().getMinEventsPerTick(),
                properties.getPulse().getMaxEventsPerTick());
    }

    public synchronized void pulseStop() {
        if (!pulseRunning.get()) {
            throw new IllegalStateException("Simulator pulse is not running.");
        }
        pulseRunning.set(false);
        log.info("[sim] pulse mode stopped");
    }

    /**
     * Fires on a fixed delay from the moment the app starts (not just while
     * pulse mode is on) — {@code pulseRunning} is the actual on/off switch,
     * checked first so a tick is a no-op whenever pulse mode hasn't been
     * started via {@link #pulseStart()}. This is the standard
     * "always-scheduled, conditionally-active" pattern for a toggleable
     * {@code @Scheduled} task, and avoids registering/cancelling a
     * ScheduledFuture by hand.
     * <p>
     * Runs 2-5 (configurable via app.simulator.pulse.min/max-events-per-tick)
     * of the same weighted random actions burst mode uses ({@link #pickAction}),
     * so pulse-mode traffic looks like the same mix of browsing/cart/checkout
     * activity, just trickled in slowly instead of arriving all at once.
     */
    @Scheduled(fixedDelayString = "${app.simulator.pulse.interval-ms:12000}")
    public void pulseTick() {
        if (!pulseRunning.get()) {
            return;
        }
        if (pulseClient == null) {
            pulseClient = new SimulatorClient(properties.getBaseUrl());
        }
        if (userPool.isEmpty() || productPool.isEmpty()) {
            try {
                ensurePoolsLoaded(pulseClient);
            } catch (Exception e) {
                log.warn("[sim] pulse tick skipped - could not load user/product pools: {}", e.getMessage());
                lastError.set("pulse pool load failed: " + e.getMessage());
                return;
            }
            if (userPool.isEmpty() || productPool.isEmpty()) {
                log.warn("[sim] pulse tick skipped - user/product pool still empty, has DataGenerator run yet?");
                return;
            }
        }

        int min = Math.max(0, properties.getPulse().getMinEventsPerTick());
        int max = Math.max(min, properties.getPulse().getMaxEventsPerTick());
        int count = min + (max > min ? pulseRandom.nextInt(max - min + 1) : 0);

        for (int i = 0; i < count; i++) {
            SimAction action = pickAction(pulseRandom);
            executeAction(action, pulseClient, pulseRandom, "PULSE");
        }
        log.info("[sim] pulse tick: ran {} action(s)", count);
    }

    /** Loads userPool/productPool once if empty, shared by both start() paths. */
    private void ensurePoolsLoaded(SimulatorClient client) {
        if (!userPool.isEmpty() && !productPool.isEmpty()) {
            return;
        }
        List<Long> userIds = client.fetchUserIds(properties.getUserPoolSize());
        List<Long> productIds = client.fetchProductIds(properties.getProductPoolSize());
        if (userPool.isEmpty()) {
            userPool.addAll(userIds);
        }
        if (productPool.isEmpty()) {
            productPool.addAll(productIds);
        }
        log.info("[sim] pools loaded (pulse): {} users, {} products", userPool.size(), productPool.size());
    }

    // ------------------------------------------------------------------
    // Main loop (burst/unbounded mode)
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
            executeAction(action, client, random, "BURST");

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

    /**
     * Runs one action and records its outcome (success/failure counters,
     * lastError, per-action-name counts). Extracted from the body of
     * {@link #runLoop} (PHASE 1, item 3) so {@link #pulseTick()} can reuse the
     * exact same execute-and-record behavior instead of duplicating it.
     * <p>
     * PHASE 3A, item 3: on success, also appends to the bounded recent-
     * events log via {@link #recordRecentEvent} - {@code mode} ("BURST" or
     * "PULSE") identifies which caller this was, and the detail string
     * {@link SimAction#run} now returns is reused as both the event log's
     * "detail" field here and nothing else (no change to what gets logged
     * via SLF4J - each action method still calls log.info itself).
     */
    private void executeAction(SimAction action, SimulatorClient client, Random random, String mode) {
        try {
            String detail = action.run(client, random);
            actionsCompleted.incrementAndGet();
            recordRecentEvent(action.name(), mode, detail);
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
        /**
         * @return a short, human-readable detail string describing what the
         * action did (e.g. "orderId=482 userId=17 total=129.50") - PHASE 3A,
         * item 3: used as the "detail" field for the recent-events log. Every
         * action method below returns the same information it already logs
         * via SLF4J, just also handed back to the caller.
         */
        String run(SimulatorClient client, Random random) throws Exception;

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
                    public String run(SimulatorClient client, Random rnd) throws Exception { return inner.run(client, rnd); }
                    public String name() { return c.name(); }
                };
            }
        }
        Choice fallback = choices.get(0);
        return new SimAction() {
            public String run(SimulatorClient client, Random rnd) throws Exception { return fallback.action().run(client, rnd); }
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

    private String actionCreateCart(SimulatorClient client, Random random) {
        long userId = randomUser(random);
        long cartId = client.createOrGetCart(userId);
        cartIdByUser.put(userId, cartId);
        itemsByCart.putIfAbsent(cartId, new CopyOnWriteArrayList<>());
        log.info("[sim] CREATE_CART POST /api/v1/carts -> cartId={} userId={}", cartId, userId);
        return "cartId=" + cartId + " userId=" + userId;
    }

    private String actionAddCartItem(SimulatorClient client, Random random) {
        long userId = randomUser(random);
        long cartId = cartIdByUser.computeIfAbsent(userId, client::createOrGetCart);
        long productId = randomProduct(random);
        int quantity = 1 + random.nextInt(3);

        long itemId = client.addCartItem(cartId, productId, quantity);
        itemsByCart.computeIfAbsent(cartId, k -> new CopyOnWriteArrayList<>())
                .add(new TrackedCartItem(itemId, productId));

        log.info("[sim] ADD_CART_ITEM POST /api/v1/carts/{}/items -> itemId={} productId={} qty={}",
                cartId, itemId, productId, quantity);
        return "cartId=" + cartId + " itemId=" + itemId + " productId=" + productId + " qty=" + quantity;
    }

    private String actionUpdateCartItem(SimulatorClient client, Random random) {
        var candidate = anyCartWithItems();
        if (candidate == null) {
            return actionAddCartItem(client, random); // nothing to update yet - seed one instead
        }
        long cartId = candidate.getKey();
        TrackedCartItem item = candidate.getValue();
        int quantity = 1 + random.nextInt(4);

        client.updateCartItem(cartId, item.itemId(), item.productId(), quantity);
        log.info("[sim] UPDATE_CART_ITEM PUT /api/v1/carts/{}/items/{} -> qty={}", cartId, item.itemId(), quantity);
        return "cartId=" + cartId + " itemId=" + item.itemId() + " qty=" + quantity;
    }

    private String actionRemoveCartItem(SimulatorClient client, Random random) {
        var candidate = anyCartWithItems();
        if (candidate == null) {
            return actionAddCartItem(client, random); // nothing to remove yet - seed one instead
        }
        long cartId = candidate.getKey();
        TrackedCartItem item = candidate.getValue();

        client.removeCartItem(cartId, item.itemId());
        itemsByCart.getOrDefault(cartId, List.of()).remove(item);
        log.info("[sim] REMOVE_CART_ITEM DELETE /api/v1/carts/{}/items/{}", cartId, item.itemId());
        return "cartId=" + cartId + " itemId=" + item.itemId();
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

    private String actionCreateOrder(SimulatorClient client, Random random) {
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
        return "orderId=" + created.orderId() + " userId=" + userId + " total=" + created.totalAmount()
                + " status=" + created.status();
    }

    private String actionCreatePayment(SimulatorClient client, Random random) {
        TrackedOrder order = orders.stream().filter(o -> !o.paid).findAny().orElse(null);
        if (order == null) {
            return actionCreateOrder(client, random); // nothing to pay for yet - seed one instead
        }

        String status = random.nextInt(100) < 88 ? "PAID" : (random.nextBoolean() ? "FAILED" : "REFUNDED");
        String method = List.of("CARD", "PAYPAL", "UPI", "WALLET", "BANK_TRANSFER").get(random.nextInt(5));

        client.createPayment(order.orderId, order.totalAmount, status, method);
        order.paid = true;

        log.info("[sim] CREATE_PAYMENT POST /api/v1/payments -> orderId={} amount={} status={}",
                order.orderId, order.totalAmount, status);
        return "orderId=" + order.orderId + " amount=" + order.totalAmount + " method=" + method + " status=" + status;
    }

    private static final List<String> STATUS_PROGRESSION =
            List.of("PENDING", "CONFIRMED", "PROCESSING", "SHIPPED", "DELIVERED");

    private String actionChangeOrderStatus(SimulatorClient client, Random random) {
        TrackedOrder order = orders.stream()
                .filter(o -> !o.status.equals("DELIVERED") && !o.status.equals("CANCELLED"))
                .findAny().orElse(null);
        if (order == null) {
            return actionCreateOrder(client, random); // nothing eligible yet - seed one instead
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
        return "orderId=" + order.orderId + " status=" + next;
    }

    private static final List<String> REVIEW_COMMENTS = List.of(
            "Works exactly as described.", "Good value for the price.", "Arrived quickly, well packaged.",
            "Decent, but I expected a bit more.", "Would buy again.", "Not what I expected, a bit disappointed.",
            "Excellent quality, highly recommend.", "Average product, does the job."
    );

    private String actionCreateReview(SimulatorClient client, Random random) {
        long userId = randomUser(random);
        long productId = randomProduct(random);
        int rating = 1 + random.nextInt(5);
        String comment = REVIEW_COMMENTS.get(random.nextInt(REVIEW_COMMENTS.size()));

        long reviewId = client.createReview(userId, productId, rating, comment);
        reviews.add(new TrackedReview(reviewId, userId, productId, rating, comment));

        log.info("[sim] CREATE_REVIEW POST /api/v1/reviews -> reviewId={} productId={} rating={}",
                reviewId, productId, rating);
        return "reviewId=" + reviewId + " productId=" + productId + " userId=" + userId + " rating=" + rating;
    }

    private String actionUpdateReview(SimulatorClient client, Random random) {
        if (reviews.isEmpty()) {
            return actionCreateReview(client, random); // nothing to update yet - seed one instead
        }
        TrackedReview review = reviews.get(random.nextInt(reviews.size()));
        int newRating = 1 + random.nextInt(5);
        String newComment = REVIEW_COMMENTS.get(random.nextInt(REVIEW_COMMENTS.size()));

        client.updateReview(review.reviewId(), review.userId(), review.productId(), newRating, newComment);

        log.info("[sim] UPDATE_REVIEW PUT /api/v1/reviews/{} -> rating={}", review.reviewId(), newRating);
        return "reviewId=" + review.reviewId() + " productId=" + review.productId() + " rating=" + newRating;
    }
}
