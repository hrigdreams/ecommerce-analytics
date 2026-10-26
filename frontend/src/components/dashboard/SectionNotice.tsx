import { DatabaseZap } from 'lucide-react';
import type { SectionMeta } from '../../types';

/**
 * Every Spark-backed section of the dashboard carries its own status: OK
 * (has data), EMPTY (job ran, table has no rows yet), or UNAVAILABLE (the
 * owning Spark job has never run -- the table doesn't exist). Surfacing
 * that distinction matters: an UNAVAILABLE chart means "run the Spark job",
 * not "something is broken."
 */
export function SectionNotice({ meta, jobHint }: { meta: SectionMeta; jobHint: string }) {
  if (meta.status === 'UNAVAILABLE') {
    return (
      <div className="flex h-[240px] flex-col items-center justify-center gap-1 text-center">
        <DatabaseZap className="h-6 w-6 text-slate-300" />
        <p className="text-sm text-slate-500">Not available yet.</p>
        <p className="text-xs text-slate-400">{jobHint}</p>
      </div>
    );
  }
  return (
    <div className="flex h-[240px] items-center justify-center text-sm text-slate-400">
      No rows yet.
    </div>
  );
}
