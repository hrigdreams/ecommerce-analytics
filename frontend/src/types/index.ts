/**
 * All types below are transcribed directly from the backend source
 * (repomix-output.xml) -- every field name, nesting level and endpoint path
 * referenced in api/client.ts and api/hooks.ts was checked against the
 * actual Java record/controller it comes from. File references are noted
 * per section so a future backend change is easy to re-verify against.
 */

// ---------------------------------------------------------------------------
// Elasticsearch: ProductDocument
// src/main/java/com/ecommerce/analytics/elasticsearch/document/ProductDocument.java
// This is the FULL document shape returned by native search hits -- it is
// the only place salesCount/popularityScore/inStock live. It does NOT
// appear anywhere in UnifiedAnalyticsResponse (see note on that below).
// ---------------------------------------------------------------------------
export interface ProductDocument {
  id: number;
  name: string;
  price: number;
  categoryId: number | null;
  categoryName: string | null;
  averageRating: number | null;
  reviewCount: number | null;
  salesCount: number;
  popularityScore: number;
  inStock: boolean;
  createdAt: string;
  lastEventOccurredAt: string | null;
  lastEventId: string | null;
  updatedAt: string | null;
}

// ---------------------------------------------------------------------------
// GET /api/v1/search/native/products -> ProductSearchResponse
// src/main/java/com/ecommerce/analytics/dto/search/ProductSearchResponse.java
// src/main/java/com/ecommerce/analytics/controller/search/NativeSearchController.java
//
// IMPORTANT backend quirk (flagged, not "fixed" here -- this is what the API
// actually returns): the native-search path always constructs
// ProductSearchResponse via its "Secondary constructor for Native Search
// calls", which hardcodes page=0 and size=10 in the response body
// regardless of the page/size you actually requested. Do NOT read
// pagination state back off this response -- track the requested page/size
// client-side instead (see useProductSearch).
// ---------------------------------------------------------------------------
export interface CategoryBucket {
  categoryId: number;
  docCount: number;
}

export interface PriceRangeBucket {
  label: string;
  docCount: number;
}

export interface ProductSearchResponse {
  hits: ProductDocument[];
  totalHits: number;
  totalPages: number;
  categoryBuckets: CategoryBucket[];
  priceRangeBuckets: PriceRangeBucket[];
  /** Always 0 on the native-search endpoint -- see note above. Not used for UI state. */
  page: number;
  /** Always 10 on the native-search endpoint -- see note above. Not used for UI state. */
  size: number;
}

// ---------------------------------------------------------------------------
// GET /api/v1/analytics/unified -> UnifiedAnalyticsResponse
// src/main/java/com/ecommerce/analytics/dto/analytics/unified/*.java
// src/main/java/com/ecommerce/analytics/service/UnifiedAnalyticsService.java
//
// This endpoint composes Spring (customerSummary), Spark batch (revenueTrend/
// productPerformance/categoryPerformance/geoPerformance) and Spark streaming
// (liveEventCounts/liveOrderActivity/livePaidRevenue) sections. It does NOT
// include anything from Elasticsearch -- confirmed by the backend's own
// comment on ProductSearchController: "Elasticsearch was NOT folded into
// GET /api/v1/analytics/unified". So the dashboard's "top products" table
// below is Spark's productPerformance (units_sold/revenue from Postgres),
// not live ES salesCount/popularityScore/inStock -- those three fields only
// ever come from /search/native/products (CatalogPage).
//
// Every section is wrapped: { meta: {source, status, asOf}, rows: [...] }.
// `rows` is a raw SELECT * projection off a Spark-written Postgres table
// (ResultSet -> Map<String,Object> -> JSON), so keys are the exact snake_case
// column/alias names from the SQL -- not camelCase, not renamed.
// ---------------------------------------------------------------------------
export type DataSource = 'SPARK_BATCH' | 'SPARK_STREAMING' | 'SPRING';
export type SectionStatus = 'OK' | 'EMPTY' | 'UNAVAILABLE';

export interface SectionMeta {
  source: DataSource;
  status: SectionStatus;
  /** ISO instant, or null for EMPTY/UNAVAILABLE sections (and for non-windowed batch sections, which never set it). */
  asOf: string | null;
}

