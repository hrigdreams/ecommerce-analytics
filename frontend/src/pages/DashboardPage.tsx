import { useMemo, useState } from 'react';
import { Link } from 'react-router-dom';
import {
  Area,
  Bar,
  BarChart,
  CartesianGrid,
  ComposedChart,
  Line,
  ResponsiveContainer,
  Tooltip,
  XAxis,
  YAxis,
} from 'recharts';
import type { ReactNode } from 'react';
import {
  TrendingUp,
  Radio,
  Users,
  UserCheck,
  Tag,
  Receipt,
  PieChart as PieChartIcon,
  Globe2,
  Activity,
  Filter,
  Clock,
  Table2,
  Terminal,
  DollarSign,
  ShoppingCart,
  HeartPulse,
  ArrowUpRight,
  ArrowDownRight,
  Minus,
  X,
} from 'lucide-react';
import {
  useFunnel,
  useOrderValueSummary,
  usePeakHours,
  useRecentSimEvents,
  useUnifiedAnalytics,
} from '../api/hooks';
import { Card, CardBody, CardHeader } from '../components/ui/Card';
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeaderCell,
  TableRow,
} from '../components/ui/Table';
import { CategoryDonutChart } from '../components/dashboard/CategoryDonutChart';
import { RegionalSalesChart } from '../components/dashboard/RegionalSalesChart';
import { EventVelocityChart } from '../components/dashboard/EventVelocityChart';
import { ConversionFunnel } from '../components/dashboard/ConversionFunnel';
import { PeakHoursChart } from '../components/dashboard/PeakHoursChart';
import { CategoryDeepDiveTable } from '../components/dashboard/CategoryDeepDiveTable';
import { GeoLogisticsGrid } from '../components/dashboard/GeoLogisticsGrid';
import { SectionNotice } from '../components/dashboard/SectionNotice';
import { DateRangeControl, buildPresetRange, type DateRange } from '../components/dashboard/DateRangeControl';
import { LivePollingToggle } from '../components/dashboard/LivePollingToggle';
import { LiveTransactionFeed } from '../components/operations/LiveTransactionFeed';
import {
  formatCompactNumber,
  formatCurrency,
  formatDayLabel,
  formatFullTimestamp,
  formatWindowTime,
  parseFlexibleTimestamp,
} from '../utils/formatters';
import { cn } from '../lib/utils';
import type { PaidRevenueRow, ProductPerformanceRow, SparkSection, UnifiedAnalyticsResponse } from '../types';


type TrendDirection = 'up' | 'down' | 'flat';

function computeTrend(
  current: number | null,
  previous: number | null,
): { direction: TrendDirection; pct: number } | null {
  if (current === null || previous === null) return null;
  if (previous === 0) {
    return current === 0 ? { direction: 'flat', pct: 0 } : { direction: 'up', pct: 100 };
  }
  const pct = ((current - previous) / previous) * 100;
  if (Math.abs(pct) < 0.5) return { direction: 'flat', pct };
  return { direction: pct > 0 ? 'up' : 'down', pct };
}

function TrendBadge({ trend }: { trend: ReturnType<typeof computeTrend> }) {
  if (!trend) return null;
  const Icon = trend.direction === 'up' ? ArrowUpRight : trend.direction === 'down' ? ArrowDownRight : Minus;
  const color =
    trend.direction === 'up'
      ? 'text-emerald-600'
      : trend.direction === 'down'
        ? 'text-rose-600'
        : 'text-slate-400';
  return (
    <span className={cn('inline-flex items-center gap-0.5 text-xs font-semibold tabular-nums', color)}>
      <Icon className="h-3.5 w-3.5" />
      {Math.abs(trend.pct).toFixed(1)}%
    </span>
  );
}

/**
 * "System Health" is a derived, client-side metric -- there's no dedicated
 * backend health endpoint, so this counts how many of the response's eight
 * independently-sourced sections (Spring's customerSummary + the seven
 * Spark batch/streaming sections) report meta.status === 'OK'. Anything
 * less than all-OK means at least one upstream job (Spark batch or
 * streaming) isn't currently populating its table -- which SectionNotice
 * already explains per-chart; this just rolls it into one ribbon number.
 */
