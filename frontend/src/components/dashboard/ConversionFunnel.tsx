import { useMemo } from 'react';
import { ArrowDown } from 'lucide-react';
import type { FunnelAnalyticsRow } from '../../types';
import { formatCompactNumber } from '../../utils/formatters';

interface Stage {
  key: string;
  label: string;
  count: number;
  /** Rate from the PREVIOUS stage into this one; null for the first stage or when the previous stage's count is 0. */
  rateFromPrevious: number | null;
}

/**
 * GET /api/v1/analytics/funnel returns one row per UTC day over the
 * requested range. This widget shows one aggregate funnel for the whole
 * range (not a day picker), so the per-day counts are summed here --
 * that's a sum of counts, which is valid; the per-day *rates* the backend
 * computed are NOT averaged (averaging per-day percentages would weight a
 * low-traffic day the same as a high-traffic one), the rates shown below
 * are recomputed from the summed counts instead, same formula the backend
 * uses (null when the denominator is 0, not 0%).
 */
function aggregateStages(rows: FunnelAnalyticsRow[]): Stage[] {
  const totals = rows.reduce(
    (acc, row) => ({
      productViews: acc.productViews + row.productViews,
      cartItemAdded: acc.cartItemAdded + row.cartItemAdded,
      checkoutStarted: acc.checkoutStarted + row.checkoutStarted,
      ordersCreated: acc.ordersCreated + row.ordersCreated,
      ordersPaid: acc.ordersPaid + row.ordersPaid,
    }),
    { productViews: 0, cartItemAdded: 0, checkoutStarted: 0, ordersCreated: 0, ordersPaid: 0 },
  );

  const rate = (numerator: number, denominator: number): number | null =>
    denominator > 0 ? numerator / denominator : null;

  return [
    { key: 'views', label: 'Product views', count: totals.productViews, rateFromPrevious: null },
    {
      key: 'cart',
      label: 'Added to cart',
      count: totals.cartItemAdded,
      rateFromPrevious: rate(totals.cartItemAdded, totals.productViews),
    },
    {
      key: 'checkout',
      label: 'Checkout started',
      count: totals.checkoutStarted,
      rateFromPrevious: rate(totals.checkoutStarted, totals.cartItemAdded),
    },
    {
      key: 'ordered',
      label: 'Order created',
      count: totals.ordersCreated,
      rateFromPrevious: rate(totals.ordersCreated, totals.checkoutStarted),
    },
    {
      key: 'paid',
      label: 'Order paid',
      count: totals.ordersPaid,
      rateFromPrevious: rate(totals.ordersPaid, totals.ordersCreated),
    },
  ];
}

/**
 * Customer Conversion Funnel -- step-by-step drop-off from product view to
 * paid order, over GET /api/v1/analytics/funnel (new in the Phase 1
 * backend: FunnelAnalytics, already populated correctly by the Kafka
 * consumer pipeline -- this widget is just a new way to read it). Each
 * step's bar width is proportional to its share of the first stage's
 * count, and the connector between steps shows the conversion rate from
 * the stage above it -- "a dash" (not "0%") when there's no data to compute
 * that rate from yet.
 */
export function ConversionFunnel({ rows, isLoading }: { rows: FunnelAnalyticsRow[]; isLoading: boolean }) {
  const stages = useMemo(() => aggregateStages(rows), [rows]);
  const maxCount = stages[0]?.count ?? 0;

  if (isLoading && rows.length === 0) {
    return <div className="flex h-[260px] items-center justify-center text-sm text-slate-400">Loading...</div>;
  }
  if (maxCount === 0) {
    return (
      <div className="flex h-[260px] items-center justify-center text-sm text-slate-400">
        No funnel activity in this range yet.
      </div>
    );
  }

  return (
    <div className="space-y-1.5">
      {stages.map((stage, index) => {
        const widthPct = maxCount > 0 ? Math.max((stage.count / maxCount) * 100, 4) : 0;
        return (
          <div key={stage.key}>
            {index > 0 && (
              <div className="flex items-center gap-2 py-1 pl-1 text-xs text-slate-400">
                <ArrowDown className="h-3 w-3" />
                {stage.rateFromPrevious !== null ? (
                  <span>{(stage.rateFromPrevious * 100).toFixed(1)}% conversion</span>
                ) : (
                  <span>&ndash; no prior-stage data</span>
                )}
              </div>
            )}
            <div className="flex items-center gap-3">
              <div className="w-32 shrink-0 text-right text-xs font-medium text-slate-600 sm:w-36">
                {stage.label}
              </div>
              <div className="h-8 flex-1 rounded-md bg-slate-100">
                <div
                  className="flex h-8 items-center justify-end rounded-md bg-indigo-500 px-2.5 transition-all"
                  style={{ width: `${widthPct}%` }}
                >
                  <span className="text-xs font-semibold text-white">
                    {formatCompactNumber(stage.count)}
                  </span>
                </div>
              </div>
            </div>
          </div>
        );
      })}
    </div>
  );
}
