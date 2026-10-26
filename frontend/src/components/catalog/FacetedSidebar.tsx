import { X } from 'lucide-react';
import type { CategoryBucket, CategoryResponse, PriceRangeBucket } from '../../types';
import { cn } from '../../lib/utils';
import { colorForIndex } from '../../utils/chartColors';

/**
 * the backend's own price-range aggregation
 * (ProductSearchNativeService.priceRangeAggregation()) always returns
 * exactly these three fixed labels. The user's original spec sketched
 * "$0-$50 / $50-$100 / $100+" as an example, but those are not the buckets
 * Elasticsearch is actually aggregating on the backend -- using invented
 * boundaries here would make the sidebar's counts (docCount per bucket)
 * come from the server while the bucket *edges* were fabricated on the
 * client, which would drift the moment the real data distribution changed.
 * Flagged as a deliberate deviation from the literal spec text, not an
 * oversight.
 */
export const PRICE_BUCKET_BOUNDS: Record<string, { minPrice?: number; maxPrice?: number }> = {
  'Under $50': { maxPrice: 50 },
  '$50-$200': { minPrice: 50, maxPrice: 200 },
  'Over $200': { minPrice: 200 },
};

/**
 * The native-search endpoint takes a single (minPrice, maxPrice) pair, not
 * a list of disjoint ranges -- so multiple checked price buckets are merged
 * into one spanning range sent to the server (an "envelope" union, not a
 * true OR of each box). For the three real, *contiguous* buckets above that
 * merge is exact for any contiguous selection (e.g. "Under $50" + "$50-$200"
 * correctly becomes maxPrice=200 with no lower bound).
 *
 * Honest gap: checking two *non-adjacent* buckets and leaving the middle one
 * unchecked (e.g. "Under $50" + "Over $200", skipping "$50-$200") cannot be
 * expressed as one min/max pair -- the envelope merge collapses it to "no
 * price filter" rather than silently narrowing to the wrong range. That's a
 * real limitation of a single-range backend filter, not a bug in this merge.
 */
export function mergeSelectedPriceBounds(
  selectedLabels: Set<string>,
): { minPrice: number | null; maxPrice: number | null } {
  if (selectedLabels.size === 0) return { minPrice: null, maxPrice: null };

  let minPrice: number | null | undefined = undefined;
  let maxPrice: number | null | undefined = undefined;

  for (const label of selectedLabels) {
    const bounds = PRICE_BUCKET_BOUNDS[label];
    if (!bounds) continue;

    if (bounds.minPrice === undefined) {
      minPrice = null; // unbounded below wins
    } else if (minPrice !== null) {
      minPrice = minPrice === undefined ? bounds.minPrice : Math.min(minPrice, bounds.minPrice);
    }

    if (bounds.maxPrice === undefined) {
      maxPrice = null; // unbounded above wins
    } else if (maxPrice !== null) {
      maxPrice = maxPrice === undefined ? bounds.maxPrice : Math.max(maxPrice, bounds.maxPrice);
    }
  }

  return { minPrice: minPrice ?? null, maxPrice: maxPrice ?? null };
}

interface FacetedSidebarProps {
  categories: CategoryResponse[];
  categoryBuckets: CategoryBucket[];
  selectedCategoryId: number | null;
  onSelectCategory: (categoryId: number | null) => void;

  priceRangeBuckets: PriceRangeBucket[];
  selectedPriceLabels: Set<string>;
  onTogglePriceLabel: (label: string) => void;

  activeFilterCount: number;
  onClearAll: () => void;

  /** Mobile off-canvas state -- ignored at `lg` and above, same pattern as the main nav Sidebar. */
  mobileOpen: boolean;
  onCloseMobile: () => void;
}

