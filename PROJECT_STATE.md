E-Commerce Analytics & Intelligence Platform
Development Documentation
Coverage: Project foundation through the completed core
REST/transactional backend, database relationships, validation,
exception handling, testing, Swagger/OpenAPI, and Actuator/health
visibility.
Current checkpoint: Core REST CRUD + JUnit Testing + Swagger UI completed → Event-Driven Architecture next.
1. Project Overview
   The E-Commerce Analytics & Intelligence Platform is a backend-first
   distributed analytics system for an e-commerce domain. The transactional
   backend is the foundation for a later event-driven analytics pipeline.
   The final planned flow is:
   E-Commerce REST APIs
   ↓
   PostgreSQL + JPA/Hibernate
   ↓
   Domain Events
   ↓
   Kafka
   ↓
   Spark / Consumers
   ↓
   Elasticsearch / Analytics Store
   ↓
   Analytics REST APIs
   ↓
   Dashboard


2. Technology Stack
   Current backend
   Java 21
   Spring Boot
   Maven
   PostgreSQL
   Spring Data JPA
   Hibernate ORM
   REST APIs
   Jakarta Bean Validation
   JUnit
   Mockito
   SpringDoc / OpenAPI / Swagger
   Spring Boot Actuator
   Git
   Planned later stages
   Apache Kafka
   Apache Spark
   Spark SQL
   Spark Structured Streaming
   Elasticsearch
   Docker / Docker Compose
   Prometheus / Grafana
   Integration and load testing




3. Development Approach
   Development followed an incremental workflow:
   Design
   ↓
   Entity
   ↓
   Repository
   ↓
   DTO
   ↓
   Service
   ↓
   Controller
   ↓
   Validation / Exceptions
   ↓
   Compile
   ↓
   Run
   ↓
   Test
   ↓
   Inspect database / logs
   ↓
   Fix
   ↓
   Confirm
   This approach exposed real database constraints during order/payment
   testing and allowed the implementation to be corrected before moving
   toward Kafka.



4. Day 1 --- Foundation, PostgreSQL, JPA and Domain Model
   4.1 Spring Boot foundation
   The Spring Boot backend was created and configured as a Maven project
   using Java 21.
   The application was repeatedly started successfully on port 8080.
   The codebase follows a layered structure:
   com.ecommerce.analytics
   ├── controller
   ├── service
   ├── repository
   ├── entity
   ├── dto
   └── exception
   Responsibilities are separated between HTTP controllers, business
   services, persistence repositories, DTOs, and exception handling.
   4.2 PostgreSQL
   PostgreSQL was established as the transactional system of record.
   Verified environment:
   Database: ecommerce_analytics
   Schema:   public
   PostgreSQL: 15.4
   JDBC: jdbc:postgresql://localhost:5432/ecommerce_analytics
   HikariCP successfully created the database connection pool and
   Hibernate/JPA initialized the persistence unit.


4.3 Core entities
The transactional domain consists of:
User
Category
Product
Cart
CartItem
Order
OrderItem
Payment
Review
An Event model is planned for the next event-driven phase and is not
treated as a completed transactional table in this milestone.

5. Database Tables
   5.1 users
   users
--------------------------------
id             PK
name
email
age
gender
city
country
created_at
Represents customers/users. city and country also provide the
geographic dimensions required by later analytics.



5.2 categories
categories
--------------------------------
id             PK
name
Represents product categories.
5.3 products
products
--------------------------------
id             PK
name
category_id    FK → categories.id
price
stock
brand
created_at
Represents products available in the e-commerce system.
5.4 carts
carts
--------------------------------
id                 PK
user_id        FK → users.id
Represents the shopping cart associated with a user.
The repository supports retrieval by user:
Optional<Cart> findByUserId(Long userId);


5.5 cart_items
cart_items
--------------------------------
id             PK
cart_id        FK → carts.id
product_id     FK → products.id
quantity
Represents products currently placed in a cart.
Cart item operations validate both the cart and product before
modification.
5.6 orders
orders
--------------------------------
id                 PK
user_id        FK → users.id
total_amount
status
payment_status
created_at
Represents a placed order.
The order tracks fulfillment state separately from payment state.
5.7 order_items
order_items
--------------------------------
id                  PK
order_id        FK → orders.id
product_id     FK → products.id
quantity
unit_price
Represents the products contained in an order.
unit_price preserves the price used for the order item rather than
relying only on the product's current price.
5.8 payments
The payment domain is associated with orders.
Conceptually:
payments
--------------------------------
id             PK
order_id       FK → orders.id
payment_method
status
amount
transaction_id
Payment creation validates the referenced order and enforces the current
business rule that an order should not receive duplicate payments.
5.9 reviews
reviews
--------------------------------
id                 PK
user_id        FK → users.id
product_id   FK → products.id
rating
comment
created_at
Reviews connect users/customers to products.