function computeSystemHealth(analytics?: UnifiedAnalyticsResponse): { okCount: number; total: number } | null {
  if (!analytics) return null;
  const statuses = [
    analytics.customerSummary.meta.status,
    analytics.revenueTrend.meta.status,
    analytics.productPerformance.meta.status,
    analytics.categoryPerformance.meta.status,
    analytics.geoPerformance.meta.status,
    analytics.liveEventCounts.meta.status,
    analytics.liveOrderActivity.meta.status,
    analytics.livePaidRevenue.meta.status,
  ];
  return { okCount: statuses.filter((s) => s === 'OK').length, total: statuses.length };
}

function ChartTooltip({
  active,
  payload,
  labelFormatter,
  valueKey,
}: {
  active?: boolean;
  payload?: { value: number; payload: Record<string, unknown> }[];
  labelFormatter: (point: Record<string, unknown>) => string;
  valueKey: string;
}) {
  if (!active || !payload?.length) return null;
  const point = payload[0].payload;
  return (
    <div className="rounded-md border border-slate-200 bg-white px-3 py-2 text-xs shadow-sm dark:border-gray-700 dark:bg-gray-800">
      <p className="font-medium text-slate-900 dark:text-white">{labelFormatter(point)}</p>
      <p className="tabular-nums text-slate-500 dark:text-gray-400">{formatCurrency(Number(point[valueKey]))}</p>
    </div>
  );
}

/**
 * Dedicated tooltip for the hero Live revenue stream chart -- distinct from
 * the generic ChartTooltip above because this chart's data carries two
 * series (actual `paid_revenue` and the item-6 forecast's `projected`) and
 * needs to label a hovered future point as a projection rather than a real
 * reading.
 */
function RevenueTooltip({
  active,
  payload,
  label,
}: {
  active?: boolean;
  payload?: { value: number | null; dataKey: string; payload: Record<string, unknown> }[];
  label?: string;
}) {
  if (!active || !payload?.length) return null;
  const point = payload[0].payload as { paid_revenue: number | null; projected: number | null };
  const isProjected = point.paid_revenue === null && point.projected !== null;
  return (
    <div className="rounded-md border border-slate-200 bg-white px-3 py-2 text-xs shadow-sm dark:border-gray-700 dark:bg-gray-800">
      <p className="font-medium text-slate-900 dark:text-white">{label ? formatFullTimestamp(label) : ''}</p>
      {isProjected ? (
        <p className="tabular-nums text-indigo-500 dark:text-indigo-400">
          {formatCurrency(point.projected ?? 0)} <span className="text-slate-400 dark:text-gray-500">(projected, SMA-5)</span>
        </p>
      ) : (
        <p className="tabular-nums text-slate-500 dark:text-gray-400">{formatCurrency(point.paid_revenue ?? 0)}</p>
      )}
    </div>
  );
}

/** Spark rows come back newest-first (ORDER BY ... DESC LIMIT); charts need oldest-first, left to right. */
function chronological<T>(rows: T[]): T[] {
  return [...rows].reverse();
}

/** One point on the Live revenue stream chart, actual or projected. */
type RevenuePoint = { window_start: string; paid_revenue: number | null; projected: number | null };

/**
 *  lightweight client-side forecast -- a simple
 * moving average (SMA) of the last 5 sliding windows (or fewer, if fewer
 * than 5 are in hand yet), projected flat across 3 future windows spaced at
 * the same interval as the real data. Deliberately not a regression/trend
 * line: the spec calls for an SMA specifically, and a flat SMA projection
 * is an honest "where recent activity has been centered"
 *
 * The projected series starts at the *last actual value* (not the SMA
 * level) for one point, purely so the dashed line visually connects to
 * where the solid line stops instead of jumping -- the SMA level itself is
 * the flat line after that bridge point.
 */
