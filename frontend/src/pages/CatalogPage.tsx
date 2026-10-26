import { useEffect, useMemo, useState } from 'react';
import { useSearchParams } from 'react-router-dom';
import { ArrowUpDown, PackageSearch, Search, SlidersHorizontal } from 'lucide-react';
import { useCategories, useProductSearch } from '../api/hooks';
import { Card, CardBody } from '../components/ui/Card';
import { FacetedSidebar, mergeSelectedPriceBounds } from '../components/catalog/FacetedSidebar';
import { ProductCard } from '../components/catalog/ProductCard';
import { parseFlexibleTimestamp } from '../utils/formatters';
import type { ProductDocument } from '../types';

type SortOption = 'relevance' | 'price-asc' | 'newest';

const SORT_LABELS: Record<SortOption, string> = {
  relevance: 'Relevance',
  'price-asc': 'Price: Low to High',
  newest: 'Newest',
};

/**
 * `NativeSearchController` (GET /api/v1/search/native/products)
 * takes q/categoryId/minPrice/maxPrice/page/size only -- there is no
 * sort/sortBy param anywhere on that endpoint. 
 */
function sortHits(hits: ProductDocument[], sort: SortOption): ProductDocument[] {
  if (sort === 'relevance') return hits;
  const copy = [...hits];
  if (sort === 'price-asc') copy.sort((a, b) => a.price - b.price);
  if (sort === 'newest') {
    copy.sort(
      (a, b) => parseFlexibleTimestamp(b.createdAt).getTime() - parseFlexibleTimestamp(a.createdAt).getTime(),
    );
  }
  return copy;
}

