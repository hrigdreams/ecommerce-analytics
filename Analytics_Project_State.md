# E-Commerce Analytics & Intelligence Platform
## Project State

> This file is the current engineering checkpoint for the repository.
---

## 1. Current Repository State

### Git branches

The repository currently uses:

- `main`
- `feature/kafka-event-architecture` — current Kafka/event-architecture branch


### Important Spark safety rule

A Spark module already exists locally under:

```text
spark-analytics/
└── spark-analytics/
```

This Spark work must be treated separately from the completed Kafka baseline.

```



---

# 2. Project Overview

The E-Commerce Analytics & Intelligence Platform is a backend-first distributed analytics system for an e-commerce domain.

The system is divided into:

- transactional REST processing
- PostgreSQL persistence
- domain-event publication
- Kafka event transport
- analytics read models
- historical/batch analytics
- live/streaming analytics
- future unified analytics serving
- future Elasticsearch/search serving

The completed Kafka architecture is the bridge between the transactional application and downstream analytics.

---

# 3. Current Architecture

## 3.1 Completed transactional + Kafka flow

```text
Client / REST API
       |
       v
Spring Boot Controller
       |
       v
Service Layer
       |
       +----------------------+
       |                      |
       v                      v
PostgreSQL              Domain Event
Transactional State          |
                              v
                     Kafka Producer
                              |
                              v
              ecommerce.domain.events.v1
                              |
                              v
                    Kafka Consumer
                              |
                              v
                  Domain Event Router
                              |
              +---------------+----------------+
              |               |                |
              v               v                v
       Product Analytics  Order Analytics  Cart Analytics
              |               |                |
              +---------------+----------------+
                              |
                              v
                     Analytics REST APIs
```

## 3.2 Historical + live analytics direction

Historical data and live event data are intentionally separated.

```text
Historical PostgreSQL Data
        |
        v
   Spark Batch
        |
        v
 spark_batch_* tables


Live REST Activity
        |
        v
 PostgreSQL
        |
        v
 Kafka
        |
        v
 Spark Structured Streaming
        |
        v
spark_streaming_* tables
```

The eventual serving architecture is:

```text
spark_batch_*
       +
spark_streaming_*
       |
       v
Unified Analytics
       |
       v
unified_* tables
       |
       v
Elasticsearch
       |
       v
Analytics APIs / Dashboard
```

The unified layer and Elasticsearch serving layer are future work and are not required for the Kafka baseline.

---

# 4. Technology Stack

## Current backend

- Java 21
- Spring Boot 4.1.1
- Maven
- PostgreSQL 15.4
- Spring Data JPA
- Hibernate ORM 7.4.5.Final
- REST APIs
- Jakarta Bean Validation
- JUnit
- Mockito
- SpringDoc / OpenAPI / Swagger
- Spring Boot Actuator
- Apache Kafka 3.8.0
- Docker
- Git

## Analytics technology already present locally

- Apache Spark
- Spark SQL
- Spark Structured Streaming

The Spark module is isolated from the Spring Boot dependency tree.

## Future serving / observability stages

- Elasticsearch
- Prometheus
- Grafana
- integration/load testing

---

# 5. Transactional System of Record

PostgreSQL is the transactional source of truth.

Verified database:

```text
Database: ecommerce_analytics
Schema:   public
PostgreSQL: 15.4
JDBC: jdbc:postgresql://localhost:5432/ecommerce_analytics
```

Core transactional entities:

```text
User
Category
Product
Cart
CartItem
Order
OrderItem
Payment
Review
```

---

# 6. Transactional REST Architecture

The application follows:

```text
HTTP Request
     |
     v
Controller
     |
     v
Service
     |
     v
Repository
     |
     v
PostgreSQL
```

DTOs separate external API contracts from persistence entities.

Completed transactional modules:

- User
- Category
- Product
- Cart
- CartItem
- Order
- OrderItem
- Payment
- Review

Implemented capabilities include:

- CRUD operations
- DTO request/response separation
- validation
- relationship validation
- global exception handling
- transactional deletion ordering
- Swagger/OpenAPI documentation
- Actuator health/operational visibility
- JUnit/Mockito testing

---

# 7. Database Relationship Model

```text
User
 | \
 |  \
 |   +----> Orders
 |            |
 |            +----> OrderItems ----> Product
 |
 +----> Cart ----> CartItems ----> Product
 |
 +----> Reviews ----> Product

