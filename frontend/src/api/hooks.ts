import { useEffect, useState } from 'react';
import useSWR from 'swr';
import { fetcher } from './client';
import type {
  CategoryResponse,
  FunnelAnalyticsRow,
  OrderValueSummary,
  PeakHourRow,
  ProductSearchResponse,
  RecentSimEvent,
  SimulatorStatus,
  SparkSection,
  UnifiedAnalyticsResponse,
} from '../types';

/**
 * Executive analytics feed -- GET /api/v1/analytics/unified
 * (UnifiedAnalyticsController, UnifiedAnalyticsService). Polls every 3s so
 * the live (Spark streaming) sections track the backend's 1-minute sliding
 * window without a manual refresh -- refreshInterval is the whole mechanism,
 * there is no websocket. The endpoint itself is server-side cached for 30s
 * (@Cacheable in UnifiedAnalyticsService), so polling faster than that would
 * just re-read the same cached snapshot.
 *
 * PHASE 2: `live` toggles polling on/off (refreshInterval 3000 vs 0) without
 * unmounting the SWR hook, so the "pause live updates" control on the
 * dashboard freezes the chart data in place -- SWR keeps the last-fetched
 * value, it just stops re-fetching -- instead of tearing down and losing
 * the last rendered frame.
 */
export function useUnifiedAnalytics(live = true) {
  const { data, error, isLoading } = useSWR<UnifiedAnalyticsResponse>(
    '/analytics/unified',
    fetcher,
    {
      refreshInterval: live ? 3000 : 0,
      revalidateOnFocus: false,
      keepPreviousData: true,
    },
  );

  return {
    analytics: data,
    isLoading,
    isError: Boolean(error),
    error,
  };
}

/** Delays updating the returned value until `value` has been stable for `delayMs`. */
function useDebouncedValue<T>(value: T, delayMs: number): T {
  const [debounced, setDebounced] = useState(value);

  useEffect(() => {
    const timer = setTimeout(() => setDebounced(value), delayMs);
    return () => clearTimeout(timer);
  }, [value, delayMs]);

  return debounced;
}

/**
 * Elasticsearch native search -- GET /api/v1/search/native/products
 * (NativeSearchController, ProductSearchNativeService). Full-text relevance
 * search (name^3, categoryName^2, fuzziness AUTO) returning full
 * ProductDocument hits (salesCount/popularityScore/inStock included) plus
 * category/price-range bucket aggregations.
 *
 * The raw keystroke value is debounced by 300ms before it becomes an SWR
 * key, so typing doesn't fan out a request per keystroke. `categoryId` lets
 * the UI filter by one of the returned categoryBuckets without a second
 * round trip. Page/size are tracked here, client-side, rather than read
 * back off the response -- see the ProductSearchResponse.page/size doc
 * comment in types/index.ts for why.
 *
 * PHASE 3B: added `minPrice`/`maxPrice`, passed straight through as the
 * same-named query params `NativeSearchController` already accepts
 * (confirmed against the backend source -- these were always supported
 * server-side, just never wired up from the UI before the faceted
 * sidebar). `shouldFetch` now also triggers on a price filter alone, same
 * as it already did for `categoryId` alone, so checking only a price-range
 * box (no text typed, no category picked) still searches instead of
 * sitting on the "start typing" empty state.
 */
export function useProductSearch(
  query: string,
  categoryId: number | null,
  minPrice: number | null = null,
  maxPrice: number | null = null,
  page = 0,
  size = 12,
) {
  const debouncedQuery = useDebouncedValue(query.trim(), 300);

  const shouldFetch =
    debouncedQuery.length > 0 || categoryId !== null || minPrice !== null || maxPrice !== null;
  const params = new URLSearchParams();
  if (debouncedQuery) params.set('q', debouncedQuery);
  if (categoryId !== null) params.set('categoryId', String(categoryId));
  if (minPrice !== null) params.set('minPrice', String(minPrice));
  if (maxPrice !== null) params.set('maxPrice', String(maxPrice));
  params.set('page', String(page));
  params.set('size', String(size));

  const key = shouldFetch ? `/search/native/products?${params.toString()}` : null;

  const { data, error, isLoading } = useSWR<ProductSearchResponse>(key, fetcher, {
    revalidateOnFocus: false,
    keepPreviousData: true,
  });

  return {
    hits: data?.hits ?? [],
    totalHits: data?.totalHits ?? 0,
    totalPages: data?.totalPages ?? 0,
    categoryBuckets: data?.categoryBuckets ?? [],
    priceRangeBuckets: data?.priceRangeBuckets ?? [],
    isLoading: shouldFetch && isLoading,
    isError: Boolean(error),
    /** True while the user is mid-keystroke, before the debounce settles. */
    isDebouncing: query.trim() !== debouncedQuery,
  };
}

