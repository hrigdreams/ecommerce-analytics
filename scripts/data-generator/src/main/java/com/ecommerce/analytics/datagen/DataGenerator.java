package com.ecommerce.analytics.datagen;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.Random;

/**
 * Seeds the ecommerce-analytics database with historical baseline data:
 * users, categories, products, orders, order_items, payments — then backfills
 * the analytics read-model tables directly with SQL aggregates, so the
 * /api/v1/analytics/* endpoints have something to show immediately.
 *
 * This bypasses Kafka on purpose. Replaying hundreds of thousands of events
 * one at a time through a local single-broker Kafka is slow and is not what
 * historical backfill is for — Kafka is for the live/incremental stream of
 * events your running app produces afterward. Run this once to get a
 * populated baseline, then use the app normally (through its REST API) and
 * new activity will flow through Kafka -> consumer -> analytics as usual.
 *
 * Usage (from scripts/data-generator):
 *   mvn -q clean package
 *   java -jar target/data-generator.jar [small|large|users=N,products=N,orders=N] [--reset]
 *
 * Connection settings come from env vars (with the same defaults as the main
 * app's application.properties), or override with -Ddb.url=... etc.
 */
public class DataGenerator {

    private static final int BATCH_SIZE = 1000;
    private static final long SEED = 42L; // fixed seed => reproducible runs

    private final Connection conn;
    private final Random random = new Random(SEED);
    private final Scale scale;

    public static void main(String[] args) throws Exception {
        String url = envOr("DB_URL", "jdbc:postgresql://localhost:5432/ecommerce_analytics");
        String user = envOr("DB_USERNAME", "postgres");
        String password = envOr("DB_PASSWORD", "admin");

        boolean reset = false;
        String scaleArg = null;
        for (String a : args) {
            if (a.equals("--reset")) {
                reset = true;
            } else if (!a.startsWith("--")) {
                scaleArg = a;
            }
        }
        Scale scale = Scale.parse(scaleArg);

        System.out.printf(
                "Connecting to %s as %s ... scale=%s (users=%d, products=%d, orders=%d)%n",
                url, user, scaleArg == null ? "large" : scaleArg, scale.users(), scale.products(), scale.orders()
        );

        try (Connection conn = DriverManager.getConnection(url, user, password)) {
            conn.setAutoCommit(false);
            new DataGenerator(conn, scale).run(reset);
        }
    }

    private static String envOr(String key, String fallback) {
        String v = System.getenv(key);
        return (v == null || v.isBlank()) ? fallback : v;
    }

    DataGenerator(Connection conn, Scale scale) {
        this.conn = conn;
        this.scale = scale;
    }

    void run(boolean reset) throws Exception {
        long start = System.currentTimeMillis();

        if (reset) {
            resetTables();
        }

        long[] categoryIds = seedCategories();
        System.out.println("Categories: " + categoryIds.length);

        long[] userIds = seedUsers(scale.users());
        System.out.println("Users: " + userIds.length);

        ProductSeedResult products = seedProducts(scale.products(), categoryIds);
        System.out.println("Products: " + products.ids.length);

        OrderSeedResult orderResult = seedOrdersItemsPayments(scale.orders(), userIds, products);
        System.out.println("Orders: " + orderResult.orderCount
                + " | Order items: " + orderResult.itemCount
                + " | Payments: " + orderResult.paymentCount);

        System.out.println("Backfilling analytics tables from transactional data...");
        backfillAnalytics();

        long seconds = (System.currentTimeMillis() - start) / 1000;
        System.out.println("Done in " + seconds + "s.");
    }

    // ------------------------------------------------------------------
    // Reset
    // ------------------------------------------------------------------

    private void resetTables() throws Exception {
        System.out.println("--reset: truncating all seeded + analytics tables...");
        try (Statement st = conn.createStatement()) {
            st.execute("""
                    TRUNCATE TABLE
                        payments, order_items, orders, cart_items, carts, reviews,
                        products, categories, users,
                        product_analytics, order_analytics, order_item_analytics,
                        category_analytics, customer_analytics, time_analytics,
                        geo_analytics, funnel_analytics, cart_analytics, cart_item_analytics,
                        review_analytics, processed_events
                    RESTART IDENTITY CASCADE
                    """);
        }
        conn.commit();
    }

    // ------------------------------------------------------------------
    // Categories
    // ------------------------------------------------------------------

