import { NavLink } from 'react-router-dom';
import { LayoutDashboard, Search, Settings, Activity, X } from 'lucide-react';
import { cn } from '../../lib/utils';

const NAV_ITEMS = [
  { to: '/', label: 'Dashboard', icon: LayoutDashboard, end: true },
  { to: '/catalog', label: 'Catalog', icon: Search, end: false },
  { to: '/operations', label: 'Operations', icon: Settings, end: false },
];


export function Sidebar({ open, onClose }: { open: boolean; onClose: () => void }) {
  return (
    <>
      {/* Backdrop: mobile only, only rendered while the drawer is open. */}
      {open && (
        <div
          className="fixed inset-0 z-10 bg-slate-900/50 md:hidden"
          onClick={onClose}
          aria-hidden="true"
        />
      )}

      <aside
        className={cn(
          'fixed inset-y-0 left-0 z-20 flex w-60 flex-col bg-slate-900 transition-transform duration-200 ease-in-out',
          'md:translate-x-0',
          open ? 'translate-x-0' : '-translate-x-full',
        )}
      >
        <div className="flex h-16 items-center justify-between gap-2.5 px-5">
          <div className="flex items-center gap-2.5">
            <div className="flex h-8 w-8 items-center justify-center rounded-md bg-indigo-500">
              <Activity className="h-4.5 w-4.5 text-white" strokeWidth={2.5} />
            </div>
            <div className="leading-tight">
              <p className="text-sm font-semibold text-white">Commerce Ops</p>
              <p className="text-xs text-slate-400">Event-driven console</p>
            </div>
          </div>
          <button
            onClick={onClose}
            aria-label="Close navigation"
            className="rounded-md p-1 text-slate-400 hover:bg-slate-800 hover:text-white md:hidden"
          >
            <X className="h-4 w-4" />
          </button>
        </div>

        <nav className="flex-1 space-y-1 px-3 py-4">
          {NAV_ITEMS.map(({ to, label, icon: Icon, end }) => (
            <NavLink
              key={to}
              to={to}
              end={end}
              onClick={onClose}
              className={({ isActive }) =>
                cn(
                  'flex items-center gap-3 rounded-md px-3 py-2 text-sm font-medium transition-colors',
                  isActive
                    ? 'bg-slate-800 text-white'
                    : 'text-slate-400 hover:bg-slate-800/60 hover:text-slate-200',
                )
              }
            >
              <Icon className="h-4 w-4" />
              {label}
            </NavLink>
          ))}
        </nav>

        <div className="border-t border-slate-800 px-5 py-4">
          <p className="text-xs text-slate-500">Backed by Kafka, Spark and Elasticsearch</p>
        </div>
      </aside>
    </>
  );
}