function buildRevenueWithForecast(rows: PaidRevenueRow[]): { data: RevenuePoint[]; hasForecast: boolean } {
  const actual = chronological(rows);
  if (actual.length < 2) {
    return { data: actual.map((r) => ({ window_start: r.window_start, paid_revenue: r.paid_revenue, projected: null })), hasForecast: false };
  }

  const last5 = actual.slice(-5);
  const sma = last5.reduce((sum, r) => sum + r.paid_revenue, 0) / last5.length;

  // Average spacing between the last few windows, so projected points land
  // on the same cadence as real data instead of an arbitrary guessed interval.
  const recentForSpacing = actual.slice(-Math.min(5, actual.length));
  let totalGapMs = 0;
  for (let i = 1; i < recentForSpacing.length; i++) {
    totalGapMs +=
      parseFlexibleTimestamp(recentForSpacing[i].window_start).getTime() -
      parseFlexibleTimestamp(recentForSpacing[i - 1].window_start).getTime();
  }
  const avgGapMs = recentForSpacing.length > 1 ? totalGapMs / (recentForSpacing.length - 1) : 60_000;

  const lastActual = actual[actual.length - 1];
  const lastTimeMs = parseFlexibleTimestamp(lastActual.window_start).getTime();

  const data: RevenuePoint[] = actual.map((r, i) => ({
    window_start: r.window_start,
    paid_revenue: r.paid_revenue,
    // Bridge point: the last actual point also carries the forecast series'
    // starting value, so the dashed line picks up exactly where the solid
    // area chart stops rather than leaving a visible gap.
    projected: i === actual.length - 1 ? r.paid_revenue : null,
  }));

  const FORECAST_POINTS = 3;
  for (let i = 1; i <= FORECAST_POINTS; i++) {
    data.push({
      window_start: new Date(lastTimeMs + avgGapMs * i).toISOString(),
      paid_revenue: null,
      projected: sma,
    });
  }

  return { data, hasForecast: true };
}

function StatTile({
  icon,
  label,
  value,
  trend,
  iconClassName,
}: {
  icon: ReactNode;
  label: string;
  value: string;
  trend?: ReactNode;
  iconClassName?: string;
}) {
  return (
    <Card>
      <CardBody className="flex items-center gap-3">
        <div
          className={cn(
            'flex h-9 w-9 shrink-0 items-center justify-center rounded-md bg-slate-100 text-slate-500 dark:bg-gray-800 dark:text-gray-400',
            iconClassName,
          )}
        >
          {icon}
        </div>
        <div className="min-w-0 flex-1">
          <p className="text-xs text-slate-500 dark:text-gray-400">{label}</p>
          <div className="flex items-baseline gap-2">
            <p className="truncate font-mono text-lg font-semibold tabular-nums text-slate-900 dark:text-white">
              {value}
            </p>
            {trend}
          </div>
        </div>
      </CardBody>
    </Card>
  );
}

