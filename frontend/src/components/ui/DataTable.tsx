import {
  type Column,
  type ColumnDef,
  type SortingState,
  flexRender,
  getCoreRowModel,
  getSortedRowModel,
  useReactTable,
} from '@tanstack/react-table';
import { useState } from 'react';
import { ArrowDown, ArrowUp, ArrowUpDown } from 'lucide-react';
import { cn } from '../../lib/utils';

/**
 *  dense-data-table primitive, built on TanStack Table v8
 *
 * - Sticky header: the wrapping div is the scroll container
 *   (`overflow-y-auto` + `maxHeightClassName`), and `<thead>` is
 *   `sticky top-0` within it -- this is also what gives the Live
 *   Transaction Feed its "terminal/log" scroll behavior (pass a
 *   `maxHeightClassName` there; omit it on the Dashboard tables, which
 *   scroll with the page instead).
 * - Row click -> `onRowClick`, used for the master/detail sync: clicking a
 *   row in CategoryDeepDiveTable or GeoLogisticsGrid sets the same
 *   selection state a chart click would, and vice versa.
 * - `isRowHighlighted` renders the selected row with an indigo tint and
 *   dims the rest slightly, mirroring the opacity treatment the Phase 2
 *   charts already use for their own click-to-filter state, so the
 *   "something is filtered" affordance looks the same whether it started
 *   on a chart or a table row.
 */
export interface DataTableProps<TData> {
  // eslint-disable-next-line @typescript-eslint/no-explicit-any -- a column
  // array legitimately mixes columns of different cell-value types; `any`
  // here is TanStack Table's own documented pattern for ColumnDef[], not a
  // shortcut around real typing (every individual column below is still
  // fully typed against its row shape).
  columns: ColumnDef<TData, any>[];
  data: TData[];
  getRowId?: (row: TData, index: number) => string;
  onRowClick?: (row: TData) => void;
  isRowHighlighted?: (row: TData) => boolean;
  hasActiveFilter?: boolean;
  maxHeightClassName?: string;
  emptyMessage?: string;
  initialSorting?: SortingState;
}

export function DataTable<TData>({
  columns,
  data,
  getRowId,
  onRowClick,
  isRowHighlighted,
  hasActiveFilter = false,
  maxHeightClassName,
  emptyMessage = 'No rows.',
  initialSorting = [],
}: DataTableProps<TData>) {
  const [sorting, setSorting] = useState<SortingState>(initialSorting);

  const table = useReactTable({
    data,
    columns,
    state: { sorting },
    onSortingChange: setSorting,
    getCoreRowModel: getCoreRowModel(),
    getSortedRowModel: getSortedRowModel(),
    getRowId,
  });

  if (data.length === 0) {
    return <div className="p-8 text-center text-sm text-slate-400">{emptyMessage}</div>;
  }

  return (
    <div className={cn('overflow-auto', maxHeightClassName)}>
      <table className="w-full min-w-full border-collapse text-sm">
        <thead className="sticky top-0 z-10 border-b border-slate-200 bg-white">
          {table.getHeaderGroups().map((headerGroup) => (
            <tr key={headerGroup.id}>
              {headerGroup.headers.map((header) => (
                <th
                  key={header.id}
                  className="whitespace-nowrap bg-white px-4 py-3 text-left text-xs font-medium text-slate-500"
                >
                  {header.isPlaceholder
                    ? null
                    : flexRender(header.column.columnDef.header, header.getContext())}
                </th>
              ))}
            </tr>
          ))}
        </thead>
        <tbody className="divide-y divide-slate-100">
          {table.getRowModel().rows.map((row) => {
            const highlighted = isRowHighlighted?.(row.original) ?? false;
            return (
              <tr
                key={row.id}
                onClick={onRowClick ? () => onRowClick(row.original) : undefined}
                className={cn(
                  'transition-colors',
                  onRowClick && 'cursor-pointer hover:bg-slate-50',
                  highlighted && 'bg-indigo-50 hover:bg-indigo-50',
                  hasActiveFilter && !highlighted && 'opacity-50',
                )}
              >
                {row.getVisibleCells().map((cell) => (
                  <td key={cell.id} className="px-4 py-2.5 text-slate-700">
                    {flexRender(cell.column.columnDef.cell, cell.getContext())}
                  </td>
                ))}
              </tr>
            );
          })}
        </tbody>
      </table>
    </div>
  );
}

/** Sortable column header: label + a chevron reflecting current sort direction (or a neutral both-ways icon when unsorted). */
export function SortableHeader<TData>({
  column,
  label,
  align = 'left',
}: {
  column: Column<TData, unknown>;
  label: string;
  align?: 'left' | 'right';
}) {
  const sorted = column.getIsSorted();
  return (
    <button
      type="button"
      onClick={column.getToggleSortingHandler()}
      className={cn(
        'inline-flex items-center gap-1 text-xs font-medium text-slate-500 hover:text-slate-700',
        align === 'right' && 'w-full flex-row-reverse',
      )}
    >
      <span>{label}</span>
      {sorted === 'asc' ? (
        <ArrowUp className="h-3 w-3" />
      ) : sorted === 'desc' ? (
        <ArrowDown className="h-3 w-3" />
      ) : (
        <ArrowUpDown className="h-3 w-3 opacity-40" />
      )}
    </button>
  );
}
