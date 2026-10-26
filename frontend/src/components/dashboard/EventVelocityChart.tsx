import { useMemo } from 'react';
import {
  CartesianGrid,
  Legend,
  Line,
  LineChart,
  ReferenceLine,
  ResponsiveContainer,
  Tooltip,
  XAxis,
  YAxis,
} from 'recharts';
import type { EventCountRow, SparkSection } from '../../types';
import { colorForKey } from '../../utils/chartColors';
import {
  formatCompactNumber,
  formatFullTimestamp,
  formatWindowTime,
  parseFlexibleTimestamp,
} from '../../utils/formatters';
import { SectionNotice } from './SectionNotice';

/** One point per window_start, one numeric field per event_type present in that window. */
type PivotedPoint = { window_start: string } & Record<string, number | string>;

/**
 * Pivots the raw spark_streaming_event_counts rows -- one row per
 * (window_start, event_type) pair -- into one point per window_start with a
 * numeric field per event_type, which is the shape Recharts' <Line> needs
 * (each series reads one fixed dataKey across the whole dataset). Also
 * returns the sorted list of distinct event_type values actually present,
 * so the chart can render exactly the series the data contains rather than
 * a hardcoded list that might not match what this backend is actually
 * publishing.
 */
function pivotByEventType(rows: EventCountRow[]): { points: PivotedPoint[]; eventTypes: string[] } {
  const eventTypes = Array.from(new Set(rows.map((r) => r.event_type))).sort();
  const byWindow = new Map<string, PivotedPoint>();

  for (const row of rows) {
    let point = byWindow.get(row.window_start);
    if (!point) {
      point = { window_start: row.window_start };
      for (const type of eventTypes) point[type] = 0;
      byWindow.set(row.window_start, point);
    }
    point[row.event_type] = row.event_count;
  }

  // Rows arrive newest-first (ORDER BY window_start DESC LIMIT) -- reverse to
  // chronological for a left-to-right chart, same convention as the other
  // time-series charts on this dashboard.
  //

  const points = Array.from(byWindow.values()).sort(
    (a, b) =>
      parseFlexibleTimestamp(a.window_start).getTime() - parseFlexibleTimestamp(b.window_start).getTime(),
  );

  return { points, eventTypes };
}

function VelocityTooltip({
  active,
  payload,
  label,
}: {
  active?: boolean;
  payload?: { value: number; dataKey: string; color: string }[];
  label?: string;
}) {
  if (!active || !payload?.length) return null;
  return (
    <div className="rounded-lg border border-slate-200 bg-white px-3 py-2 text-xs shadow-md">
      <p className="font-medium text-slate-900">{label ? formatFullTimestamp(label) : ''}</p>
      {payload.map((entry) => (
        <p key={entry.dataKey} className="tabular-nums" style={{ color: entry.color }}>
          {entry.dataKey}: {formatCompactNumber(entry.value)}
        </p>
      ))}
    </div>
  );
}

/**
 * The Spark streaming job's own watermark (SPARK_WATERMARK_DELAY, 2 min by
 * default) means this chart is legitimately blank for the first couple of
 * minutes after the dashboard loads or the job restarts 
 **/
function VelocitySkeleton() {
  return (
    <div className="flex h-[280px] flex-col justify-end gap-3 px-2 pb-6">
      <div className="flex items-end gap-2">
        {[40, 65, 50, 80, 55, 70, 45, 85, 60, 75, 50, 90].map((h, i) => (
          <div
            key={i}
            className="flex-1 animate-pulse rounded-t-sm bg-slate-100"
            style={{ height: `${h}%`, animationDelay: `${i * 60}ms` }}
          />
        ))}
      </div>
      <div className="flex items-center justify-center gap-2 pt-2">
        <span className="h-1.5 w-1.5 animate-pulse rounded-full bg-indigo-400" />
        <p className="text-xs text-slate-400">Synchronizing live stream...</p>
      </div>
    </div>
  );
}

