import { Pause, Play } from 'lucide-react';
import { cn } from '../../lib/utils';

/**

 * polling (useUnifiedAnalytics(live)) without navigating away or losing the
 * last-rendered frame -- useful when stepping through a screenshot, or
 * reading the live event-velocity chart without it visibly shifting every
 * few seconds. Purely a client-side polling toggle: it does not affect the
 * live simulator producing the underlying events 
 */
export function LivePollingToggle({ live, onChange }: { live: boolean; onChange: (live: boolean) => void }) {
  return (
    <button
      onClick={() => onChange(!live)}
      title={live ? 'Pause live updates' : 'Resume live updates'}
      className={cn(
        'inline-flex items-center gap-1.5 rounded-full border px-3 py-1.5 text-xs font-medium transition-colors',
        live
          ? 'border-emerald-200 bg-emerald-50 text-emerald-700'
          : 'border-slate-200 bg-slate-50 text-slate-500',
      )}
    >
      {live ? (
        <span className="relative flex h-2 w-2">
          <span className="absolute inline-flex h-full w-full animate-ping rounded-full bg-emerald-400 opacity-75" />
          <span className="relative inline-flex h-2 w-2 rounded-full bg-emerald-500" />
        </span>
      ) : (
        <span className="h-2 w-2 rounded-full bg-slate-400" />
      )}
      {live ? 'Live' : 'Paused'}
      {live ? <Pause className="h-3 w-3 opacity-50" /> : <Play className="h-3 w-3 opacity-50" />}
    </button>
  );
}
