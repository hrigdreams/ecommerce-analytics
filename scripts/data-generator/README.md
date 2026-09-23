# data-generator

Seeds the ecommerce-analytics database with realistic historical data, then
backfills the analytics read-model tables directly with SQL so the
`/api/v1/analytics/*` endpoints have something to show right away.

## Why this bypasses Kafka

This tool inserts straight into Postgres with plain JDBC, and the analytics
backfill step is also plain SQL run against the transactional tables. It does
**not** publish PRODUCT_CREATED / ORDER_CREATED / etc. events to Kafka.

That's on purpose. Replaying 100,000+ events one at a time through a local,
single-broker Kafka setup is slow and isn't what a historical backfill is
for. Kafka is for the live, incremental stream your running app produces as
real requests come in — after you seed a baseline with this tool, keep using
the app normally through its REST API, and new activity will flow through
Kafka -> consumer -> analytics exactly as before.

## Usage

```bash
cd scripts/data-generator
mvn -q clean package
```

Small scale — the "prove the pipeline works" size (10 users / 20 products /
50 orders / ~100 order items):

```bash
java -jar target/data-generator.jar small
```

Large scale — the historical baseline (10,000 users / 5,000 products /
100,000 orders / ~300,000 order items / ~90,000 payments):

```bash
java -jar target/data-generator.jar large
```

Custom scale:

```bash
java -jar target/data-generator.jar users=1000,products=500,orders=5000
```

Wipe everything this tool touches and start fresh (also clears the
processed_events dedupe table, so don't run this against a database with
real event-driven data you want to keep):

```bash
java -jar target/data-generator.jar large --reset
```

Re-running **without** `--reset` adds more users/products/orders on top of
what's there; the analytics backfill step re-derives its tables from
scratch each time either way, so analytics numbers stay correct.

## Connection settings

Reads the same environment variables as the main app (`DB_URL`,
`DB_USERNAME`, `DB_PASSWORD`), defaulting to
`jdbc:postgresql://localhost:5432/ecommerce_analytics` / `postgres` / `admin`
if unset — matching `application.properties`.

```bash
DB_URL=jdbc:postgresql://localhost:5432/ecommerce_analytics \
DB_USERNAME=postgres \
DB_PASSWORD=admin \
java -jar target/data-generator.jar large
```

## What it generates

- **Categories**: 15 fixed categories (Electronics, Fashion, Groceries, ...).
- **Users**: name, email, age, gender, city/country from a weighted list of
  ~12 hubs (so geo analytics shows real hot spots, not a flat spread),
  registration date spread over the last 400 days.
- **Products**: name, price (ranged per category), spread across all 15
  categories.
- **Orders**: created_at spread over the last 180 days, weighted toward more
  recent days and toward 10:00-22:00 (peak shopping hours). Status mix:
  55% DELIVERED, 15% SHIPPED, 10% PROCESSING, 12% PENDING, 8% CANCELLED.
- **Order items**: 1-5 per order (skewed toward 2-3), products chosen from a
  Pareto/Zipf distribution — a small set of products account for most of the
  order volume, like a real catalog.
- **Payments**: one per non-PENDING/non-CANCELLED order; 85% PAID, 8% FAILED,
  7% REFUNDED. `orders.payment_status` is kept consistent with each payment.

## What it does NOT generate (yet)

Carts and reviews aren't seeded by this tool — only the core
users/products/orders/order_items/payments flow the task specifically asked
for. `funnel_analytics` gets **synthetic, derived** upstream numbers (views /
cart-adds / checkout-starts estimated as multiples of each day's order
count) since PRODUCT_VIEWED/CART_ITEM_ADDED/CHECKOUT_STARTED events never
actually happened for this historical data — real funnel numbers only
appear once live traffic runs through the app and Kafka.

## Performance

Batches of 1,000 rows per round trip, single transaction per batch. On a
laptop against local Postgres, the "large" preset (100K orders, ~300K order
items, ~90K payments) typically finishes in a few minutes. If it's slow,
check that autocommit is off (it is, by default in this tool) and that
Postgres isn't also under load from the app doing something else.