6. Complete Relationship Model


                            			 ORDER
          					       │
          					      ▼
           				           PAYMENT


Relationship table


User Cart One user has the carts.user_id → users.id
current cart
User Order One user can have orders.user_id → users.id
many orders
User Review One user can reviews.user_id → users.id
create many
reviews
Category Product One category can products.category_id → categories.id
contain many
products
Cart CartItem One cart can cart_items.cart_id → carts.id
contain many cart
items
Product CartItem One product can cart_items.product_id → products.id
appear in many
cart items
Order OrderItem One order order_items.order_id → orders.id
contains one or
more order items
Product OrderItem One product can order_items.product_id → products.id
appear in many
order items
Order Payment Current business payments.order_id → orders.id
rule allows one
payment per order

7. Day 2 --- REST API and Business Services
   The transactional REST layer was implemented using the standard Spring
   Boot flow:
   HTTP Request
   ↓
   Controller
   ↓
   Service
   ↓
   Repository
   ↓
   PostgreSQL
   DTOs are used to separate external API payloads from persistence
   entities.

7.1 User module
Components:
User
UserRequest
UserResponse
UserRepository
UserService
UserController
User REST operations and not-found handling were integrated with the
global exception layer.



7.2 Category module
Components:
Category
CategoryRequest
CategoryResponse
CategoryRepository
CategoryService
CategoryController
CategoryNotFoundException
Missing categories return 404 Not Found.

7.3 Product module
Components:
Product
ProductRequest
ProductResponse
ProductRepository
ProductService
ProductController
ProductNotFoundException
ProductInUseException
Implemented behavior includes:
Product CRUD
Product/category validation
Product not found → 404
Product-in-use conflict → 409



7.4 Cart module
Components:
Cart
CartItem
CartRequest
CartResponse
CartItemRequest
CartItemResponse
CartRepository
CartItemRepository
CartService
CartController
Important repository methods include:
Optional<Cart> findByUserId(Long userId);

Optional<CartItem> findByIdAndCartId(Long id, Long cartId);
Cart item creation follows:
Validate cart
↓
Validate product
↓
Create CartItem
↓
Associate with Cart
↓
Save
↓
Return response DTO
This prevents an item from being manipulated outside its intended cart.



7.5 Order module
Components:
Order
OrderItem
OrderRequest
OrderItemRequest
OrderResponse
OrderItemResponse
OrderRepository
OrderItemRepository
OrderService
OrderController
OrderNotFoundException
Order creation:
Validate User
↓
For each requested item:
Validate Product
↓
Create OrderItem
↓
Store quantity + unit price
↓
Add to Order
↓
Calculate total
↓
Save Order
↓
Return OrderResponse
The current service calculates:
Order total = Σ(quantity × product price)
New orders start with:
status        = PENDING
paymentStatus = UNPAID

7.6 Payment module
Components:
Payment
PaymentRequest
PaymentResponse
PaymentRepository
PaymentService
PaymentController
PaymentNotFoundException
Business rules implemented:
Order must exist before payment creation.
Duplicate payments are prevented.
Payment amount must match the order total.
Creating a payment updates the order payment status.
Deleting a payment changes the order payment status back to
UNPAID.
Missing order → 404.
Missing payment → 404.
A successful documented test used:
POST /api/v1/payments

orderId: 8
paymentMethod: CARD
status: PAID
amount: 1099.99
transactionId: txn-payment-test-008

Result: 200 OK
Payment ID: 7
Order ID: 8
The related order was then checked through:
GET /api/v1/orders/8
and its payment status was verified as PAID.

7.7 Review module
Components:
Review
ReviewRequest
ReviewResponse
ReviewRepository
ReviewService
ReviewController
ReviewNotFoundException
Supported operations:
Create
Get all
Get by ID
Update
Delete
Validation:
rating: 1–5
user must exist
product must exist
A rating of 6 was verified as:
400 Bad Request
Rating cannot exceed 5
Missing relationships were also tested:
userId: 999
→ 404 User not found

productId: 999
→ 404 Product not found
Missing review operations return 404.


8. Global Exception Handling
   A centralized GlobalExceptionHandler was implemented using:
   @RestControllerAdvice
   Current mappings:
   Condition HTTP status

UserNotFoundException 404
OrderNotFoundException 404
ProductNotFoundException 404
CategoryNotFoundException 404
CartNotFoundException 404
ReviewNotFoundException 404
ProductInUseException 409
Validation errors 400
Unexpected errors 500
Standard error response:
{
"status": 404,
"message": "..."
}
Validation response:
{
"status": 400,
"message": "Validation failed",
"errors": {
"field": "validation message"
}
}