export interface SparkSection<TRow> {
  meta: SectionMeta;
  rows: TRow[];
}

/** spark_batch_revenue_trend -- BatchAnalytics.dailyRevenueTrend */
export interface RevenueTrendRow {
  day: string;
  order_count: number;
  paid_order_count: number;
  revenue: number;
}

/** spark_batch_product_performance -- BatchAnalytics.productPerformance (PAID orders only) */
export interface ProductPerformanceRow {
  product_id: number;
  product_name: string;
  category_id: number | null;
  units_sold: number;
  revenue: number;
  order_count: number;
}

/** spark_batch_category_performance -- BatchAnalytics.categoryPerformance */
export interface CategoryPerformanceRow {
  category_id: number;
  category_name: string;
  product_count: number;
  units_sold: number;
  revenue: number;
}

/** spark_batch_geo_performance -- BatchAnalytics.geoPerformance */
export interface GeoPerformanceRow {
  country: string;
  city: string;
  order_count: number;
  paid_order_count: number;
  revenue: number;
}

/** spark_streaming_event_counts -- StreamingAnalytics.eventCountsByWindow (1-minute sliding window) */
export interface EventCountRow {
  window_start: string;
  window_end: string;
  event_type: string;
  event_count: number;
}

/** spark_streaming_order_activity -- StreamingAnalytics.orderActivityByWindow, ORDER_CREATED events only */
export interface OrderActivityRow {
  window_start: string;
  window_end: string;
  orders_created: number;
  gross_amount: number;
}

/** spark_streaming_revenue -- StreamingAnalytics.paidRevenueByWindow, PAYMENT_CREATED/PAID events only */
export interface PaidRevenueRow {
  window_start: string;
  window_end: string;
  paid_count: number;
  paid_revenue: number;
}

export interface CustomerSummarySection {
  meta: SectionMeta;
  totalCustomers: number;
  customersWithPaidOrders: number;
  totalCategories: number;
}

export interface UnifiedAnalyticsResponse {
  generatedAt: string;
  customerSummary: CustomerSummarySection;
  revenueTrend: SparkSection<RevenueTrendRow>;
  productPerformance: SparkSection<ProductPerformanceRow>;
  categoryPerformance: SparkSection<CategoryPerformanceRow>;
  geoPerformance: SparkSection<GeoPerformanceRow>;
  liveEventCounts: SparkSection<EventCountRow>;
  liveOrderActivity: SparkSection<OrderActivityRow>;
  livePaidRevenue: SparkSection<PaidRevenueRow>;
}

// ---------------------------------------------------------------------------
// Operations page: admin trigger endpoints
// ---------------------------------------------------------------------------

/**
 * POST /api/v1/simulator/start, /stop, /pulse/start, /pulse/stop, GET /status
 * -- simulator/dto/SimulatorStatus.java
 *
 * PHASE 2: `pulseRunning` is a new field added to the backend record in the
 * Phase 1 delivery (10th record component, additive). It reflects the new,
 * independent continuous "pulse" mode (POST /simulator/pulse/start|stop) --
 * `running` still reflects the original burst/iterations mode only, and the
 * two can be true at the same time.
 */
export interface SimulatorStatus {
  running: boolean;
  enabled: boolean;
  actionsCompleted: number;
  actionsFailed: number;
  targetIterations: number;
  historicalCutoff: string;
  startedAt: string | null;
  actionCounts: Record<string, number>;
  lastError: string | null;
  pulseRunning: boolean;
}

/** POST /api/v1/search/admin/sync/popularity -- dto/search/PopularitySyncResponse.java */
export interface PopularitySyncResponse {
  sourceStatus: string;
  productsUpdated: number;
}

/** POST /api/v1/search/admin/reindex/products -- dto/search/BackfillResponse.java (not wired into the UI yet, typed for completeness) */
export interface BackfillResponse {
  productsIndexed: number;
  pagesProcessed: number;
  targetIndex: string | null;
}

/** entity/OrderStatus.java */
export type OrderStatus = 'PENDING' | 'CONFIRMED' | 'PROCESSING' | 'SHIPPED' | 'DELIVERED' | 'CANCELLED';

