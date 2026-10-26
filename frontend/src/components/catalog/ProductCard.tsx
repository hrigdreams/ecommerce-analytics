import type { ProductDocument } from '../../types';
import { highlightMatch } from '../../utils/highlight';
import { formatCompactNumber, formatCurrency } from '../../utils/formatters';
import { colorForIndex } from '../../utils/chartColors';
import { StockBadge } from '../ui/Badge';
import { cn } from '../../lib/utils';


function MetaStat({ label, value, primary = false }: { label: string; value: string; primary?: boolean }) {
  return (
    <div className="text-right">
      <p className="text-[10px] font-medium uppercase tracking-wide text-slate-400">{label}</p>
      <p
        className={cn(
          'font-mono tabular-nums',
          primary ? 'text-sm font-bold text-slate-900' : 'text-sm font-semibold text-slate-600',
        )}
      >
        {value}
      </p>
    </div>
  );
}

export function ProductCard({ product, query }: { product: ProductDocument; query: string }) {
  const categoryColor = colorForIndex(product.categoryId ?? 0);

  return (
    <div className="flex flex-col gap-3 rounded-lg border border-slate-200 bg-white px-4 py-3 shadow-sm transition-colors transition-shadow hover:border-slate-300 hover:bg-slate-50 hover:shadow-md sm:flex-row sm:items-center sm:justify-between sm:gap-6">
      {/* Identity + facets */}
      <div className="min-w-0 flex-1">
        <div className="flex items-center gap-2">
          <h3 className="truncate text-sm font-semibold text-slate-900">
            {highlightMatch(product.name, query)}
          </h3>
          <span className="shrink-0 rounded bg-slate-100 px-1.5 py-0.5 font-mono text-[10px] font-medium text-slate-500">
            #{product.id}
          </span>
        </div>

        <div className="mt-1.5 flex flex-wrap items-center gap-1.5">
          {product.categoryName && (
            <span
              className="rounded-full px-2 py-0.5 text-xs font-medium"
              style={{ backgroundColor: `${categoryColor}1a`, color: categoryColor }}
            >
              {product.categoryName}
              <span className="ml-1 opacity-60">(id: {product.categoryId})</span>
            </span>
          )}
          {!product.categoryName && (
            <span className="rounded-full bg-slate-100 px-2 py-0.5 text-xs font-medium text-slate-500">
              categoryId: {product.categoryId ?? 'null'}
            </span>
          )}
          <StockBadge inStock={product.inStock} />
        </div>
      </div>

      {/* Metrics -- grouped like dashboard stat tiles, price given visual priority since it's the primary business figure */}
      <div className="flex shrink-0 items-center gap-5 border-t border-slate-100 pt-3 sm:border-t-0 sm:pt-0">
        <MetaStat label="Price" value={formatCurrency(product.price)} primary />
        <MetaStat label="Sales" value={formatCompactNumber(product.salesCount)} />
        <MetaStat label="Popularity" value={product.popularityScore.toFixed(2)} />
        {product.averageRating !== null && (
          <MetaStat label="Rating" value={`${product.averageRating.toFixed(1)} ★`} />
        )}
      </div>
    </div>
  );
}