export function CatalogPage() {
  
  const [searchParams, setSearchParams] = useSearchParams();

  const [query, setQuery] = useState('');
  const [categoryId, setCategoryId] = useState<number | null>(() => {
    const raw = searchParams.get('categoryId');
    return raw !== null ? Number(raw) : null;
  });
  const [selectedPriceLabels, setSelectedPriceLabels] = useState<Set<string>>(() => {
    const raw = searchParams.get('price');
    return raw ? new Set(raw.split(',')) : new Set();
  });
  const [sort, setSort] = useState<SortOption>(() => {
    const raw = searchParams.get('sort');
    return raw === 'price-asc' || raw === 'newest' ? raw : 'relevance';
  });
  const [mobileFiltersOpen, setMobileFiltersOpen] = useState(false);

  useEffect(() => {
    const next = new URLSearchParams(searchParams);
    if (categoryId !== null) next.set('categoryId', String(categoryId));
    else next.delete('categoryId');
    if (selectedPriceLabels.size > 0) next.set('price', Array.from(selectedPriceLabels).join(','));
    else next.delete('price');
    if (sort !== 'relevance') next.set('sort', sort);
    else next.delete('sort');
    setSearchParams(next, { replace: true });
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [categoryId, selectedPriceLabels, sort]);

  const { minPrice, maxPrice } = mergeSelectedPriceBounds(selectedPriceLabels);

  const { hits, totalHits, categoryBuckets, priceRangeBuckets, isLoading, isDebouncing } =
    useProductSearch(query, categoryId, minPrice, maxPrice);

  const { categories } = useCategories();

  const sortedHits = useMemo(() => sortHits(hits, sort), [hits, sort]);

  const showResults = query.trim().length > 0 || categoryId !== null || selectedPriceLabels.size > 0;
  const showSpinner = isDebouncing || isLoading;
  const activeFilterCount = (categoryId !== null ? 1 : 0) + selectedPriceLabels.size;

  function togglePriceLabel(label: string) {
    setSelectedPriceLabels((prev) => {
      const next = new Set(prev);
      next.has(label) ? next.delete(label) : next.add(label);
      return next;
    });
  }

  function clearAllFilters() {
    setCategoryId(null);
    setSelectedPriceLabels(new Set());
  }

  return (
    <div className="flex flex-col gap-6 lg:flex-row">
      <FacetedSidebar
        categories={categories}
        categoryBuckets={categoryBuckets}
        selectedCategoryId={categoryId}
        onSelectCategory={setCategoryId}
        priceRangeBuckets={priceRangeBuckets}
        selectedPriceLabels={selectedPriceLabels}
        onTogglePriceLabel={togglePriceLabel}
        activeFilterCount={activeFilterCount}
        onClearAll={clearAllFilters}
        mobileOpen={mobileFiltersOpen}
        onCloseMobile={() => setMobileFiltersOpen(false)}
      />

      <div className="min-w-0 flex-1 space-y-6">
        <div className="flex gap-2">
          <div className="relative flex-1">
            <Search className="pointer-events-none absolute left-4 top-1/2 h-5 w-5 -translate-y-1/2 text-slate-400" />
            <input
              type="text"
              value={query}
              onChange={(e) => setQuery(e.target.value)}
              placeholder="Search products by name -- typo-tolerant, backed by Elasticsearch"
              className="w-full rounded-lg border border-slate-200 bg-white py-4 pl-12 pr-12 text-base text-slate-900 shadow-sm outline-none transition-shadow placeholder:text-slate-400 focus:border-indigo-400 focus:ring-4 focus:ring-indigo-50"
            />
            {showSpinner && (
              <div className="absolute right-4 top-1/2 h-4 w-4 -translate-y-1/2 animate-spin rounded-full border-2 border-slate-300 border-t-indigo-500" />
            )}
          </div>

          {/* Mobile-only filters toggle -- the sidebar itself is hidden below `lg`. */}
          <button
            onClick={() => setMobileFiltersOpen(true)}
            className="relative flex shrink-0 items-center gap-2 rounded-lg border border-slate-200 bg-white px-4 text-sm font-medium text-slate-600 shadow-sm hover:border-slate-300 lg:hidden"
          >
            <SlidersHorizontal className="h-4 w-4" />
            Filters
            {activeFilterCount > 0 && (
              <span className="absolute -right-1.5 -top-1.5 flex h-4.5 w-4.5 items-center justify-center rounded-full bg-indigo-600 text-[10px] font-semibold text-white">
                {activeFilterCount}
              </span>
            )}
          </button>
        </div>

        {showResults && (
          <div className="flex flex-wrap items-center justify-between gap-3">
            <p className="text-sm text-slate-500">
              {totalHits} {totalHits === 1 ? 'result' : 'results'}
            </p>
            <label className="flex items-center gap-2 text-sm text-slate-600">
              <ArrowUpDown className="h-3.5 w-3.5 text-slate-400" />
              <select
                value={sort}
                onChange={(e) => setSort(e.target.value as SortOption)}
                className="rounded-md border border-slate-200 bg-white py-1.5 pl-2 pr-7 text-sm text-slate-700 outline-none focus:border-indigo-400 focus:ring-2 focus:ring-indigo-50"
              >
                {(Object.keys(SORT_LABELS) as SortOption[]).map((opt) => (
                  <option key={opt} value={opt}>
                    {SORT_LABELS[opt]}
                  </option>
                ))}
              </select>
            </label>
          </div>
        )}

        {!showResults && (
          <Card>
            <CardBody className="flex flex-col items-center gap-2 py-16 text-center">
              <PackageSearch className="h-8 w-8 text-slate-300" />
              <p className="text-sm text-slate-500">
                Start typing, or pick a category or price range, to search the live product catalog.
              </p>
            </CardBody>
          </Card>
        )}

        {showResults && !isLoading && sortedHits.length === 0 && (
          <Card>
            <CardBody className="flex flex-col items-center gap-2 py-16 text-center">
              <PackageSearch className="h-8 w-8 text-slate-300" />
              <p className="text-sm text-slate-500">
                {query.trim() ? `No products match "${query.trim()}".` : 'No products match these filters.'}
              </p>
            </CardBody>
          </Card>
        )}

        {showResults && sortedHits.length > 0 && (
          // PHASE 3D: switched from the Phase 3C dense multi-column card
          // grid to a full-width list -- each ProductCard row now carries
          // 4-5 metadata fields (price/sales/popularity/rating) plus
          // category and stock facets, which reads far better as one row
          // per product than packed into a narrow grid cell.
          <div className="flex flex-col gap-3">
            {sortedHits.map((product) => (
              <ProductCard key={product.id} product={product} query={query} />
            ))}
          </div>
        )}
      </div>
    </div>
  );
}
