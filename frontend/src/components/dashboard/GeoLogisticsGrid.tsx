import { useMemo } from 'react';
import type { ColumnDef } from '@tanstack/react-table';
import type { GeoPerformanceRow, SparkSection } from '../../types';
import { formatCompactNumber, formatCurrency } from '../../utils/formatters';
import { DataTable, SortableHeader } from '../ui/DataTable';
import { SectionNotice } from './SectionNotice';

interface GeoRow extends GeoPerformanceRow {
  key: string;
  averageOrderValue: number | null;
}

interface Props {
  section?: SparkSection<GeoPerformanceRow>;
  isLoading: boolean;
  selectedCityKey: string | null;
  onSelectCity: (key: string | null) => void;
}

const columns: ColumnDef<GeoRow, any>[] = [
  {
    accessorKey: 'city',
    header: 'City',
    cell: ({ row }) => (
      <div>
        <span className="font-medium text-slate-900">{row.original.city}</span>
        <span className="ml-1.5 text-xs text-slate-400">{row.original.country}</span>
      </div>
    ),
  },
  {
    accessorKey: 'revenue',
    header: ({ column }) => <SortableHeader column={column} label="Total revenue" align="right" />,
    cell: ({ row }) => (
      <span className="block text-right font-mono text-xs text-slate-600">
        {formatCurrency(row.original.revenue)}
      </span>
    ),
  },
  {
    accessorKey: 'order_count',
    header: ({ column }) => <SortableHeader column={column} label="Total orders" align="right" />,
    cell: ({ row }) => (
      <span className="block text-right font-mono text-xs text-slate-600">
        {formatCompactNumber(row.original.order_count)}
      </span>
    ),
  },
  {
    accessorKey: 'paid_order_count',
    header: ({ column }) => <SortableHeader column={column} label="Paid orders" align="right" />,
    cell: ({ row }) => (
      <span className="block text-right font-mono text-xs text-slate-500">
        {formatCompactNumber(row.original.paid_order_count)}
      </span>
    ),
  },
  {
    accessorKey: 'averageOrderValue',
    header: ({ column }) => <SortableHeader column={column} label="AOV" align="right" />,
    cell: ({ row }) => (
      <span className="block text-right font-mono text-xs font-semibold text-slate-900">
        {row.original.averageOrderValue !== null
          ? formatCurrency(row.original.averageOrderValue)
          : '—'}
      </span>
    ),
  },
];

function cityKey(row: GeoPerformanceRow): string {
  return `${row.city}|${row.country}`;
}

/**
 * Every city in `spark_batch_geo_performance`
 * (not just the chart's top 8), sortable by any column -- the whole point
 * is letting ops find a high-AOV market the top-8 chart would hide.
**/
export function GeoLogisticsGrid({ section, isLoading, selectedCityKey, onSelectCity }: Props) {
  const rows = useMemo<GeoRow[]>(() => {
    const geoRows = section?.rows ?? [];
    return geoRows.map((r) => ({
      ...r,
      key: cityKey(r),
      averageOrderValue: r.paid_order_count > 0 ? r.revenue / r.paid_order_count : null,
    }));
  }, [section]);

  if (isLoading && !section) {
    return <div className="p-8 text-center text-sm text-slate-400">Loading cities...</div>;
  }
  if (section && section.meta.status !== 'OK') {
    return (
      <SectionNotice
        meta={section.meta}
        jobHint="Run the Spark batch job (run-batch.sh) to populate this."
      />
    );
  }

  return (
    <DataTable
      columns={columns}
      data={rows}
      getRowId={(row) => row.key}
      onRowClick={(row) => onSelectCity(selectedCityKey === row.key ? null : row.key)}
      isRowHighlighted={(row) => selectedCityKey === row.key}
      hasActiveFilter={selectedCityKey !== null}
      maxHeightClassName="max-h-[420px]"
      initialSorting={[{ id: 'revenue', desc: true }]}
      emptyMessage="No geo data yet."
    />
  );
}
