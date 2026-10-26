import { Cell, Pie, PieChart, ResponsiveContainer, Tooltip } from 'recharts';
import type { CategoryPerformanceRow, SparkSection } from '../../types';
import { colorForIndex } from '../../utils/chartColors';
import { formatCompactNumber, formatCurrency } from '../../utils/formatters';
import { SectionNotice } from './SectionNotice';

interface Props {
  section?: SparkSection<CategoryPerformanceRow>;
  isLoading: boolean;
  /** Currently-selected category id (click-to-filter), or null for "all". */
  selectedCategoryId: number | null;
  onSelectCategory: (categoryId: number | null) => void;
}

function DonutTooltip({
  active,
  payload,
}: {
  active?: boolean;
  payload?: { payload: CategoryPerformanceRow & { share: number } }[];
}) {
  if (!active || !payload?.length) return null;
  const row = payload[0].payload;
  return (
    <div className="rounded-md border border-slate-200 bg-white px-3 py-2 text-xs shadow-sm">
      <p className="font-medium text-slate-900">{row.category_name}</p>
      <p className="text-slate-500">{formatCurrency(row.revenue)} revenue</p>
      <p className="text-slate-500">{formatCompactNumber(row.units_sold)} units sold</p>
      <p className="text-slate-400">{(row.share * 100).toFixed(1)}% of total revenue</p>
    </div>
  );
}

/**
 * Category Revenue Share -- donut chart over spark_batch_category_performance
 * (BatchAnalytics.categoryPerformance, 15 clean categories after the Phase 1
 * seeding fix). Clicking a slice calls `onSelectCategory`, which the parent
 * DashboardPage uses to filter the "Top products by revenue" table below --
 * click the same slice again (or the "Clear filter" chip the parent renders)
 * to go back to showing every category. The selected slice is rendered with
 * full opacity and a white stroke; everything else dims, so the filter state
 * is visible on the chart itself, not just in the table beneath it.
 */
export function CategoryDonutChart({ section, isLoading, selectedCategoryId, onSelectCategory }: Props) {
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
  const totalRevenue = rows.reduce((sum, r) => sum + r.revenue, 0);
  const data = rows.map((r) => ({ ...r, share: totalRevenue > 0 ? r.revenue / totalRevenue : 0 }));

  if (data.length === 0) {
    return <div className="flex h-[280px] items-center justify-center text-sm text-slate-400">No rows yet.</div>;
  }

  return (
    <div className="flex flex-col gap-3">
      <ResponsiveContainer width="100%" height={240}>
        <PieChart>
          <Pie
            data={data}
            dataKey="revenue"
            nameKey="category_name"
            innerRadius={60}
            outerRadius={95}
            paddingAngle={1.5}
            stroke="#fff"
            strokeWidth={2}
            onClick={(entry) => {
              // Recharts merges sector geometry onto the original datum for
              // <Pie> click handlers, but different versions have been
              // inconsistent about whether the original fields land at the
              // top level or nested under `.payload` -- read both so this
              // keeps working regardless.
              const clicked = (entry as { payload?: { category_id: number } } & { category_id?: number });
              const categoryId = clicked.payload?.category_id ?? clicked.category_id;
              if (categoryId === undefined) return;
              onSelectCategory(selectedCategoryId === categoryId ? null : categoryId);
            }}
            cursor="pointer"
          >
            {data.map((row, index) => (
              <Cell
                key={row.category_id}
                fill={colorForIndex(index)}
                opacity={
                  selectedCategoryId === null || selectedCategoryId === row.category_id ? 1 : 0.25
                }
              />
            ))}
          </Pie>
          <Tooltip content={<DonutTooltip />} />
        </PieChart>
      </ResponsiveContainer>

      {/* Compact legend, scrollable: 15 categories won't all fit as chart labels. */}
      <div className="grid max-h-28 grid-cols-1 gap-x-4 gap-y-1 overflow-y-auto pr-1 sm:grid-cols-2">
        {data.map((row, index) => (
          <button
            key={row.category_id}
            onClick={() =>
              onSelectCategory(selectedCategoryId === row.category_id ? null : row.category_id)
            }
            className={`flex items-center gap-1.5 rounded px-1 py-0.5 text-left text-xs transition-opacity hover:bg-slate-50 ${
              selectedCategoryId !== null && selectedCategoryId !== row.category_id
                ? 'opacity-40'
                : ''
            }`}
          >
            <span
              className="h-2 w-2 shrink-0 rounded-full"
              style={{ backgroundColor: colorForIndex(index) }}
            />
            <span className="truncate text-slate-600">{row.category_name}</span>
          </button>
        ))}
      </div>
    </div>
  );
}