/**
 * GET /api/v1/categories -- the id -> human-readable name source for the
 * Catalog page's faceted sidebar (see CategoryResponse doc comment in
 * types/index.ts for why this, not the search facet aggregation, is the
 * right source for names). Categories essentially never change at runtime,
 * so this polls far slower than anything else in the app -- 5 minutes is
 * "notice eventually if an admin adds one," not a freshness guarantee.
 */
export function useCategories() {
  const { data, error, isLoading } = useSWR<CategoryResponse[]>('/categories', fetcher, {
    refreshInterval: 300_000,
    revalidateOnFocus: false,
  });

  return { categories: data ?? [], isLoading, isError: Boolean(error) };
}

/**
 * PHASE 3B: GET /api/v1/simulator/events/recent, for the Operations page's
 * Live Transaction Feed. Polls every 2s per this task's own spec -- faster
 * than any other poll in the app, deliberately, since the whole point of
 * this widget is "watch the simulator work in close to real time." Unlike
 * useUnifiedAnalytics, there is no pause toggle here: the feed is only
 * ever rendered on the Operations page itself (not the Dashboard a user
 * might leave open unattended for a long session), so the same
 * cost-of-constant-polling concern that motivated the Dashboard's
 * play/pause control doesn't apply here the same way.
 */
export function useRecentSimEvents() {
  const { data, error, isLoading } = useSWR<RecentSimEvent[]>(
    '/simulator/events/recent',
    fetcher,
    { refreshInterval: 2000, revalidateOnFocus: false, keepPreviousData: true },
  );

  return { events: data ?? [], isLoading, isError: Boolean(error) };
}

// ---------------------------------------------------------------------------
// PHASE 2: hooks for the three new Phase 1 endpoints
// (AdvancedAnalyticsController.java). See types/index.ts for why these are
// "new read path, not new computation."
// ---------------------------------------------------------------------------

/** Builds a `?from=...&to=...` query string from two optional ISO instants, or ''. */
function rangeParams(from?: string, to?: string): string {
  const params = new URLSearchParams();
  if (from) params.set('from', from);
  if (to) params.set('to', to);
  const qs = params.toString();
  return qs ? `?${qs}` : '';
}

/**
 * GET /api/v1/analytics/funnel. Defaults to the backend's own default range
 * (last 30 days) when from/to are omitted. Polled less aggressively than
 * the unified feed (funnel buckets are daily, not streaming) but still live
 * enough that a day rolling over or a backfill shows up without a manual
 * refresh.
 */
export function useFunnel(from?: string, to?: string) {
  const { data, error, isLoading } = useSWR<FunnelAnalyticsRow[]>(
    `/analytics/funnel${rangeParams(from, to)}`,
    fetcher,
    { refreshInterval: 15_000, revalidateOnFocus: false, keepPreviousData: true },
  );

  return { rows: data ?? [], isLoading, isError: Boolean(error) };
}

/** GET /api/v1/analytics/order-value. Same [from, to) range semantics as useFunnel. */
export function useOrderValueSummary(from?: string, to?: string) {
  const { data, error, isLoading } = useSWR<OrderValueSummary>(
    `/analytics/order-value${rangeParams(from, to)}`,
    fetcher,
    { refreshInterval: 15_000, revalidateOnFocus: false, keepPreviousData: true },
  );

  return { summary: data, isLoading, isError: Boolean(error) };
}

/**
 * GET /api/v1/analytics/peak-hours. The underlying spark_batch_hourly_pattern
 * table only changes when the Spark batch job re-runs (minutes-to-hours
 * cadence, not streaming), so this polls far less often than the live
 * sections -- 60s is just "catch it eventually," not a freshness guarantee.
 */
export function usePeakHours() {
  const { data, error, isLoading } = useSWR<SparkSection<PeakHourRow>>(
    '/analytics/peak-hours',
    fetcher,
    { refreshInterval: 60_000, revalidateOnFocus: false, keepPreviousData: true },
  );

  return {
    section: data,
    rows: data?.rows ?? [],
    status: data?.meta.status,
    isLoading,
    isError: Boolean(error),
  };
}

/**
 * GET /api/v1/simulator/status. Used by OperationsPage to show live
 * running/pulseRunning state and action counts without the user having to
 * manually hit "refresh status" after every start/stop/pulse toggle.
 * `mutate` is returned so a POST handler can optimistically re-fetch right
 * after a start/stop call completes, instead of waiting for the next poll.
 */
export function useSimulatorStatus(pollMs = 4000) {
  const { data, error, isLoading, mutate } = useSWR<SimulatorStatus>(
    '/simulator/status',
    fetcher,
    { refreshInterval: pollMs, revalidateOnFocus: false },
  );

  return {
    status: data,
    isLoading,
    isError: Boolean(error),
    refresh: mutate,
  };
}