Category ----> Products

Order ----> Payment
```

Important relationships:

```text
users.id           -> carts.user_id
users.id           -> orders.user_id
users.id           -> reviews.user_id
categories.id      -> products.category_id
carts.id           -> cart_items.cart_id
products.id        -> cart_items.product_id
orders.id          -> order_items.order_id
products.id        -> order_items.product_id
orders.id          -> payments.order_id
users.id           -> reviews.user_id
products.id        -> reviews.product_id
```

---

# 8. Global Exception Handling

A centralized `GlobalExceptionHandler` is used with `@RestControllerAdvice`.

Current mappings include:

```text
UserNotFoundException       -> 404
OrderNotFoundException      -> 404
ProductNotFoundException    -> 404
CategoryNotFoundException   -> 404
CartNotFoundException       -> 404
ReviewNotFoundException     -> 404
ProductInUseException       -> 409
Validation errors            -> 400
Unexpected errors            -> 500
```

Validation responses use a consistent structure containing the HTTP status, message, and field-level errors where applicable.

---

# 9. Important PostgreSQL Constraint Finding

During order deletion testing, PostgreSQL foreign-key constraints exposed the dependency order between:

```text
payments -> orders
order_items -> orders
```

The deletion sequence was therefore made explicit:

```text
Find Order
   |
   v
Delete Payment(s)
   |
   v
Delete OrderItem(s)
   |
   v
Delete Order
```

The operation is transactional.

This behavior is preserved in the Kafka architecture because domain events are published only after the transactional operation has completed successfully.

---

# 10. Testing Baseline

Testing has included:

- manual REST/curl verification
- JUnit
- Mockito
- application-context testing
- repository interaction verification
- validation testing
- relationship validation
- Kafka producer/consumer tests
- domain-event router tests
- analytics handler/service tests
- simulator tests

The previously established Spring application baseline reached a clean Maven test run with all tests passing.

The Kafka/event-architecture phase subsequently expanded the test surface significantly.

---

# 11. Swagger / OpenAPI

SpringDoc/OpenAPI is enabled.

Important endpoints:

```text
/v3/api-docs
/swagger-ui.html
```

Swagger provides visibility into:

- HTTP methods
- endpoint paths
- request DTOs
- response DTOs
- parameters
- validation
- HTTP status codes

---

# 12. Actuator

Spring Boot Actuator is enabled for operational visibility.

Operational endpoints are exposed below:

```text
/actuator/...
```

Business APIs remain under:

```text
/api/v1/...
```

---

# 13. Domain Event Architecture

The Kafka phase introduced a common event model.

The current event envelope is:

```text
EventEnvelope<T>
```

Fields:

```text
eventId
eventType
occurredAt
aggregateType
aggregateId
version
payload
```

Semantics:

- `eventId` is a UUID identifying the event.
- `eventType` identifies the business event.
- `occurredAt` records the event timestamp.
- `aggregateType` identifies the domain aggregate.
- `aggregateId` identifies the aggregate instance.
- `version` provides event-version information.
- `payload` contains event-specific data.

The event factory creates:

```text
UUID eventId
Instant.now()
version = 1
```

---

# 14. Event Type Catalog

The completed event catalog includes:

```text
USER_CREATED
USER_UPDATED
USER_DELETED

CATEGORY_CREATED
CATEGORY_UPDATED
CATEGORY_DELETED

PRODUCT_CREATED
PRODUCT_UPDATED
PRODUCT_DELETED

CART_CREATED
CART_UPDATED
CART_DELETED

CART_ITEM_ADDED
CART_ITEM_UPDATED
CART_ITEM_REMOVED

ORDER_CREATED
ORDER_STATUS_CHANGED
ORDER_DELETED

PAYMENT_CREATED
PAYMENT_DELETED

REVIEW_CREATED
REVIEW_UPDATED
REVIEW_DELETED