export function FacetedSidebar({
  categories,
  categoryBuckets,
  selectedCategoryId,
  onSelectCategory,
  priceRangeBuckets,
  selectedPriceLabels,
  onTogglePriceLabel,
  activeFilterCount,
  onClearAll,
  mobileOpen,
  onCloseMobile,
}: FacetedSidebarProps) {
  // docCount per categoryId, from this same search response's aggregation --
  // so counts always reflect the current query text, not a stale global count.
  const countByCategoryId = new Map(categoryBuckets.map((b) => [b.categoryId, b.docCount]));
  const countByPriceLabel = new Map(priceRangeBuckets.map((b) => [b.label, b.docCount]));

  const body = (
    <div className="space-y-6">
      <div className="flex items-center justify-between">
        <h2 className="text-sm font-semibold text-slate-900">Filters</h2>
        {activeFilterCount > 0 && (
          <button
            onClick={onClearAll}
            className="text-xs font-medium text-indigo-600 hover:text-indigo-700"
          >
            Clear all ({activeFilterCount})
          </button>
        )}
      </div>

      <fieldset>
        <legend className="mb-2 text-xs font-semibold uppercase tracking-wide text-slate-400">
          Category
        </legend>
        {/*  the backend filter is a single `categoryId` param, not a
            list, so this behaves as single-select -- picking a second
            category swaps the filter rather than widening it to "either of
            these two." Rendered as checkboxes (per spec) for a faceted-sidebar
            look, but functionally radio-like: checking one unchecks any other. */}
        <div className="space-y-1.5">
          {categories.map((category, i) => {
            const count = countByCategoryId.get(category.id) ?? 0;
            const checked = selectedCategoryId === category.id;
            return (
              <label
                key={category.id}
                className={cn(
                  'flex cursor-pointer items-center gap-2.5 rounded-md px-1.5 py-1 text-sm hover:bg-slate-50',
                  checked && 'bg-indigo-50/60',
                )}
              >
                <input
                  type="checkbox"
                  checked={checked}
                  onChange={() => onSelectCategory(checked ? null : category.id)}
                  className="h-3.5 w-3.5 rounded border-slate-300 text-indigo-600 focus:ring-indigo-500"
                />
                <span
                  className="h-2 w-2 shrink-0 rounded-full"
                  style={{ backgroundColor: colorForIndex(i) }}
                  aria-hidden="true"
                />
                <span className={cn('flex-1 truncate', checked ? 'font-medium text-slate-900' : 'text-slate-600')}>
                  {category.name}
                </span>
                <span className="text-xs text-slate-400">{count}</span>
              </label>
            );
          })}
        </div>
      </fieldset>

      <fieldset>
        <legend className="mb-2 text-xs font-semibold uppercase tracking-wide text-slate-400">
          Price
        </legend>
        <div className="space-y-1.5">
          {Object.keys(PRICE_BUCKET_BOUNDS).map((label) => {
            const count = countByPriceLabel.get(label) ?? 0;
            const checked = selectedPriceLabels.has(label);
            return (
              <label
                key={label}
                className={cn(
                  'flex cursor-pointer items-center gap-2.5 rounded-md px-1.5 py-1 text-sm hover:bg-slate-50',
                  checked && 'bg-indigo-50/60',
                )}
              >
                <input
                  type="checkbox"
                  checked={checked}
                  onChange={() => onTogglePriceLabel(label)}
                  className="h-3.5 w-3.5 rounded border-slate-300 text-indigo-600 focus:ring-indigo-500"
                />
                <span className={cn('flex-1', checked ? 'font-medium text-slate-900' : 'text-slate-600')}>
                  {label}
                </span>
                <span className="text-xs text-slate-400">{count}</span>
              </label>
            );
          })}
        </div>
      </fieldset>
    </div>
  );

  return (
    <>
      {/* Desktop: a plain static column, no drawer mechanics. */}
      <div className="hidden lg:block lg:w-64 lg:shrink-0">{body}</div>

      {/* Mobile/tablet: off-canvas drawer, same visual language as the main
          nav Sidebar (backdrop + translate-x slide) so the two "drawer"
          interactions in the app feel like one pattern, not two. */}
      {mobileOpen && (
        <div
          className="fixed inset-0 z-30 bg-slate-900/50 lg:hidden"
          onClick={onCloseMobile}
          aria-hidden="true"
        />
      )}
      <div
        className={cn(
          'fixed inset-y-0 left-0 z-40 w-72 overflow-y-auto bg-white p-5 shadow-xl transition-transform duration-200 ease-in-out lg:hidden',
          mobileOpen ? 'translate-x-0' : '-translate-x-full',
        )}
      >
        <div className="mb-4 flex items-center justify-between">
          <p className="text-sm font-semibold text-slate-900">Filters</p>
          <button
            onClick={onCloseMobile}
            aria-label="Close filters"
            className="rounded-md p-1 text-slate-400 hover:bg-slate-100 hover:text-slate-700"
          >
            <X className="h-4 w-4" />
          </button>
        </div>
        {body}
      </div>
    </>
  );
}