    private long[] seedCategories() throws Exception {
        String checkSql = "SELECT id FROM categories WHERE name = ?";
        String insertSql = "INSERT INTO categories (name) VALUES (?)";

        long[] ids = new long[RefData.CATEGORIES.length];

        try (PreparedStatement check = conn.prepareStatement(checkSql);
             PreparedStatement insert = conn.prepareStatement(insertSql, Statement.RETURN_GENERATED_KEYS)) {

            for (int i = 0; i < RefData.CATEGORIES.length; i++) {
                String name = (String) RefData.CATEGORIES[i][0];

                check.setString(1, name);
                try (ResultSet rs = check.executeQuery()) {
                    if (rs.next()) {
                        ids[i] = rs.getLong(1);
                        continue;
                    }
                }

                insert.setString(1, name);
                insert.executeUpdate();
                try (ResultSet keys = insert.getGeneratedKeys()) {
                    keys.next();
                    ids[i] = keys.getLong(1);
                }
            }
        }
        conn.commit();
        return ids;
    }

    // ------------------------------------------------------------------
    // Users
    // ------------------------------------------------------------------

    private long[] seedUsers(int count) throws Exception {
        long[] ids = new long[count];
        String sql = "INSERT INTO users (name, email, age, gender, city, country, created_at) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?)";

        int cursor = 0;
        try (PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            for (int batchStart = 0; batchStart < count; batchStart += BATCH_SIZE) {
                int batchEnd = Math.min(batchStart + BATCH_SIZE, count);

                for (int i = batchStart; i < batchEnd; i++) {
                    String first = pick(RefData.FIRST_NAMES);
                    String last = pick(RefData.LAST_NAMES);
                    String name = first + " " + last;
                    String email = (first + "." + last + "." + i + "@example.com").toLowerCase();
                    int age = 18 + random.nextInt(53); // 18-70
                    String gender = weightedPick(RefData.GENDERS, RefData.GENDER_WEIGHTS);
                    Object[] geo = weightedGeo();
                    // Registration spread over the last ~400 days.
                    LocalDateTime createdAt = LocalDateTime.now().minusDays(random.nextInt(400));

                    ps.setString(1, name);
                    ps.setString(2, email);
                    ps.setInt(3, age);
                    ps.setString(4, gender);
                    ps.setString(5, (String) geo[0]);
                    ps.setString(6, (String) geo[1]);
                    ps.setTimestamp(7, Timestamp.valueOf(createdAt));
                    ps.addBatch();
                }

                ps.executeBatch();
                try (ResultSet keys = ps.getGeneratedKeys()) {
                    while (keys.next()) {
                        ids[cursor++] = keys.getLong(1);
                    }
                }
                conn.commit();
                progress("users", batchEnd, count);
            }
        }
        return ids;
    }

    // ------------------------------------------------------------------
    // Products
    // ------------------------------------------------------------------

    static final class ProductSeedResult {
        long[] ids;
        double[] prices;
        long[] categoryIds;
    }

    private ProductSeedResult seedProducts(int count, long[] categoryIds) throws Exception {
        ProductSeedResult result = new ProductSeedResult();
        result.ids = new long[count];
        result.prices = new double[count];
        result.categoryIds = new long[count];

        String sql = "INSERT INTO products (name, price, category_id) VALUES (?, ?, ?)";

        int cursor = 0;
        try (PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            for (int batchStart = 0; batchStart < count; batchStart += BATCH_SIZE) {
                int batchEnd = Math.min(batchStart + BATCH_SIZE, count);

                for (int i = batchStart; i < batchEnd; i++) {
                    int categoryIndex = random.nextInt(RefData.CATEGORIES.length);
                    Object[] cat = RefData.CATEGORIES[categoryIndex];
                    long categoryId = categoryIds[categoryIndex];

                    String name = pick(RefData.PRODUCT_ADJECTIVES) + " " + pick(RefData.PRODUCT_NOUNS) + " " + (i + 1);
                    double min = (double) cat[1];
                    double max = (double) cat[2];
                    double price = Math.round((min + random.nextDouble() * (max - min)) * 100.0) / 100.0;

                    ps.setString(1, name);
                    ps.setDouble(2, price);
                    ps.setLong(3, categoryId);
                    ps.addBatch();

                    result.prices[i] = price;
                    result.categoryIds[i] = categoryId;
                }

                ps.executeBatch();
                try (ResultSet keys = ps.getGeneratedKeys()) {
                    while (keys.next()) {
                        result.ids[cursor++] = keys.getLong(1);
                    }
                }
                conn.commit();
                progress("products", batchEnd, count);
            }
        }
        return result;
    }