PRODUCT_VIEWED
CHECKOUT_STARTED
```

These event types support both transactional lifecycle events and analytics/funnel events.

---

# 15. Kafka Producer Architecture

The application publishes domain events through:

```text
DomainEventPublisher
```

The publisher uses:

```text
KafkaTemplate<String, Object>
```

Kafka event publication is integrated with the transactional service flow.

The important transaction rule is:

```text
Database transaction
       |
       v
Successful commit
       |
       v
Publish domain event
       |
       v
Kafka
```

When transaction synchronization is active, events are published after commit so a failed database transaction does not produce a successful-looking downstream event.

---

# 16. Kafka Topic

The completed domain-event topic is:

```text
ecommerce.domain.events.v1
```

Kafka producer records use the aggregate ID as the message key.

This gives the architecture a stable partitioning strategy and preserves ordering for events sharing the same aggregate key.

Verified topic characteristics from the established Kafka checkpoint:

```text
Partitions: 3
Replication factor: 1
```

---

# 17. Kafka Consumer Architecture

The analytics consumer uses:

```text
consumer group:
analytics-consumer
```

The consumer receives:

```text
EventEnvelope<JsonNode>
```

and passes the event to the domain-event router.

The consumer does not directly contain business logic for every event type.

The flow is:

```text
Kafka
 |
 v
DomainEventConsumer
 |
 v
EventEnvelope<JsonNode>
 |
 v
DefaultDomainEventRouter
 |
 v
Event-specific handler
```

---

# 18. Domain Event Router

The router is:

```text
DefaultDomainEventRouter
```

It builds an:

```text
EnumMap<EventType, DomainEventHandler>
```

Handlers are registered by their supported event type.

The router:

1. validates handlers
2. rejects duplicate event-type handlers
3. validates the event
4. reads `eventType`
5. finds the registered handler
6. delegates the payload

This keeps Kafka transport concerns separate from analytics business logic.

---

# 19. Analytics Event Handlers

The Kafka architecture now contains event-driven analytics projections/read models.

The analytics consumer updates read models such as:

```text
product_analytics
order_analytics
order_item_analytics
cart_analytics
cart_item_analytics
review_analytics
customer_analytics
category_analytics
time_analytics
geo_analytics
funnel_analytics
```

Kafka therefore acts as the event backbone between the transactional system and analytics projections.

---

# 20. Kafka Idempotency / Deduplication

The system includes:

```text
processed_events
```

This table is used for event-processing idempotency.

The purpose is to prevent duplicate Kafka delivery from causing duplicate analytics projection effects.

Conceptually:

```text
Kafka event
    |
    v
Check processed_events
    |
    +---- already processed ---> skip
    |
    +---- new event -----------> process
                                  |
                                  v
                           analytics projection
                                  |
                                  v
                           mark processed
```

This is important because Kafka consumers must be designed for at-least-once delivery behavior.

---

# 21. Analytics Read Models

The completed analytics read-model layer includes:

```text
product_analytics
order_analytics
order_item_analytics
processed_events

cart_analytics
cart_item_analytics

review_analytics

customer_analytics
category_analytics

time_analytics
geo_analytics
funnel_analytics
```

These tables are read-oriented projections rather than replacements for the transactional source-of-truth tables.

---

# 22. Product Analytics

Product analytics tracks product-level activity and sales-related metrics.

The established model includes fields supporting:

```text
cart_add_count
cart_remove_count
```

along with product performance information.

The database schema was corrected during end-to-end Kafka validation to include these counters.

Required correction:

```sql
ALTER TABLE product_analytics
ADD COLUMN cart_add_count BIGINT NOT NULL DEFAULT 0;