export function DashboardPage() {
  //  polling toggle -- when paused, useUnifiedAnalytics stops
  // re-fetching but keeps the last frame rendered. 
  const [live, setLive] = useState(true);
  const { analytics, isLoading, isError } = useUnifiedAnalytics(live);

  // date range governs the two Phase-1-backed widgets that accept
  // an explicit [from, to) range (funnel, AOV) -- see DateRangeControl doc
  // comment for why this isn't a free-form calendar picker.
  const [range, setRange] = useState<DateRange>(() => buildPresetRange(30));
  const { rows: funnelRows, isLoading: funnelLoading } = useFunnel(range.from, range.to);
  const { summary: orderValueSummary } = useOrderValueSummary(range.from, range.to);
  const { rows: peakHourRows, status: peakHourStatus, isLoading: peakHoursLoading } = usePeakHours();

  // raw scrolling feed for the new Z-pattern row,
  
  const { events: recentEvents, isLoading: eventsLoading } = useRecentSimEvents();

  //  click-to-filter -- selecting a slice in the Category Revenue
  // Share donut filters the "Top products by revenue" table below to that
  // category.

  const [selectedCategoryId, setSelectedCategoryId] = useState<number | null>(null);
  const selectedCategoryName = useMemo(() => {
    if (selectedCategoryId === null) return null;
    return (
      analytics?.categoryPerformance.rows.find((r) => r.category_id === selectedCategoryId)
        ?.category_name ?? null
    );
  }, [analytics, selectedCategoryId]);

  // same click-to-filter pattern, for city instead of category --
  // selecting a bar in the Regional Sales Ranking chart (or a row in the
  // new Geo-Logistics Grid) filters the other to that one city. Keyed on
  // `city|country` since city names aren't unique across countries.
  const [selectedCityKey, setSelectedCityKey] = useState<string | null>(null);
  const selectedCityLabel = useMemo(() => {
    if (selectedCityKey === null) return null;
    const [city, country] = selectedCityKey.split('|');
    return `${city}, ${country}`;
  }, [selectedCityKey]);

  // item 5: click-to-filter drill-down -- clicking a
  // point on Real-time Event Velocity sets this to that window's raw
  // window_start, which the Live Transaction Feed (in the Z-pattern row
  // above) uses to narrow itself to that one minute's raw events.
  const [selectedVelocityWindow, setSelectedVelocityWindow] = useState<string | null>(null);

  // item 6: SMA-5 forecast appended to the hero chart's
  // own data -- see buildRevenueWithForecast's doc comment. Computed here
  // (before the isError early return below) so this hook always runs in
  // the same order on every render, per the Rules of Hooks.
  const { data: revenueWithForecast, hasForecast } = useMemo(
    () => buildRevenueWithForecast(analytics?.livePaidRevenue.rows ?? []),
    [analytics],
  );

  if (isError) {
    return (
      <Card accent="rose">
        <CardBody>
          <p className="text-sm text-slate-700 dark:text-gray-300">
            Couldn't reach the analytics backend. Confirm the Spring Boot API is running on{' '}
            <code className="rounded bg-slate-100 px-1 py-0.5 font-mono text-xs dark:bg-gray-800 dark:text-gray-200">
              localhost:8080
            </code>{' '}
            and retry.
          </p>
        </CardBody>
      </Card>
    );
  }

  const latestOrderWindow = analytics?.liveOrderActivity.rows[0];
  const previousOrderWindow = analytics?.liveOrderActivity.rows[1];
  const ordersTrend = computeTrend(
    latestOrderWindow ? latestOrderWindow.orders_created : null,
    previousOrderWindow ? previousOrderWindow.orders_created : null,
  );

  // Revenue (24h): sum of every sliding window currently returned by
  // spark_streaming_revenue (that endpoint's own lookback, not a fixed
  // literal 24h on the client) -- the closest "recent total" this response
  // shape supports. Trend compares the two most recent windows directly
  // (latest vs. previous paid_revenue), since summing-so-far isn't a
  // meaningful window-over-window comparison.
  const revenueRows = analytics?.livePaidRevenue.rows ?? [];
  const revenueTotal = revenueRows.length > 0 ? revenueRows.reduce((sum, r) => sum + r.paid_revenue, 0) : null;
  const revenueTrend = computeTrend(
    revenueRows[0] ? revenueRows[0].paid_revenue : null,
    revenueRows[1] ? revenueRows[1].paid_revenue : null,
  );

  const systemHealth = computeSystemHealth(analytics);

  return (
    <div className="space-y-6">
      {/* Controls bar */}
      <div className="flex flex-wrap items-center justify-between gap-3">
        <DateRangeControl value={range} onChange={setRange} />
        <LivePollingToggle live={live} onChange={setLive} />
      </div>

      {/* KPI ribbon -- 4 headline metrics with
          trend indicators, sitting directly above everything else
          (including the existing 5-tile detail row below it, which is
          kept rather than replaced: it covers different ground --
          customer/category counts and the selected date-range AOV --
          that doesn't fit this ribbon's "live, at-a-glance" framing). */}
      <div className="grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-4">
        <StatTile
          icon={<DollarSign className="h-4 w-4" />}
          iconClassName="bg-emerald-50 text-emerald-600"
          label="Revenue (live windows)"
          value={revenueTotal !== null ? formatCurrency(revenueTotal) : '—'}
          trend={<TrendBadge trend={revenueTrend} />}
        />
        <StatTile
          icon={<ShoppingCart className="h-4 w-4" />}
          iconClassName="bg-indigo-50 text-indigo-600"
          label="Active orders (last window)"
          value={latestOrderWindow ? formatCompactNumber(latestOrderWindow.orders_created) : '—'}
          trend={<TrendBadge trend={ordersTrend} />}
        />
        <StatTile
          icon={<Receipt className="h-4 w-4" />}
          iconClassName="bg-amber-50 text-amber-600"
          label={`Avg order value (${range.label})`}
          value={orderValueSummary ? formatCurrency(orderValueSummary.averageOrderValue) : '—'}
        />
        <StatTile
          icon={<HeartPulse className="h-4 w-4" />}
          iconClassName={cn(
            systemHealth && systemHealth.okCount < systemHealth.total ? 'bg-amber-50 text-amber-600' : 'bg-emerald-50 text-emerald-600',
          )}
          label="System health"
          value={systemHealth ? `${systemHealth.okCount}/${systemHealth.total} OK` : '—'}
          trend={
            systemHealth && (
              <span
                className={cn(
                  'h-2 w-2 rounded-full',
                  systemHealth.okCount === systemHealth.total ? 'bg-emerald-500' : 'bg-amber-500',
                )}
              />
            )
          }
        />
      </div>

      {/* KPI row (detail) */}
      <div className="grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-5">
        <StatTile
          icon={<Users className="h-4 w-4" />}
          label="Customers"
          value={analytics ? formatCompactNumber(analytics.customerSummary.totalCustomers) : '—'}
        />
        <StatTile
          icon={<UserCheck className="h-4 w-4" />}
          label="With paid orders"
          value={
            analytics ? formatCompactNumber(analytics.customerSummary.customersWithPaidOrders) : '—'
          }
        />
        <StatTile
          icon={<Tag className="h-4 w-4" />}
          label="Categories"
          value={analytics ? formatCompactNumber(analytics.customerSummary.totalCategories) : '—'}
        />
        <StatTile
          icon={<Radio className="h-4 w-4" />}
          label="Orders (last window)"
          value={latestOrderWindow ? formatCompactNumber(latestOrderWindow.orders_created) : '—'}
        />
        <StatTile
          icon={<Receipt className="h-4 w-4" />}
          label={`Avg order value (${range.label})`}
          value={orderValueSummary ? formatCurrency(orderValueSummary.averageOrderValue) : '—'}
        />
      </div>

      {/*  live revenue stream, full-width and taller than the
          other charts. This used to sit in a 2-col grid next to Historical
          trend, which compressed its x-axis enough to make a windowed
          revenue trend genuinely hard to read -- the whole point of this
          chart is inspecting the shape of recent sliding windows, which
          needs horizontal room. Given its own row and more height
          (340 vs the usual 260) since it's now the dashboard's lead
          visual. */}
      <Card accent="emerald">
        <CardHeader
          title="Live revenue stream"
          description="Spark streaming, paid revenue per sliding window (spark_streaming_revenue)"
          action={
            <div className="flex items-center gap-3">
              {hasForecast && (
                <span className="hidden items-center gap-1.5 text-xs text-slate-400 sm:flex dark:text-gray-500">
                  <svg width="16" height="2" aria-hidden="true">
                    <line x1="0" y1="1" x2="16" y2="1" stroke="#6366f1" strokeWidth="2" strokeDasharray="3 2" />
                  </svg>
                  Projected (SMA-5)
                </span>
              )}
              <Radio className="h-4 w-4 text-emerald-500" />
            </div>
          }
        />
        <CardBody>
          {isLoading && !analytics ? (
            <ChartSkeleton />
          ) : analytics && analytics.livePaidRevenue.meta.status !== 'OK' ? (
            <SectionNotice
              meta={analytics.livePaidRevenue.meta}
              jobHint="Start spark-streaming (run-streaming.sh) to populate this."
            />
          ) : (
            <ResponsiveContainer width="100%" height={340}>
              <ComposedChart data={revenueWithForecast}>
                <defs>
                  <linearGradient id="liveRevenueGradient" x1="0" y1="0" x2="0" y2="1">
                    <stop offset="0%" stopColor="#10b981" stopOpacity={0.35} />
                    <stop offset="100%" stopColor="#10b981" stopOpacity={0} />
                  </linearGradient>
                </defs>
                <CartesianGrid vertical={false} stroke="#e2e8f0" />
                <XAxis
                  dataKey="window_start"
                  tickFormatter={formatWindowTime}
                  tick={{ fontSize: 11, fill: '#64748b' }}
                  axisLine={{ stroke: '#e2e8f0' }}
                  tickLine={false}
                />
                <YAxis
                  tickFormatter={(v) => formatCompactNumber(v)}
                  tick={{ fontSize: 11, fill: '#64748b' }}
                  axisLine={false}
                  tickLine={false}
                  width={44}
                />
                <Tooltip content={<RevenueTooltip />} />
                <Area
                  type="monotone"
                  dataKey="paid_revenue"
                  stroke="#10b981"
                  strokeWidth={2}
                  fill="url(#liveRevenueGradient)"
                  isAnimationActive={false}
                />
                {hasForecast && (
                  <Line
                    type="monotone"
                    dataKey="projected"
                    stroke="#6366f1"
                    strokeWidth={2}
                    strokeOpacity={0.5}
                    strokeDasharray="5 4"
                    dot={false}
                    activeDot={{ r: 3 }}
                    connectNulls
                    isAnimationActive={false}
                  />
                )}
              </ComposedChart>
            </ResponsiveContainer>
          )}
        </CardBody>
      </Card>

      {/*  Z-pattern row directly under the hero --
          aggregated "Top products" on the left, raw scrolling
          "Live transaction feed" on the right. TopProductsTable moved up
          from the bottom of the page (not duplicated) to sit beside the
          feed, per the requested left-aggregate / right-raw pairing. */}
      <div className="grid grid-cols-1 gap-6 lg:grid-cols-2">
        <Card accent="indigo">
          <CardHeader
            title="Top products by revenue"
            description="Spark batch, PAID orders only (spark_batch_product_performance) -- not live Elasticsearch ranking signals, see Catalog for those"
            action={
              selectedCategoryId !== null && (
                <div className="flex items-center gap-2">
                  <Link
                    to={`/catalog?categoryId=${selectedCategoryId}`}
                    className="rounded-full border border-slate-200 bg-white px-2.5 py-1 text-xs font-medium text-slate-600 hover:border-slate-300"
                  >
                    View in catalog
                  </Link>
                  <button
                    onClick={() => setSelectedCategoryId(null)}
                    className="flex items-center gap-1 rounded-full border border-indigo-200 bg-indigo-50 px-2.5 py-1 text-xs font-medium text-indigo-700 hover:bg-indigo-100"
                  >
                    {selectedCategoryName ?? `Category ${selectedCategoryId}`}
                    <X className="h-3 w-3" />
                  </button>
                </div>
              )
            }
          />
          <CardBody className="p-0">
            <TopProductsTable
              section={analytics?.productPerformance}
              isLoading={isLoading}
              categoryFilter={selectedCategoryId}
            />
          </CardBody>
        </Card>

        <Card accent="slate">
          <CardHeader
            title="Live transaction feed"
            description="Simulator events as they happen (GET /simulator/events/recent, ~2s poll)"
            action={<Terminal className="h-4 w-4 text-slate-400" />}
          />
          <CardBody>
            <LiveTransactionFeed
              events={recentEvents}
              isLoading={eventsLoading}
              filterWindowStart={selectedVelocityWindow}
              onClearFilter={() => setSelectedVelocityWindow(null)}
            />
          </CardBody>
        </Card>
      </div>

      {/*  historical trend + real-time event velocity -- re-paired
          here once Live revenue stream moved out to its own hero row above
          (Event velocity was already a standalone full-width card; pairing
          these two similarly-sized trend charts together avoids leaving a
          half-empty grid row where Historical trend used to sit alone). */}
      <div className="grid grid-cols-1 gap-6 lg:grid-cols-2">
        <Card accent="slate">
          <CardHeader
            title="Historical trend"
            description="Spark batch, daily revenue (spark_batch_revenue_trend) -- now includes today's streamed activity (Phase 1, item 4)"
            action={<TrendingUp className="h-4 w-4 text-slate-400" />}
          />
          <CardBody>
            {isLoading && !analytics ? (
              <ChartSkeleton />
            ) : analytics && analytics.revenueTrend.meta.status !== 'OK' ? (
              <SectionNotice
                meta={analytics.revenueTrend.meta}
                jobHint="Run the Spark batch job (run-batch.sh) to populate this."
              />
            ) : (
              <ResponsiveContainer width="100%" height={260}>
                <BarChart data={chronological(analytics?.revenueTrend.rows ?? [])}>
                  <CartesianGrid vertical={false} stroke="#e2e8f0" />
                  <XAxis
                    dataKey="day"
                    tickFormatter={formatDayLabel}
                    tick={{ fontSize: 11, fill: '#64748b' }}
                    axisLine={{ stroke: '#e2e8f0' }}
                    tickLine={false}
                  />
                  <YAxis
                    tickFormatter={(v) => formatCompactNumber(v)}
                    tick={{ fontSize: 11, fill: '#64748b' }}
                    axisLine={false}
                    tickLine={false}
                    width={44}
                  />
                  <Tooltip
                    content={
                      <ChartTooltip
                        valueKey="revenue"
                        labelFormatter={(p) => (typeof p.day === 'string' ? formatDayLabel(p.day) : '')}
                      />
                    }
                    cursor={{ fill: '#f1f5f9' }}
                  />
                  <Bar dataKey="revenue" fill="#475569" radius={[4, 4, 0, 0]} maxBarSize={36} />
                </BarChart>
              </ResponsiveContainer>
            )}
          </CardBody>
        </Card>

        <Card accent="indigo">
          <CardHeader
            title="Real-time event velocity"
            description="Spark streaming, concurrent event types per sliding window (spark_streaming_event_counts)"
            action={<Activity className="h-4 w-4 text-indigo-500" />}
          />
          <CardBody>
            <EventVelocityChart
              section={analytics?.liveEventCounts}
              isLoading={isLoading}
              selectedWindowStart={selectedVelocityWindow}
              onSelectWindow={setSelectedVelocityWindow}
            />
          </CardBody>
        </Card>
      </div>

      {/* Row 2: category donut + regional ranking (new, Phase 2) */}
      <div className="grid grid-cols-1 gap-6 lg:grid-cols-2">
        <Card accent="indigo">
          <CardHeader
            title="Category revenue share"
            description="Spark batch, revenue distribution across all 15 categories (spark_batch_category_performance) -- click a slice to filter the table below"
            action={<PieChartIcon className="h-4 w-4 text-indigo-500" />}
          />
          <CardBody>
            <CategoryDonutChart
              section={analytics?.categoryPerformance}
              isLoading={isLoading}
              selectedCategoryId={selectedCategoryId}
              onSelectCategory={setSelectedCategoryId}
            />
          </CardBody>
        </Card>

        <Card accent="amber">
          <CardHeader
            title="Regional sales ranking"
            description="Spark batch, top cities by revenue or order count (spark_batch_geo_performance)"
            action={<Globe2 className="h-4 w-4 text-amber-500" />}
          />
          <CardBody>
            <RegionalSalesChart
              section={analytics?.geoPerformance}
              isLoading={isLoading}
              selectedCityKey={selectedCityKey}
              onSelectCity={setSelectedCityKey}
            />
          </CardBody>
        </Card>
      </div>

      {/* Category Deep-Dive + Geo-Logistics data tables (new, Phase 3).
          Placed directly below the donut/regional charts they're the
          master/detail counterpart to -- clicking a slice or a bar above
          filters the table on the same side below it, and vice versa. */}
      <div className="grid grid-cols-1 gap-6 lg:grid-cols-2">
        <Card accent="indigo">
          <CardHeader
            title="Category deep-dive"
            description="All 15 categories -- revenue share, revenue, and order count (order count derived from spark_batch_product_performance, see component doc)"
            action={
              <div className="flex items-center gap-2">
                <Table2 className="h-4 w-4 text-indigo-500" />
                {selectedCategoryId !== null && (
                  <button
                    onClick={() => setSelectedCategoryId(null)}
                    className="flex items-center gap-1 rounded-full border border-indigo-200 bg-indigo-50 px-2.5 py-1 text-xs font-medium text-indigo-700 hover:bg-indigo-100"
                  >
                    {selectedCategoryName ?? `Category ${selectedCategoryId}`}
                    <X className="h-3 w-3" />
                  </button>
                )}
              </div>
            }
          />
          <CardBody className="p-0">
            <CategoryDeepDiveTable
              categorySection={analytics?.categoryPerformance}
              productSection={analytics?.productPerformance}
              isLoading={isLoading}
              selectedCategoryId={selectedCategoryId}
              onSelectCategory={setSelectedCategoryId}
            />
          </CardBody>
        </Card>

        <Card accent="amber">
          <CardHeader
            title="Geo-logistics grid"
            description="Every city, not just the chart's top 8 -- AOV = paid revenue / paid orders (see component doc)"
            action={
              <div className="flex items-center gap-2">
                <Table2 className="h-4 w-4 text-amber-500" />
                {selectedCityKey !== null && (
                  <button
                    onClick={() => setSelectedCityKey(null)}
                    className="flex items-center gap-1 rounded-full border border-amber-200 bg-amber-50 px-2.5 py-1 text-xs font-medium text-amber-700 hover:bg-amber-100"
                  >
                    {selectedCityLabel}
                    <X className="h-3 w-3" />
                  </button>
                )}
              </div>
            }
          />
          <CardBody className="p-0">
            <GeoLogisticsGrid
              section={analytics?.geoPerformance}
              isLoading={isLoading}
              selectedCityKey={selectedCityKey}
              onSelectCity={setSelectedCityKey}
            />
          </CardBody>
        </Card>
      </div>

      {/* Row 3: conversion funnel + peak hours (new, Phase 2) */}
      <div className="grid grid-cols-1 gap-6 lg:grid-cols-2">
        <Card accent="slate">
          <CardHeader
            title="Customer conversion funnel"
            description={`Views → paid orders, ${range.label} range (GET /analytics/funnel)`}
            action={<Filter className="h-4 w-4 text-slate-400" />}
          />
          <CardBody>
            <ConversionFunnel rows={funnelRows} isLoading={funnelLoading} />
          </CardBody>
        </Card>

        <Card accent="slate">
          <CardHeader
            title="Peak shopping hours"
            description="Spark batch, orders by hour of day (spark_batch_hourly_pattern)"
            action={<Clock className="h-4 w-4 text-slate-400" />}
          />
          <CardBody>
            <PeakHoursChart rows={peakHourRows} status={peakHourStatus} isLoading={peakHoursLoading} />
          </CardBody>
        </Card>
      </div>

    </div>
  );
}