    // ------------------------------------------------------------------
    // Orders + order_items + payments
    // ------------------------------------------------------------------

    static final class OrderSeedResult {
        long orderCount;
        long itemCount;
        long paymentCount;
    }

    private static final String[] ORDER_STATUSES =
            {"DELIVERED", "SHIPPED", "PROCESSING", "PENDING", "CANCELLED"};
    private static final int[] ORDER_STATUS_WEIGHTS =
            {55, 15, 10, 12, 8};

    private OrderSeedResult seedOrdersItemsPayments(int count, long[] userIds, ProductSeedResult products) throws Exception {
        OrderSeedResult result = new OrderSeedResult();

        // Popular-products-get-most-of-the-sales distribution. Product indices
        // are shuffled first so "popularity" isn't just "was inserted first".
        Integer[] productRank = shuffledIndices(products.ids.length);
        Weighted popularity = new Weighted(products.ids.length, 1.05);

        String orderSql = "INSERT INTO orders (user_id, total_amount, status, payment_status, created_at) "
                + "VALUES (?, ?, ?, ?, ?)";
        String itemSql = "INSERT INTO order_items (order_id, product_id, quantity, unit_price) "
                + "VALUES (?, ?, ?, ?)";
        String paymentSql = "INSERT INTO payments (order_id, payment_method, status, amount, transaction_id, created_at) "
                + "VALUES (?, ?, ?, ?, ?, ?)";

        String updatePaymentStatusSql = "UPDATE orders SET payment_status = ? WHERE id = ?";

        try (PreparedStatement orderPs = conn.prepareStatement(orderSql, Statement.RETURN_GENERATED_KEYS);
             PreparedStatement itemPs = conn.prepareStatement(itemSql);
             PreparedStatement paymentPs = conn.prepareStatement(paymentSql);
             PreparedStatement paymentStatusPs = conn.prepareStatement(updatePaymentStatusSql)) {

            for (int batchStart = 0; batchStart < count; batchStart += BATCH_SIZE) {
                int batchEnd = Math.min(batchStart + BATCH_SIZE, count);
                int batchSize = batchEnd - batchStart;

                // Precompute each order's items/total/status/timestamp before
                // inserting the order row, since total_amount must be known upfront.
                int[][] itemProductIdx = new int[batchSize][];
                int[][] itemQty = new int[batchSize][];
                double[] orderTotal = new double[batchSize];
                String[] orderStatus = new String[batchSize];
                LocalDateTime[] orderCreatedAt = new LocalDateTime[batchSize];

                for (int i = 0; i < batchSize; i++) {
                    int itemCount = 1 + skewedSmallInt(4); // 1-5, skewed toward 2-3
                    itemProductIdx[i] = new int[itemCount];
                    itemQty[i] = new int[itemCount];

                    double total = 0;
                    for (int j = 0; j < itemCount; j++) {
                        int productIndex = productRank[popularity.sample(random)];
                        int qty = 1 + skewedSmallInt(3); // 1-4, skewed toward 1-2
                        itemProductIdx[i][j] = productIndex;
                        itemQty[i][j] = qty;
                        total += round2(products.prices[productIndex] * qty);
                    }
                    orderTotal[i] = round2(total);
                    orderStatus[i] = weightedPick(ORDER_STATUSES, ORDER_STATUS_WEIGHTS);
                    orderCreatedAt[i] = randomOrderTimestamp();
                }

                // ---- insert orders ----
                for (int i = 0; i < batchSize; i++) {
                    long userId = userIds[random.nextInt(userIds.length)];
                    // "UNPAID" default matches OrderService.createOrder; updated below
                    // via updateOrderPaymentStatus() once we know if a payment was made.

                    orderPs.setLong(1, userId);
                    orderPs.setDouble(2, orderTotal[i]);
                    orderPs.setString(3, orderStatus[i]);
                    orderPs.setString(4, "UNPAID");
                    orderPs.setTimestamp(5, Timestamp.valueOf(orderCreatedAt[i]));
                    orderPs.addBatch();
                }
                orderPs.executeBatch();

                long[] orderIds = new long[batchSize];
                int idx = 0;
                try (ResultSet keys = orderPs.getGeneratedKeys()) {
                    while (keys.next()) {
                        orderIds[idx++] = keys.getLong(1);
                    }
                }

                // ---- insert order_items ----
                for (int i = 0; i < batchSize; i++) {
                    for (int j = 0; j < itemProductIdx[i].length; j++) {
                        int productIndex = itemProductIdx[i][j];
                        itemPs.setLong(1, orderIds[i]);
                        itemPs.setLong(2, products.ids[productIndex]);
                        itemPs.setInt(3, itemQty[i][j]);
                        itemPs.setDouble(4, products.prices[productIndex]);
                        itemPs.addBatch();
                        result.itemCount++;
                    }
                }
                itemPs.executeBatch();

                // ---- insert payments (skip for PENDING/CANCELLED orders) ----
                for (int i = 0; i < batchSize; i++) {
                    if (orderStatus[i].equals("PENDING") || orderStatus[i].equals("CANCELLED")) {
                        continue;
                    }
                    String paymentStatus = weightedPick(
                            new String[]{"PAID", "FAILED", "REFUNDED"}, new int[]{85, 8, 7});
                    String method = pick(RefData.PAYMENT_METHODS);
                    String txnId = "txn_" + orderIds[i] + "_" + random.nextInt(1_000_000);
                    LocalDateTime paidAt = orderCreatedAt[i].plusMinutes(2 + random.nextInt(30));

                    paymentPs.setLong(1, orderIds[i]);
                    paymentPs.setString(2, method);
                    paymentPs.setString(3, paymentStatus);
                    paymentPs.setDouble(4, orderTotal[i]);
                    paymentPs.setString(5, txnId);
                    paymentPs.setTimestamp(6, Timestamp.valueOf(paidAt));
                    paymentPs.addBatch();
                    result.paymentCount++;

                    // Keep orders.payment_status consistent with the payment we just wrote.
                    paymentStatusPs.setString(1, paymentStatus);
                    paymentStatusPs.setLong(2, orderIds[i]);
                    paymentStatusPs.addBatch();
                }
                paymentPs.executeBatch();
                paymentStatusPs.executeBatch();

                conn.commit();
                result.orderCount += batchSize;
                progress("orders", (int) result.orderCount, count);
            }
        }
        return result;
    }