9. Important Database Constraint Finding
   During order deletion testing, PostgreSQL returned:
   SQLState: 23503
   The cause was the relationship:
   payments.order_id → orders.id
   A payment referencing an order prevents the referenced order from being
   deleted unless cascading is explicitly configured.
   Order items also reference the order:
   order_items.order_id → orders.id
   Therefore, the deletion sequence was changed to:
   Find Order
   ↓
   Delete Payment(s)
   ↓
   Delete OrderItem(s)
   ↓
   Delete Order
   The operation is transactional.
   This is an important design decision because it makes the deletion order
   explicit and consistent with PostgreSQL foreign-key constraints.




10. Validation
    Validation was added at the DTO/service boundary.
    Examples include:
    Required user IDs
    Required product IDs
    Non-empty order item lists
    Order item validation
    Review rating 1–5
    Referenced user existence
    Referenced product existence
    Referenced order existence
    For example, OrderRequest validates that:
    userId is not null
    items is not empty
    items are valid
    Invalid input is converted into a consistent 400 Bad Request response
    by the global exception handler.

11. Testing
    Testing was performed using both manual API testing and automated
    service tests.
    11.1 Manual testing
    curl was used to verify:
    GET
    POST
    PUT
    DELETE
    and negative scenarios:
    404 Not Found
    400 Bad Request
    409 Conflict
    Relationship behavior was also explicitly tested.
    11.2 JUnit / Mockito
    Service tests use JUnit and Mockito.
    Tests cover:
    Successful service operations
    Repository calls
    Not-found exceptions
    Order creation
    Order total calculation
    Payment behavior
    Review behavior
    Delete behavior
    Relationship validation
    During development, a monetary type mismatch was found between Double
    and BigDecimal.
    The important lesson is to keep monetary types consistent through:
    Entity
    ↓
    Service
    ↓
    DTO
    ↓
    Mockito stubs
    ↓
    Assertions
    BigDecimal is the appropriate representation for monetary
    persistence/calculation where exact decimal behavior is required.
    A documented clean Maven test run completed with:
    Tests run: 94
    Failures: 0
    Errors: 0
    Skipped: 0

BUILD SUCCESS

12. Swagger / OpenAPI
    SpringDoc/OpenAPI support was added to document the REST API.
    The application exposes:
    /v3/api-docs
    /swagger-ui.html
    Swagger is used to make the REST contract visible before starting the
    Kafka phase.
    The documented API domain includes:
    Users
    Products
    Categories
    Cart
    Orders
    Payments
    Reviews
    The API documentation provides a place to inspect:
    HTTP methods
    Endpoint paths
    Request DTOs
    Response DTOs
    Parameters
    Validation
    HTTP status codes


13. Health / Actuator
    Spring Boot Actuator is enabled for operational visibility.
    Startup logs confirm Actuator endpoints under:
    /actuator
    The distinction is:
    Business endpoints
    /api/v1/...

Operational endpoints
/actuator/...
This provides the foundation for the later monitoring phase involving
Prometheus and Grafana.

14. Current API / Module Inventory

Module Main entity Request DTO Response DTO Repository Service Controller

User User UserRequest UserResponse UserRepository UserService UserController
Category Category CategoryRequest CategoryResponse CategoryRepository CategoryService CategoryController
Product Product ProductRequest ProductResponse ProductRepository ProductService ProductController
Cart Cart CartRequest CartResponse CartRepository CartService CartController
Cart Item CartItem CartItemRequest CartItemResponse CartItemRepository CartService CartController
Order Order OrderRequest OrderResponse OrderRepository OrderService OrderController
Order Item OrderItem OrderItemRequest OrderItemResponse OrderItemRepository OrderService OrderController
Payment Payment PaymentRequest PaymentResponse PaymentRepository PaymentService PaymentController
Review Review ReviewRequest ReviewResponse ReviewRepository ReviewService ReviewController

15. Transactional Data vs Event Data
    The architecture now has a clear separation between transactional state
    and future event data.
    Transactional state
    PostgreSQL stores:
    User
    Product
    Category
    Cart
    CartItem
    Order
    OrderItem
    Payment
    Review
    Event data
    The next phase will introduce events such as:
    USER_REGISTERED
    PRODUCT_VIEWED
    PRODUCT_ADDED_TO_CART
    ORDER_CREATED
    PAYMENT_COMPLETED
    REVIEW_CREATED
    The purpose of events is to describe actions that happened so that
    independent downstream consumers can process them.

