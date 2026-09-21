# Analytics build-out — file list

## New files (Phase D–K)

### Cart analytics (Phase D)
- entity/CartAnalytics.java
- entity/CartItemAnalytics.java
- repository/CartAnalyticsRepository.java
- repository/CartItemAnalyticsRepository.java
- service/CartAnalyticsService.java
- event/consumer/handler/CartCreatedEventHandler.java
- event/consumer/handler/CartUpdatedEventHandler.java
- event/consumer/handler/CartDeletedEventHandler.java
- event/consumer/handler/CartItemAddedEventHandler.java
- event/consumer/handler/CartItemUpdatedEventHandler.java
- event/consumer/handler/CartItemRemovedEventHandler.java

### Review analytics (Phase E)
- entity/ReviewAnalytics.java
- repository/ReviewAnalyticsRepository.java
- service/ReviewAnalyticsService.java
- event/consumer/handler/ReviewCreatedEventHandler.java
- event/consumer/handler/ReviewUpdatedEventHandler.java
- event/consumer/handler/ReviewDeletedEventHandler.java

### Customer analytics (Phase F)
- entity/CustomerAnalytics.java
- repository/CustomerAnalyticsRepository.java
- service/CustomerAnalyticsService.java

### Category analytics (Phase G)
- entity/CategoryAnalytics.java
- repository/CategoryAnalyticsRepository.java
- service/CategoryAnalyticsService.java
- event/consumer/handler/CategoryCreatedEventHandler.java
- event/consumer/handler/CategoryUpdatedEventHandler.java

### Time analytics (Phase H)
- entity/TimeAnalytics.java
- repository/TimeAnalyticsRepository.java
- service/TimeAnalyticsService.java

### Geography analytics (Phase I)
- entity/GeoAnalytics.java
- repository/GeoAnalyticsRepository.java
- service/GeoAnalyticsService.java

### REST APIs (Phase K)
- controller/AnalyticsController.java
  (GET /api/v1/analytics/orders/{id}, /orders/{id}/items, /products/{id},
   /products, /carts/{id}, /carts/{id}/items, /carts/abandoned,
   /customers/{id}, /customers/summary, /categories/{id}, /categories,
   /time?bucket=DAY&limit=30, /geography)

## Modified files
- entity/ProductAnalytics.java
  + cartAddCount, cartRemoveCount fields/getters/setters
- service/ProductAnalyticsService.java
  + CategoryAnalyticsService dependency (new constructor arg)
  + recordCartAdd / recordCartRemove
  + addReview / updateReview / removeReview
  + category-count sync in upsertProduct/markDeleted, category rollups in addSale
- service/OrderAnalyticsService.java
  + CustomerAnalyticsService, TimeAnalyticsService, GeoAnalyticsService
    dependencies (new constructor args — was 3 args, now 6)
  + calls into all three on ORDER_CREATED and first-PAID
- test/service/OrderAnalyticsServiceTest.java
  + updated to the new 6-arg OrderAnalyticsService constructor with mocks

## Not implemented
- Phase J (Funnel analytics): still blocked on PRODUCT_VIEWED / CHECKOUT_STARTED
  events not existing in the producer/event catalog, as you already noted.

## Merge notes
- `ddl-auto=update` will create the new tables (cart_analytics, cart_item_analytics,
  review_analytics, customer_analytics, category_analytics, time_analytics,
  geo_analytics) automatically on next boot — no manual migration needed.
- OrderAnalyticsService and ProductAnalyticsService constructor signatures changed;
  if anything else in your branch constructs them directly (not via Spring), update
  those call sites too.