    // ------------------------------------------------------------------
    // Analytics backfill (direct SQL, bypasses Kafka for the bulk historical load)
    // ------------------------------------------------------------------

    private void backfillAnalytics() throws Exception {
        try (Statement st = this.conn.createStatement()) {

            st.execute("""
            INSERT INTO product_analytics
                (product_id, product_name, category_id, price, active,
                 total_views, units_sold, revenue, review_count, rating_sum,
                 cart_add_count, cart_remove_count, created_at, updated_at)
            SELECT
                p.id, p.name, p.category_id, p.price, true,
                0,
                COALESCE(SUM(oi.quantity) FILTER (WHERE o.payment_status = 'PAID'), 0),
                COALESCE(SUM(oi.quantity * oi.unit_price) FILTER (WHERE o.payment_status = 'PAID'), 0),
                0, 0, 0, 0,
                now(), now()
            FROM products p
            LEFT JOIN order_items oi ON oi.product_id = p.id
            LEFT JOIN orders o ON o.id = oi.order_id
            GROUP BY p.id, p.name, p.category_id, p.price
            ON CONFLICT (product_id) DO UPDATE SET
                units_sold = EXCLUDED.units_sold,
                revenue = EXCLUDED.revenue,
                updated_at = now()
            """);

            System.out.println("  product_analytics backfilled");

            st.execute("""
            INSERT INTO order_analytics
                (order_id, user_id, status, payment_status, total_amount,
                 paid, paid_at, created_at, updated_at)
            SELECT
                o.id, o.user_id, o.status, o.payment_status, o.total_amount,
                (o.payment_status = 'PAID'),
                CASE WHEN o.payment_status = 'PAID' THEN o.created_at ELSE NULL END,
                o.created_at, now()
            FROM orders o
            ON CONFLICT (order_id) DO UPDATE SET
                status = EXCLUDED.status,
                payment_status = EXCLUDED.payment_status,
                paid = EXCLUDED.paid,
                updated_at = now()
            """);

            System.out.println("  order_analytics backfilled");

            st.execute("""
            DELETE FROM order_item_analytics
            WHERE order_id IN (SELECT id FROM orders)
            """);

            st.execute("""
            INSERT INTO order_item_analytics
                (order_id, product_id, product_name, quantity, unit_price, line_total)
            SELECT
                oi.order_id,
                oi.product_id,
                p.name,
                oi.quantity,
                oi.unit_price,
                oi.quantity * oi.unit_price
            FROM order_items oi
            JOIN products p ON p.id = oi.product_id
            """);

            System.out.println("  order_item_analytics backfilled");

            st.execute("""
            INSERT INTO category_analytics
                (category_id, category_name, product_count, units_sold,
                 revenue, review_count, rating_sum, updated_at)
            SELECT
                c.id,
                c.name,
                COUNT(DISTINCT p.id),
                COALESCE(SUM(oi.quantity) FILTER (WHERE o.payment_status = 'PAID'), 0),
                COALESCE(SUM(oi.quantity * oi.unit_price) FILTER (WHERE o.payment_status = 'PAID'), 0),
                0,
                0,
                now()
            FROM categories c
            LEFT JOIN products p ON p.category_id = c.id
            LEFT JOIN order_items oi ON oi.product_id = p.id
            LEFT JOIN orders o ON o.id = oi.order_id
            GROUP BY c.id, c.name
            ON CONFLICT (category_id) DO UPDATE SET
                product_count = EXCLUDED.product_count,
                units_sold = EXCLUDED.units_sold,
                revenue = EXCLUDED.revenue,
                updated_at = now()
            """);

            System.out.println("  category_analytics backfilled");

            st.execute("""
            INSERT INTO customer_analytics
                (user_id, total_orders, paid_orders, total_spend,
                 first_order_at, last_order_at, updated_at)
            SELECT
                o.user_id,
                COUNT(*),
                COUNT(*) FILTER (WHERE o.payment_status = 'PAID'),
                COALESCE(SUM(o.total_amount) FILTER (WHERE o.payment_status = 'PAID'), 0),
                MIN(o.created_at),
                MAX(o.created_at),
                now()
            FROM orders o
            GROUP BY o.user_id
            ON CONFLICT (user_id) DO UPDATE SET
                total_orders = EXCLUDED.total_orders,
                paid_orders = EXCLUDED.paid_orders,
                total_spend = EXCLUDED.total_spend,
                first_order_at = EXCLUDED.first_order_at,
                last_order_at = EXCLUDED.last_order_at,
                updated_at = now()
            """);

            System.out.println("  customer_analytics backfilled");

            st.execute("""
            DELETE FROM geo_analytics
            """);

            st.execute("""
            INSERT INTO geo_analytics
                (country, city, orders, paid_orders, revenue, updated_at)
            SELECT
                u.country,
                u.city,
                COUNT(*),
                COUNT(*) FILTER (WHERE o.payment_status = 'PAID'),
                COALESCE(SUM(o.total_amount) FILTER (WHERE o.payment_status = 'PAID'), 0),
                now()
            FROM orders o
            JOIN users u ON u.id = o.user_id
            GROUP BY u.country, u.city
            """);

            System.out.println("  geo_analytics backfilled");

            /*
             * Time analytics
             *
             * IMPORTANT:
             * The date_trunc unit is inserted directly into the SQL rather than
             * being represented by two different JDBC parameters.
             */
            for (String bucket : new String[]{"HOUR", "DAY", "WEEK", "MONTH", "YEAR"}) {

                String truncUnit;

                switch (bucket) {
                    case "HOUR" -> truncUnit = "hour";
                    case "DAY" -> truncUnit = "day";
                    case "WEEK" -> truncUnit = "week";
                    case "MONTH" -> truncUnit = "month";
                    default -> truncUnit = "year";
                }

                try (PreparedStatement del =
                             this.conn.prepareStatement(
                                     "DELETE FROM time_analytics WHERE bucket_type = ?")) {

                    del.setString(1, bucket);
                    del.executeUpdate();
                }

                String timeAnalyticsSql = """
                INSERT INTO time_analytics
                    (bucket_type, bucket_start, orders, paid_orders,
                     revenue, units_sold, updated_at)
                SELECT
                    ?,
                    date_trunc('%s', o.created_at),
                    COUNT(DISTINCT o.id),
                    COUNT(DISTINCT o.id) FILTER (WHERE o.payment_status = 'PAID'),
                    COALESCE(
                        SUM(o.total_amount) FILTER (WHERE o.payment_status = 'PAID'),
                        0
                    ),
                    COALESCE(
                        SUM(oi.quantity) FILTER (WHERE o.payment_status = 'PAID'),
                        0
                    ),
                    now()
                FROM orders o
                LEFT JOIN order_items oi ON oi.order_id = o.id
                GROUP BY date_trunc('%s', o.created_at)
                """.formatted(truncUnit, truncUnit);

                try (PreparedStatement ps =
                             this.conn.prepareStatement(timeAnalyticsSql)) {

                    ps.setString(1, bucket);
                    ps.executeUpdate();
                }

                System.out.println("  time_analytics[" + bucket + "] backfilled");
            }

            st.execute("""
            INSERT INTO funnel_analytics
                (bucket_date, product_views, cart_item_added, checkout_started,
                 orders_created, orders_paid, updated_at)
            SELECT
                date_trunc('day', o.created_at) AS bucket_date,
                COUNT(*) * 12,
                COUNT(*) * 4,
                COUNT(*) * 2,
                COUNT(*),
                COUNT(*) FILTER (WHERE o.payment_status = 'PAID'),
                now()
            FROM orders o
            GROUP BY date_trunc('day', o.created_at)
            ON CONFLICT (bucket_date) DO UPDATE SET
                product_views = EXCLUDED.product_views,
                cart_item_added = EXCLUDED.cart_item_added,
                checkout_started = EXCLUDED.checkout_started,
                orders_created = EXCLUDED.orders_created,
                orders_paid = EXCLUDED.orders_paid,
                updated_at = now()
            """);

            System.out.println("  funnel_analytics backfilled (synthetic upstream stages)");
        }

        this.conn.commit();
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private String pick(String[] values) {
        return values[random.nextInt(values.length)];
    }

    private String weightedPick(String[] values, int[] weights) {
        int total = 0;
        for (int w : weights) total += w;
        int target = random.nextInt(total);
        int running = 0;
        for (int i = 0; i < values.length; i++) {
            running += weights[i];
            if (target < running) return values[i];
        }
        return values[values.length - 1];
    }

    private Object[] weightedGeo() {
        int total = 0;
        for (Object[] g : RefData.GEO) total += (int) g[2];
        int target = random.nextInt(total);
        int running = 0;
        for (Object[] g : RefData.GEO) {
            running += (int) g[2];
            if (target < running) return g;
        }
        return RefData.GEO[RefData.GEO.length - 1];
    }

    /** Returns 0..maxExclusive-1, biased toward 0 (e.g. more orders with 1-2 items than 4-5). */
    private int skewedSmallInt(int maxExclusive) {
        double r = random.nextDouble();
        return (int) (maxExclusive * (r * r)); // square biases toward small values
    }

    /** Spread over the last 180 days, weighted toward more recent days and daytime hours. */
    private LocalDateTime randomOrderTimestamp() {
        double r = random.nextDouble();
        int daysAgo = (int) (180 * (r * r)); // recent days are denser
        int hour = 8 + skewedPeakHour(); // most orders land between ~10:00 and ~22:00
        int minute = random.nextInt(60);
        return LocalDateTime.now().minusDays(daysAgo).withHour(Math.min(hour, 23)).withMinute(minute).withSecond(0).withNano(0);
    }

    private int skewedPeakHour() {
        // triangular-ish distribution over 0..14 (added to base hour 8 => 8..22)
        return (int) ((random.nextDouble() + random.nextDouble()) / 2.0 * 14);
    }

    private Integer[] shuffledIndices(int n) {
        Integer[] arr = new Integer[n];
        for (int i = 0; i < n; i++) arr[i] = i;
        for (int i = n - 1; i > 0; i--) {
            int j = random.nextInt(i + 1);
            Integer tmp = arr[i];
            arr[i] = arr[j];
            arr[j] = tmp;
        }
        return arr;
    }

    private static double round2(double v) {
        return Math.round(v * 100.0) / 100.0;
    }

    private void progress(String label, int done, int total) {
        if (done == total || done % (BATCH_SIZE * 5) == 0) {
            System.out.printf("  %s: %d / %d%n", label, done, total);
        }
    }
}