16. Next Phase --- Common Event Model + Kafka
    The next implementation phase is:
    REST operation
    ↓
    Domain event
    ↓
    Kafka producer
    ↓
    Kafka topic
    ↓
    Consumer
    The first step is not Spark or Elasticsearch.
    Step 1 --- Event model
    Create a common event structure.
    Example:
    {
    "eventId": "evt-1005",
    "eventType": "ORDER_CREATED",
    "userId": "user-101",
    "orderId": "order-9001",
    "timestamp": "2026-09-14T10:40:00Z",
    "metadata": {}
    }
    Initial event types:
    USER_REGISTERED
    PRODUCT_VIEWED
    PRODUCT_ADDED_TO_CART
    ORDER_CREATED
    PAYMENT_COMPLETED
    REVIEW_CREATED
    Step 2 --- Kafka producer
    After the event model compiles:
    Kafka dependency
    Kafka producer configuration
    Kafka topics
    Producer service
    Possible topics:
    user-events
    product-events
    cart-events
    order-events
    payment-events
    review-events
    A simplified MVP can also use:
    ecommerce-events
    with eventType identifying the event.
    Step 3 --- Integrate services
    Eventually publish events from:
    UserService
    ProductService
    CartService
    OrderService
    PaymentService
    ReviewService
    Step 4 --- Consumers
    Initially consumers should:
    Receive event
    ↓
    Log event
    ↓
    Verify delivery
    ↓
    Confirm producer/consumer communication
    Only after this flow is stable should the project move deeper into
    analytics infrastructure.

17. Planned Analytics Pipeline
    After Kafka:
    Kafka
    ↓
    Spark consumers / processing
    ↓
    Batch + Streaming analytics
    ↓
    Elasticsearch / Analytics Store
    ↓
    Analytics REST APIs
    ↓
    Dashboard
    Planned analytics include:
    Revenue
    Total revenue
    Daily/monthly revenue
    Revenue by category
    Revenue by product
    Revenue by city/country
    Product
    Product views
    Product sales
    Product revenue
    Trending products
    Conversion metrics
    Customer
    Total customers
    New customers
    Returning customers
    Average spending
    Orders per customer
    Customer lifetime value
    Funnel
    Visitors
    ↓
    Product Views
    ↓
    Add to Cart
    ↓
    Checkout
    ↓
    Purchase
    Cart
    Cart abandonment
    Frequently abandoned products
    Repeated abandonment
    Category-level abandonment
    Payment
    Successful payments
    Failed payments
    Payment methods
    Transaction amounts
    Geography
    Country
    Province/state
    City
    Time
    Hourly
    Daily
    Weekly
    Monthly
    Yearly

18. Wishlist Scope Decision
    Wishlist is not part of the documented core module/event requirements.
    Therefore it remains outside the current scope and should not be
    introduced unless the project requirements are explicitly expanded.

19. Pre-Kafka Acceptance Checklist
    Foundation
    Java 21
    Spring Boot
    Maven
    PostgreSQL
    JPA/Hibernate
    Repository layer
    Service layer
    REST controller layer
    Domain
    User
    Product
    Category
    Cart
    CartItem
    Order
    OrderItem
    Payment
    Review
    Relationships
    User → Cart
    User → Orders
    User → Reviews
    Category → Products
    Cart → CartItems
    CartItem → Product
    Order → OrderItems
    OrderItem → Product
    Order → Payment
    Review → User
    Review → Product
    API quality
    CRUD endpoints
    DTO request/response separation
    Validation
    Global exception handling
    Swagger/OpenAPI
    Actuator / health visibility
    Testing
    Manual curl testing
    JUnit
    Mockito
    Application context testing
    Clean Maven test run documented


20. Final Checkpoint
    At the end of the current milestone:
    Spring Boot
    ↓
    PostgreSQL + JPA/Hibernate
    ↓
    Entities + Relationships
    ↓
    Repositories
    ↓
    DTOs
    ↓
    Services
    ↓
    REST Controllers
    ↓
    Validation
    ↓
    Global Exception Handling
    ↓
    Users / Products / Categories
    ↓
    Cart / CartItems
    ↓
    Orders / OrderItems
    ↓
    Payments
    ↓
    Reviews
    ↓
    JUnit + Mockito
    ↓
    curl verification
    ↓
    Swagger / OpenAPI
    ↓
    Actuator / Health
    ↓
   
    ↓
    Common Event Model
    ↓
    Kafka Producer
    ↓
    Kafka Consumers
    ↓ ========================
    CURRENT CHECKPOINT
    ========================
    Spark / Elasticsearch
    ↓
    Analytics APIs
    ↓
    Dashboard
    The project is therefore ready to proceed from the transactional REST
    foundation into the event-driven architecture.
    Next coding checkpoint: create the common event model and event types,
    compile and test it, and only then configure the Kafka producer.