/**
 * Real-Time Event Velocity -- multi-line chart over spark_streaming_event_counts
 * (StreamingAnalytics.eventCountsByWindow, 1-minute sliding windows). Series
 * are discovered dynamically from whatever `event_type` values are actually
 * present in the current response (CART_CREATED, CHECKOUT_STARTED,
 * ORDER_CREATED and anything else the event pipeline publishes)
 */
export function EventVelocityChart({
  section,
  isLoading,
  selectedWindowStart = null,
  onSelectWindow,
}: {
  section?: SparkSection<EventCountRow>;
  isLoading: boolean;
  /** The currently drilled-down window_start (raw backend string), or null. */
  selectedWindowStart?: string | null;
  /**
   *  fired with the clicked window's raw
   * `window_start` on click, or `null` to clear -- lifted to DashboardPage
   * so the same selection can also filter the Live Transaction Feed
   * sitting elsewhere on the page. Optional so this chart still works
   * standalone (e.g. in a future page) without a parent wiring it up.
   */
  onSelectWindow?: (windowStart: string | null) => void;
}) {
  const { points, eventTypes } = useMemo(() => pivotByEventType(section?.rows ?? []), [section]);

  function handleChartClick(state: { activeLabel?: string | number } | null) {
    if (!onSelectWindow || !state?.activeLabel) return;
    const clicked = String(state.activeLabel);
    onSelectWindow(clicked === selectedWindowStart ? null : clicked);
  }

  if (isLoading && !section) {
    return <VelocitySkeleton />;
  }
  if (section && section.meta.status !== 'OK') {
    return (
      <SectionNotice
        meta={section.meta}
        jobHint="Start spark-streaming (run-streaming.sh) to populate this."
      />
    );
  }
  if (points.length === 0) {
    // Legitimate watermark-delay gap (section reports OK, it's just that no
    // window has cleared the watermark yet) -- same skeleton as the initial
    // loading state, not an error/empty state, since from the user's POV
    // both just mean "still waiting on the first real data point."
    return <VelocitySkeleton />;
  }

  return (
    <div>
      {onSelectWindow && (
        <div className="mb-1.5 flex items-center justify-end">
          {selectedWindowStart ? (
            <button
              onClick={() => onSelectWindow(null)}
              className="flex items-center gap-1 rounded-full border border-indigo-200 bg-indigo-50 px-2.5 py-1 text-xs font-medium text-indigo-700 hover:bg-indigo-100 dark:border-indigo-800 dark:bg-indigo-900/30 dark:text-indigo-300"
            >
              Filtered to {formatWindowTime(selectedWindowStart)} window
              <span aria-hidden="true">&times;</span>
            </button>
          ) : (
            <p className="text-xs text-slate-400 dark:text-gray-500">Click a point to filter the feed to that minute</p>
          )}
        </div>
      )}
      <ResponsiveContainer width="100%" height={280}>
        <LineChart
          data={points}
          margin={{ top: 4, right: 16 }}
          onClick={handleChartClick}
          className={onSelectWindow ? 'cursor-pointer' : undefined}
        >
        <CartesianGrid vertical={false} stroke="#f3f4f6" strokeDasharray="4 4" />
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
          width={40}
        />
        <Tooltip content={<VelocityTooltip />} />
        {selectedWindowStart && points.some((p) => p.window_start === selectedWindowStart) && (
          <ReferenceLine
            x={selectedWindowStart}
            stroke="#6366f1"
            strokeWidth={2}
            strokeDasharray="3 3"
            ifOverflow="extendDomain"
          />
        )}
        <Legend
          wrapperStyle={{ fontSize: 12 }}
          formatter={(value) => <span className="text-slate-600">{value}</span>}
        />
        {eventTypes.map((type) => (
          <Line
            key={type}
            type="monotone"
            dataKey={type}
            name={type}
            stroke={colorForKey(type, eventTypes)}
            strokeWidth={2}
            dot={false}
            isAnimationActive={false}
          />
        ))}
        </LineChart>
      </ResponsiveContainer>
    </div>
  );
}
