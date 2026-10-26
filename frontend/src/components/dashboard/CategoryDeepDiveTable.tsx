import { useMemo } from 'react';
import type { ColumnDef } from '@tanstack/react-table';
import type { CategoryPerformanceRow, ProductPerformanceRow, SparkSection } from '../../types';
import { formatCompactNumber, formatCurrency } from '../../utils/formatters';
import { DataTable, SortableHeader } from '../ui/DataTable';
import { SectionNotice } from './SectionNotice';

interface DeepDiveRow extends CategoryPerformanceRow {
  revenueShare: number;
  orderCount: number;
}

interface Props {
  categorySection?: SparkSection<CategoryPerformanceRow>;
  productSection?: SparkSection<ProductPerformanceRow>;
  isLoading: boolean;
  selectedCategoryId: number | null;
  onSelectCategory: (categoryId: number | null) => void;
}

const columns: ColumnDef<DeepDiveRow, any>[] = [
  {
    accessorKey: 'category_name',
    header: 'Category',
    cell: ({ row }) => (
      <span className="font-medium text-slate-900">{row.original.category_name}</span>
    ),
  },
  {
    accessorKey: 'revenueShare',
    header: ({ column }) => <SortableHeader column={column} label="Revenue share" align="right" />,
    cell: ({ row }) => (
      <span className="block text-right font-mono text-xs text-slate-600">
        {(row.original.revenueShare * 100).toFixed(1)}%
      </span>
    ),
  },
  {
    accessorKey: 'revenue',
    header: ({ column }) => <SortableHeader column={column} label="Revenue" align="right" />,
    cell: ({ row }) => (
      <span className="block text-right font-mono text-xs text-slate-600">
        {formatCurrency(row.original.revenue)}
      </span>
    ),
  },
  {
    accessorKey: 'orderCount',
    header: ({ column }) => <SortableHeader column={column} label="Order count" align="right" />,
    cell: ({ row }) => (
      <span className="block text-right font-mono text-xs text-slate-600">
        {formatCompactNumber(row.original.orderCount)}
      </span>
    ),
  },
];

/**
 * Category Deep-Dive Table 
 *
 * Data-availability note: `spark_batch_category_performance`
 * (CategoryPerformanceRow) does NOT carry an order-count field -- only
 * product_count and units_sold. Rather than substitute units_sold under an
 * "Order count" label (which would silently misrepresent what the number
 * is), this derives a real per-category order count by summing order_count
 * across `spark_batch_product_performance` rows (ProductPerformanceRow,
 * which does have both category_id and order_count) grouped by
 * category_id. Both sections already arrive together on
 * GET /analytics/unified, so this is a client-side aggregation over data
 * already in hand, not an extra request. A product with a null category_id
 * contributes to no category's total here (there's nothing to attribute it
 * to) -- so the sum of every row's orderCount can be slightly less than the
 * platform's total paid order count whenever uncategorized products exist.
 */
export function CategoryDeepDiveTable({
  categorySection,
  productSection,
  isLoading,
  selectedCategoryId,
  onSelectCategory,
}: Props) {
  const rows = useMemo<DeepDiveRow[]>(() => {
    const categoryRows = categorySection?.rows ?? [];
    const productRows = productSection?.rows ?? [];

    const orderCountByCategory = new Map<number, number>();
    for (const product of productRows) {
      if (product.category_id === null) continue;
      orderCountByCategory.set(
        product.category_id,
        (orderCountByCategory.get(product.category_id) ?? 0) + product.order_count,
      );
    }

    const totalRevenue = categoryRows.reduce((sum, r) => sum + r.revenue, 0);

    return categoryRows.map((r) => ({
      ...r,
      revenueShare: totalRevenue > 0 ? r.revenue / totalRevenue : 0,
      orderCount: orderCountByCategory.get(r.category_id) ?? 0,
    }));
  }, [categorySection, productSection]);

  if (isLoading && !categorySection) {
    return <div className="p-8 text-center text-sm text-slate-400">Loading categories...</div>;
  }
  if (categorySection && categorySection.meta.status !== 'OK') {
    return (
      <SectionNotice
        meta={categorySection.meta}
        jobHint="Run the Spark batch job (run-batch.sh) to populate this."
      />
    );
  }

  return (
    <DataTable
      columns={columns}
      data={rows}
      getRowId={(row) => String(row.category_id)}
      onRowClick={(row) =>
        onSelectCategory(selectedCategoryId === row.category_id ? null : row.category_id)
      }
      isRowHighlighted={(row) => selectedCategoryId === row.category_id}
      hasActiveFilter={selectedCategoryId !== null}
      initialSorting={[{ id: 'revenue', desc: true }]}
      emptyMessage="No category data yet."
    />
  );
}