ALTER TABLE product_analytics
ADD COLUMN cart_remove_count BIGINT NOT NULL DEFAULT 0;
```

These fields are now part of the current analytics read-model expectation.

---

# 23. Cart Analytics

Cart-related analytics are updated from events including:

```text
CART_CREATED
CART_UPDATED
CART_DELETED
CART_ITEM_ADDED
CART_ITEM_UPDATED
CART_ITEM_REMOVED
```

Read models include:

```text
cart_analytics
cart_item_analytics
```

The architecture supports analytics such as:

- active carts
- stale carts
- abandoned carts
- cart item activity
- product additions/removals

---

# 24. Order Analytics

Order-related events include:

```text
ORDER_CREATED
ORDER_STATUS_CHANGED
ORDER_DELETED
```

The read models include:

```text
order_analytics
order_item_analytics
```

The API layer exposes order analytics and order-item analytics separately.

---

# 25. Payment Analytics

Payment events include:

```text
PAYMENT_CREATED
PAYMENT_DELETED
```

Payment state remains tied to transactional order state.

Analytics projections can use payment events to distinguish order creation from successful payment/revenue recognition.

---

# 26. Review Analytics

Review events include:

```text
REVIEW_CREATED
REVIEW_UPDATED
REVIEW_DELETED
```

The corresponding read model is:

```text
review_analytics
```

This allows review activity to be consumed asynchronously without coupling analytics queries directly to the transactional review tables.

---

# 27. Customer Analytics

Customer analytics are represented by:

```text
customer_analytics
```

The analytics API supports:

```text
GET /api/v1/analytics/customers/{userId}
GET /api/v1/analytics/customers/summary
```

The customer summary endpoint is cached for a short period to reduce repeated aggregation work.

---

# 28. Category Analytics

Category analytics are represented by:

```text
category_analytics
```

The API supports:

```text
GET /api/v1/analytics/categories/{categoryId}
GET /api/v1/analytics/categories?page=0&size=20
```

---

# 29. Time Analytics

Time-based analytics are represented by:

```text
time_analytics
```

Supported buckets include:

```text
HOUR
DAY
WEEK
MONTH
YEAR
```

API:

```text
GET /api/v1/analytics/time?bucket=DAY&limit=30
```

The time analytics response is cached briefly because these views are read-heavy.

---

# 30. Geography Analytics

Geographic analytics are represented by:

```text
geo_analytics
```

The source transactional dimensions include:

```text
city
country
```

API:

```text
GET /api/v1/analytics/geography
```

---

# 31. Funnel Analytics

The funnel projection uses daily buckets for stages including:

```text
PRODUCT_VIEWED
CART_ITEM_ADDED
CHECKOUT_STARTED
ORDER_CREATED
ORDER_PAID
```

The intended funnel is:

```text
Product View
     |
     v
Add to Cart
     |
     v
Checkout Started
     |
     v
Order Created
     |
     v
Order Paid
```

This allows conversion-oriented analytics to be computed independently of transactional REST queries.

---

# 32. Analytics REST API Inventory

Current analytics endpoints include:

```text
GET /api/v1/analytics/orders/{orderId}

GET /api/v1/analytics/orders/{orderId}/items

GET /api/v1/analytics/products/{productId}

GET /api/v1/analytics/products?page=0&size=20

GET /api/v1/analytics/carts/{cartId}

GET /api/v1/analytics/carts/{cartId}/items

GET /api/v1/analytics/carts/abandoned?staleMinutes=60

GET /api/v1/analytics/customers/{userId}

GET /api/v1/analytics/customers/summary

GET /api/v1/analytics/categories/{categoryId}

GET /api/v1/analytics/categories?page=0&size=20

GET /api/v1/analytics/time?bucket=DAY&limit=30

GET /api/v1/analytics/geography
```

---

# 33. Historical Data Generator

The project contains a dedicated historical data-generator module:

```text
scripts/data-generator
```

Usage:

```bash
cd scripts/data-generator
mvn -q clean package

java -jar target/data-generator.jar [small|large|users=N,products=N,orders=N] [--reset]
```

The generator is intended for historical/baseline data.

It can generate large datasets such as:

```text
10,000 users
5,000 products
100,000 orders
~300,000 order items
~90,000 payments
15 categories
```

It also backfills analytics read-model tables.

The generator intentionally does not represent live Kafka traffic.

---

# 34. Historical vs Live Cutoff

The architecture uses a historical cutoff concept to keep historical data separate from live event processing.

Conceptually:

```text
created_at < historicalCutoff
        |
        v
Historical / Batch


created_at >= historicalCutoff
        |
        v
Live / Streaming
```

This separation prevents historical and live processing from double-counting the same business activity.

---

# 35. Live Traffic Simulator

The project contains a live traffic simulator that exercises the real REST APIs.

The simulator does not write directly to:

- PostgreSQL
- Kafka
- analytics tables

Instead it uses the application's REST layer.

Typical flow:

```text
Live Traffic Simulator
        |
        v
REST API
        |
        v
