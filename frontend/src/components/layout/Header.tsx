import { useLocation } from 'react-router-dom';
import { Menu } from 'lucide-react';

const PAGE_TITLES: Record<string, { title: string; description: string }> = {
  '/': {
    title: 'Dashboard',
    description: 'Revenue and ranking signals across Spark and Postgres',
  },
  '/catalog': {
    title: 'Catalog',
    description: 'Live product search backed by Elasticsearch',
  },
  '/operations': {
    title: 'Operations',
    description: 'Trigger and verify backend event pipelines',
  },
};

/**
 * Top bar: page title derived from the current route, plus a live-stream
 * indicator. 
 */
export function Header({ onMenuClick }: { onMenuClick: () => void }) {
  const { pathname } = useLocation();
  const page = PAGE_TITLES[pathname] ?? PAGE_TITLES['/'];

  return (
    <header className="sticky top-0 z-10 flex h-16 items-center justify-between gap-3 border-b border-slate-200 bg-white/80 px-4 backdrop-blur sm:px-6 lg:px-8">
      <div className="flex items-center gap-3">
        <button
          onClick={onMenuClick}
          aria-label="Open navigation"
          className="-ml-1 rounded-md p-1.5 text-slate-500 hover:bg-slate-100 md:hidden"
        >
          <Menu className="h-5 w-5" />
        </button>
        <div>
          <h1 className="text-base font-semibold text-slate-900">{page.title}</h1>
          <p className="hidden text-xs text-slate-500 sm:block">{page.description}</p>
        </div>
      </div>

      <div className="flex items-center gap-2 rounded-full border border-emerald-200 bg-emerald-50 px-3 py-1.5">
        <span className="relative flex h-2 w-2">
          <span className="absolute inline-flex h-full w-full animate-ping rounded-full bg-emerald-400 opacity-75" />
          <span className="relative inline-flex h-2 w-2 rounded-full bg-emerald-500" />
        </span>
        <span className="hidden text-xs font-medium text-emerald-700 sm:inline">
          Live stream connected
        </span>
      </div>
    </header>
  );
}
