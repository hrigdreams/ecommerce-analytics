import { useState } from 'react';
import { Bar, BarChart, CartesianGrid, Cell, ResponsiveContainer, Tooltip, XAxis, YAxis } from 'recharts';
import type { GeoPerformanceRow, SparkSection } from '../../types';
import { colorForIndex } from '../../utils/chartColors';
import { formatCompactNumber, formatCurrency } from '../../utils/formatters';
import { SectionNotice } from './SectionNotice';
import { cn } from '../../lib/utils';

type Metric = 'revenue' | 'order_count';

const METRIC_LABEL: Record<Metric, string> = {
  revenue: 'Revenue',
  order_count: 'Orders',
};

function RegionalTooltip({
  active,
  payload,
  metric,
}: {
  active?: boolean;
  payload?: { payload: GeoPerformanceRow }[];
  metric: Metric;
}) {
  if (!active || !payload?.length) return null;
  const row = payload[0].payload;
  return (
    <div className="rounded-md border border-slate-200 bg-white px-3 py-2 text-xs shadow-sm">
      <p className="font-medium text-slate-900">
        {row.city}, {row.country}
      </p>
      <p className="text-slate-500">{formatCurrency(row.revenue)} revenue</p>
      <p className="text-slate-500">{formatCompactNumber(row.order_count)} orders</p>
      <p className="text-slate-400">{formatCompactNumber(row.paid_order_count)} paid</p>
      {metric === 'order_count' && (
        <p className="mt-1 text-slate-400">Ranked by order count</p>
      )}
    </div>
  );
}

/** Matches GeoLogisticsGrid's row identity so a bar click and a grid-row click select the same thing. */
function cityKey(row: GeoPerformanceRow): string {
  return `${row.city}|${row.country}`;
}

/**
 * Regional Sales Ranking -- horizontal bar chart over
 * clicking a bar calls `onSelectCity`, which DashboardPage uses to
 * filter the Geo-Logistics Grid below to that one city -- the same
 * `city|country` key GeoLogisticsGrid uses for its own row identity, so a
 * chart click and a grid-row click are interchangeable (master/detail sync
 * works in both directions). The selected bar renders at full opacity with
 * every other bar dimmed, same treatment as the category donut's
 * click-to-filter state.
 */
export function RegionalSalesChart({
  section,
  isLoading,
  selectedCityKey,
  onSelectCity,
}: {
  section?: SparkSection<GeoPerformanceRow>;
  isLoading: boolean;
  selectedCityKey: string | null;
  onSelectCity: (key: string | null) => void;
}) {
  const [metric, setMetric] = useState<Metric>('revenue');

  if (isLoading && !section) {
    return <div className="flex h-[280px] items-center justify-center text-sm text-slate-400">Loading...</div>;
  }
  if (section && section.meta.status !== 'OK') {
    return (
      <SectionNotice
        meta={section.meta}
        jobHint="Run the Spark batch job (run-batch.sh) to populate this."
      />
    );
  }

  const rows = section?.rows ?? [];
  const top = [...rows]
    .sort((a, b) => b[metric] - a[metric])
    .slice(0, 8)
    .map((r) => ({ ...r, label: `${r.city}`, key: cityKey(r) }));

  if (top.length === 0) {
    return <div className="flex h-[280px] items-center justify-center text-sm text-slate-400">No rows yet.</div>;
  }

  return (
    <div className="space-y-3">
      <div className="flex justify-end gap-1">
        {(['revenue', 'order_count'] as Metric[]).map((m) => (
          <button
            key={m}
            onClick={() => setMetric(m)}
            className={cn(
              'rounded-full border px-2.5 py-1 text-xs font-medium transition-colors',
              metric === m
                ? 'border-slate-900 bg-slate-900 text-white'
                : 'border-slate-200 bg-white text-slate-500 hover:border-slate-300',
            )}
          >
            {METRIC_LABEL[m]}
          </button>
        ))}
      </div>

      <ResponsiveContainer width="100%" height={260}>
        <BarChart data={top} layout="vertical" margin={{ left: 8, right: 16 }}>
          <CartesianGrid horizontal={false} stroke="#e2e8f0" />
          <XAxis
            type="number"
            tickFormatter={(v) => (metric === 'revenue' ? formatCompactNumber(v) : String(v))}
            tick={{ fontSize: 11, fill: '#64748b' }}
            axisLine={{ stroke: '#e2e8f0' }}
            tickLine={false}
          />
          <YAxis
            type="category"
            dataKey="label"
            width={88}
            tick={{ fontSize: 11, fill: '#334155' }}
            axisLine={false}
            tickLine={false}
          />
          <Tooltip content={<RegionalTooltip metric={metric} />} cursor={{ fill: '#f1f5f9' }} />
          <Bar
            dataKey={metric}
            radius={[0, 4, 4, 0]}
            maxBarSize={18}
            onClick={(entry) => {
              // Same defensive top-level-vs-.payload read as the donut's
              // onClick -- Recharts' click-payload shape for <Bar> has been
              // just as version-inconsistent as <Pie>'s.
              const clicked = entry as { payload?: { key: string } } & { key?: string };
              const key = clicked.payload?.key ?? clicked.key;
              if (key === undefined) return;
              onSelectCity(selectedCityKey === key ? null : key);
            }}
            cursor="pointer"
          >
            {top.map((row, index) => (
              <Cell
                key={row.key}
                fill={colorForIndex(index)}
                opacity={selectedCityKey === null || selectedCityKey === row.key ? 1 : 0.3}
              />
            ))}
          </Bar>
        </BarChart>
      </ResponsiveContainer>
    </div>
  );
}