function TopProductsTable({
  section,
  isLoading,
  categoryFilter,
}: {
  section?: SparkSection<ProductPerformanceRow>;
  isLoading: boolean;
  categoryFilter: number | null;
}) {
  if (isLoading && !section) {
    return <div className="p-8 text-center text-sm text-slate-400">Loading top products...</div>;
  }
  if (section && section.meta.status !== 'OK') {
    return (
      <SectionNotice
        meta={section.meta}
        jobHint="Run the Spark batch job (run-batch.sh) to populate this."
      />
    );
  }

  const rows =
    categoryFilter !== null
      ? (section?.rows ?? []).filter((r) => r.category_id === categoryFilter)
      : section?.rows ?? [];

  if (rows.length === 0) {
    return (
      <div className="p-8 text-center text-sm text-slate-400">
        No products in this category among the current top performers.
      </div>
    );
  }

  return (
    <Table>
      <TableHead>
        <TableRow>
          <TableHeaderCell>Product</TableHeaderCell>
          <TableHeaderCell className="text-right">Units sold</TableHeaderCell>
          <TableHeaderCell className="text-right">Orders</TableHeaderCell>
          <TableHeaderCell className="text-right">Revenue</TableHeaderCell>
        </TableRow>
      </TableHead>
      <TableBody>
        {rows.map((row) => (
          <TableRow key={row.product_id}>
            <TableCell className="font-medium text-slate-900">{row.product_name}</TableCell>
            <TableCell className="text-right font-mono text-xs tabular-nums text-slate-600">
              {formatCompactNumber(row.units_sold)}
            </TableCell>
            <TableCell className="text-right font-mono text-xs tabular-nums text-slate-600">
              {formatCompactNumber(row.order_count)}
            </TableCell>
            <TableCell className="text-right font-mono text-xs tabular-nums text-slate-600">
              {formatCurrency(row.revenue)}
            </TableCell>
          </TableRow>
        ))}
      </TableBody>
    </Table>
  );
}

function ChartSkeleton() {
  return <div className="h-[260px] animate-pulse rounded-md bg-slate-100 dark:bg-gray-800" />;
}