Spring Service
        |
        v
PostgreSQL
        |
        v
Domain Event Publisher
        |
        v
Kafka
        |
        v
Analytics Consumer
```

Example start request:

```bash
curl -X POST http://localhost:8080/api/v1/simulator/start \
  -H "Content-Type: application/json" \
  -d '{"iterations":100,"intervalMs":300}'
```

Status:

```bash
curl http://localhost:8080/api/v1/simulator/status
```

Simulator actions include:

```text
CREATE_CART
ADD_CART_ITEM
UPDATE_CART_ITEM
REMOVE_CART_ITEM
CREATE_ORDER
CREATE_PAYMENT
CHANGE_ORDER_STATUS
CREATE_REVIEW
UPDATE_REVIEW
```

The simulator intentionally does not directly generate every analytics event type.

---

# 36. Kafka End-to-End Validation

The Kafka event architecture has been exercised end-to-end.

Verified flows include:

```text
REST write
   |
   v
PostgreSQL transaction
   |
   v
Domain event
   |
   v
Kafka topic
   |
   v
analytics-consumer
   |
   v
event router
   |
   v
analytics read model
   |
   v
analytics REST endpoint
```

Verified examples include:

- product create → Kafka → product analytics
- cart creation → cart analytics
- cart item add → cart item analytics
- order analytics
- order-item analytics
- abandoned carts
- customer analytics
- customer summary
- category analytics
- time analytics
- geography analytics

Kafka checkpoint:

```text
Topic:
ecommerce.domain.events.v1

Partitions:
3

Replication factor:
1

Consumer group:
analytics-consumer

Known healthy checkpoint:
lag = 0
```

---

# 37. Kafka Architecture Completion Criteria

The Kafka feature branch should be considered complete when the following are preserved:

```text
[✓] Common EventEnvelope
[✓] EventType catalog
[✓] EventEnvelopeFactory
[✓] DomainEventPublisher
[✓] after-commit publishing
[✓] Kafka producer configuration
[✓] ecommerce.domain.events.v1
[✓] Kafka consumer
[✓] analytics-consumer group
[✓] Event router
[✓] Event handlers
[✓] processed_events idempotency
[✓] analytics read models
[✓] analytics REST APIs
[✓] live traffic simulator
[✓] historical/live separation
[✓] Kafka end-to-end validation
```

---

# 38. Current Kafka Branch Checkpoint

The current feature branch is:

```text
feature/kafka-event-architecture
```

The branch represents the completed transactional + event-driven analytics architecture.

The intended Git promotion is:

```text
feature/kafka-event-architecture
              |
              v
             main
```

The feature branch should be pushed before promotion to `main`.

Then `main` should contain the complete Kafka architecture as the stable baseline.

---

# 39. Spark Module Already Present Locally

A Spark module already exists in the working tree:

```text
spark-analytics/
├── ARCHITECTURE_SPARK.md
└── spark-analytics/
    ├── pom.xml
    ├── src/
    ├── run-batch.sh
    └── run-streaming.sh
```

This module is intentionally standalone from the Spring Boot application.

Important:

```text
Spring Boot Maven dependency tree
            X
            |
            X
Spark Maven module
```

The Spark module should not be moved into:

```text
src/main/java
```

of the Spring Boot application.

---

# 40. Spark Batch Design Already Present

The Spark batch job is designed to read historical transactional data from PostgreSQL.

Inputs include tables such as:

```text
orders
order_items
products
categories
users
```

The batch job uses the historical cutoff so historical records are processed separately from live data.

Its planned/implemented output tables include:

```text
spark_batch_product_performance
spark_batch_category_performance
spark_batch_customer_behavior
spark_batch_daily_revenue
spark_batch_geo_performance
spark_batch_hourly_demand
```

The batch job is intended to use Spark distributed processing rather than replacing the existing Spring analytics projection layer.

---

# 41. Spark Structured Streaming Design Already Present

The streaming job is designed to consume:

```text
ecommerce.domain.events.v1
```

with a separate consumer group:

```text
spark-streaming-analytics
```

It must not reuse:

```text
analytics-consumer
```

The streaming job uses:

- Spark Structured Streaming
- Kafka source
- event-envelope parsing
- event-time processing
- watermarking
- one-minute tumbling windows
- checkpointing
- append-oriented streaming outputs

Current streaming output family:

```text
spark_streaming_*
```

The exact implementation remains a separate Spark-phase checkpoint.

---

# 42. Spark Configuration

The Spark module uses environment-driven configuration including:

```text
DB_URL
DB_USERNAME
DB_PASSWORD

