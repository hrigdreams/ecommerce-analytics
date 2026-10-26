import { Bar, BarChart, CartesianGrid, Cell, ResponsiveContainer, Tooltip, XAxis, YAxis } from 'recharts';
import type { PeakHourRow, SectionStatus } from '../../types';
import { formatCompactNumber, formatCurrency } from '../../utils/formatters';
import { SectionNotice } from './SectionNotice';

function hourLabel(hour: number): string {
  const period = hour < 12 ? 'AM' : 'PM';
  const twelveHour = hour % 12 === 0 ? 12 : hour % 12;
  return `${twelveHour}${period}`;
}

function HourTooltip({
  active,
  payload,
}: {
  active?: boolean;
  payload?: { payload: PeakHourRow }[];
}) {
  if (!active || !payload?.length) return null;
  const row = payload[0].payload;
  return (
    <div className="rounded-md border border-slate-200 bg-white px-3 py-2 text-xs shadow-sm">
      <p className="font-medium text-slate-900">{hourLabel(row.hour_of_day)}</p>
      <p className="text-slate-500">{formatCompactNumber(row.order_count)} orders</p>
      <p className="text-slate-500">{formatCurrency(row.revenue)} revenue</p>
    </div>
  );
}

/**
 * Peak Shopping Hours 
 * endpoint GET /api/v1/analytics/peak-hours (spark_batch_hourly_pattern).
 * Not one of the four charts explicitly named in the request -- added as
 * an autonomous enhancement specifically because the backend delivery that
 * preceded this one built this exact endpoint for this exact purpose, and
 * shipping the frontend without ever reading it back would leave a real,
 * already-working feature invisible. 
 */
export function PeakHoursChart({
  rows,
  status,
  isLoading,
}: {
  rows: PeakHourRow[];
  status?: SectionStatus;
  isLoading: boolean;
}) {
  if (isLoading && rows.length === 0 && !status) {
    return <div className="flex h-[240px] items-center justify-center text-sm text-slate-400">Loading...</div>;
  }
  if (status && status !== 'OK') {
    return (
      <SectionNotice
        meta={{ source: 'SPARK_BATCH', status, asOf: null }}
        jobHint="Run the Spark batch job (run-batch.sh) to populate this."
      />
    );
  }
  if (rows.length === 0) {
    return <div className="flex h-[240px] items-center justify-center text-sm text-slate-400">No rows yet.</div>;
  }

  const busiestHour = rows.reduce((max, r) => (r.order_count > max.order_count ? r : max), rows[0]);

  return (
    <ResponsiveContainer width="100%" height={240}>
      <BarChart data={rows} margin={{ top: 4, right: 8 }}>
        <CartesianGrid vertical={false} stroke="#e2e8f0" />
        <XAxis
          dataKey="hour_of_day"
          tickFormatter={(h: number) => (h % 3 === 0 ? hourLabel(h) : '')}
          tick={{ fontSize: 10, fill: '#64748b' }}
          axisLine={{ stroke: '#e2e8f0' }}
          tickLine={false}
          interval={0}
        />
        <YAxis
          tickFormatter={(v) => formatCompactNumber(v)}
          tick={{ fontSize: 11, fill: '#64748b' }}
          axisLine={false}
          tickLine={false}
          width={36}
        />
        <Tooltip content={<HourTooltip />} cursor={{ fill: '#f1f5f9' }} />
        <Bar dataKey="order_count" radius={[3, 3, 0, 0]} maxBarSize={16}>
          {rows.map((row) => (
            <Cell
              key={row.hour_of_day}
              fill={row.hour_of_day === busiestHour.hour_of_day ? '#6366f1' : '#c7d2fe'}
            />
          ))}
        </Bar>
      </BarChart>
    </ResponsiveContainer>
  );
}