// ---------------------------------------------------------------------------
// PHASE 2: new endpoints added by the Phase 1 backend refactor
// (AdvancedAnalyticsController.java) -- "autonomous backend expansions":
// conversion funnel, peak shopping hours, average order value. All three
// are new READ paths over data that already existed and was already
// correct (FunnelAnalytics / spark_batch_hourly_pattern), per that
// delivery's CHANGES.md -- not new computation, so there is no risk of
// these numbers disagreeing with anything the backend already reports
// elsewhere.
// ---------------------------------------------------------------------------

/**
 * GET /api/v1/analytics/funnel -> FunnelAnalyticsResponse[]
 * dto/analytics/FunnelAnalyticsResponse.java
 *
 * One row per UTC day. Rate fields are `null` (not 0) when the previous
 * stage's count is 0 -- "no data to compute a rate from" is a different
 * claim from "0% conversion", and the backend's own DTO javadoc makes the
 * same distinction, so the UI must not coerce null to 0% either.
 */
export interface FunnelAnalyticsRow {
  bucketDate: string;
  productViews: number;
  cartItemAdded: number;
  checkoutStarted: number;
  ordersCreated: number;
  ordersPaid: number;
  viewToCartRate: number | null;
  cartToCheckoutRate: number | null;
  checkoutToOrderRate: number | null;
  orderToPaidRate: number | null;
  updatedAt: string;
}

/**
 * GET /api/v1/analytics/order-value -> OrderValueSummaryResponse
 * dto/analytics/OrderValueSummaryResponse.java
 * Paid orders only, over an arbitrary [from, to) range.
 */
export interface OrderValueSummary {
  from: string;
  to: string;
  paidOrderCount: number;
  totalRevenue: number;
  averageOrderValue: number;
}

/**
 * GET /api/v1/analytics/peak-hours -> SparkSection<PeakHourRow>
 * spark_batch_hourly_pattern, written by BatchAnalytics.hourOfDayPattern().
 * Full 0-23 hour-of-day pattern, no LIMIT on the backend query -- ordered by
 * hour_of_day ascending, so rows arrive already in chart order.
 */
export interface PeakHourRow {
  hour_of_day: number;
  order_count: number;
  revenue: number;
}

// ---------------------------------------------------------------------------
// PHASE 3A backend delivery: simulator event log
// src/main/java/com/ecommerce/analytics/simulator/dto/RecentSimEvent.java
// ---------------------------------------------------------------------------

/**
 * GET /api/v1/simulator/events/recent -> RecentSimEvent[]
 * Up to the last 100 successfully-generated simulator actions, newest
 * first, from both burst and pulse mode. `detail` is a flat, already-
 * formatted "key=value key=value ..." string (e.g.
 * "orderId=482 userId=17 total=129.50 status=PENDING") -- the exact same
 * text the backend already writes to its own application log for that
 * action, not a structured payload object. The Live Transaction Feed
 * parses it with `parseEventDetail` (utils/formatters.ts) to pull out
 * individual fields like userId/amount for display, falling back to the
 * raw string for anything it doesn't recognize.
 */
export interface RecentSimEvent {
  eventType: string;
  occurredAt: string;
  mode: 'BURST' | 'PULSE';
  detail: string;
}

/**
 * GET /api/v1/categories -> CategoryResponse[]
 * dto/CategoryResponse.java (id, name only -- confirmed against the
 * backend source, not guessed).
 *
 * PHASE 3B: the Elasticsearch search facet aggregation
 * (CategoryBucket -- see dto/search/CategoryBucket.java) only returns
 * {categoryId, docCount}, no name, by design (it's a plain Elasticsearch
 * terms aggregation keyed on the numeric categoryId field). This endpoint
 * is the actual source of categoryId -> name for the Catalog page's
 * faceted sidebar, fetched once and cached client-side (categories change
 * essentially never) rather than trying to derive names from whichever
 * products happen to be in the current page of search hits, which would
 * silently omit the name for a category with zero matches in the current
 * query/page.
 */
export interface CategoryResponse {
  id: number;
  name: string;
}