KAFKA_BOOTSTRAP_SERVERS

SPARK_KAFKA_TOPIC
SPARK_KAFKA_GROUP_ID
SPARK_CHECKPOINT_LOCATION
SPARK_HISTORICAL_CUTOFF
SPARK_WINDOW_DURATION
SPARK_WATERMARK_DELAY
SPARK_TRIGGER_INTERVAL
SPARK_STARTING_OFFSETS
```

Default streaming starting offsets are designed for the Spark streaming consumer rather than changing the existing analytics consumer.

---


```text
main
 |
 +---- feature/kafka-event-architecture
 |
 +---- feature/spark-analytics
```

The Kafka feature branch represents the completed event architecture.

The Spark feature branch represents the next analytics-processing phase.

---

# 43. Final Kafka Baseline

The desired stable baseline after promotion is:

```text
main
 |
 v
Spring Boot REST API
 |
 v
PostgreSQL transactional system
 |
 v
Domain Events
 |
 v
Kafka
 |
 v
Kafka Consumer
 |
 v
Event Router
 |
 v
Analytics Read Models
 |
 v
Analytics REST APIs
```

This is the baseline from which Spark development should continue.

---

# 48. Future Spark + Unified Analytics Direction

The next analytics-processing stage is:

```text
Kafka
 |
 +----------------------+
 |                      |
 v                      v
Existing Analytics   Spark Streaming
Consumer              |
 |                    v
Read Models      spark_streaming_*
 |
 v

Historical PostgreSQL
 |
 v
Spark Batch
 |
 v
spark_batch_*
```

The future unification stage is:

```text
spark_batch_*
       +
spark_streaming_*
       |
       v
Unified Analytics
       |
       v
unified_*
       |
       v
Elasticsearch
       |
       v
Analytics API
       |
       v
Dashboard
```

The unified layer should be added only after the batch and streaming computations are stable.

---

# 49. Source-of-Truth Rules

The architecture follows these responsibilities:

```text
PostgreSQL
= transactional system of record

Kafka
= event backbone / transport

Spring analytics read models
= current event-driven operational analytics projections

Spark Batch
= distributed historical analytics

Spark Structured Streaming
= distributed live/windowed analytics

Unified Analytics
= future reconciliation/serving layer

Elasticsearch
= future fast analytics/search serving layer
```

Elasticsearch should not become the transactional source of truth.

---

# 50. Current Engineering Checkpoint

At the Kafka completion boundary:

```text
[✓] Spring Boot foundation
[✓] PostgreSQL + JPA/Hibernate
[✓] Domain entities
[✓] Relationships
[✓] Repositories
[✓] DTOs
[✓] Services
[✓] REST controllers
[✓] Validation
[✓] Global exception handling
[✓] Swagger/OpenAPI
[✓] Actuator
[✓] JUnit + Mockito
[✓] Historical data generator
[✓] Domain event model
[✓] Kafka producer
[✓] Kafka topic
[✓] Kafka consumer
[✓] Event router
[✓] Event handlers
[✓] Idempotency / processed events
[✓] Analytics read models
[✓] Analytics REST APIs
[✓] Live traffic simulator
[✓] Kafka end-to-end verification
[✓] Kafka feature branch ready for promotion
[ ] Spark test stabilization
[ ] Spark batch/streaming final validation
[ ] Unified analytics layer
[ ] Elasticsearch
[ ] Dashboard
[ ] Prometheus/Grafana
```

---



# 51. Final State Definition

The repository is considered ready to start the Spark phase when:

```text
main
  |
  +-- contains the complete Kafka event architecture
  |
  +-- does not contain unfinished Spark-only commits
  |
  +-- project_state.md reflects the current architecture
  |
  +-- stale change.md has been removed
  |
  +-- working tree is understood and clean enough for branching
```

At that point:

```text
main
  |
  +---- feature/spark-analytics
```

becomes the clean starting point for the next phase.
