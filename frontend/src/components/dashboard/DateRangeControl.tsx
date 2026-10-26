import { cn } from '../../lib/utils';

export interface DateRange {
  label: string;
  from: string;
  to: string;
}

const PRESETS: { label: string; days: number }[] = [
  { label: '7D', days: 7 },
  { label: '30D', days: 30 },
  { label: '90D', days: 90 },
];

/** Builds a preset's [from, to) ISO instant pair, `to` pinned to "now" at build time. */
export function buildPresetRange(days: number): DateRange {
  const to = new Date();
  const from = new Date(to.getTime() - days * 24 * 60 * 60 * 1000);
  return { label: `${days}D`, from: from.toISOString(), to: to.toISOString() };
}


export function DateRangeControl({
  value,
  onChange,
}: {
  value: DateRange;
  onChange: (range: DateRange) => void;
}) {
  return (
    <div className="inline-flex rounded-md border border-slate-200 bg-white p-0.5">
      {PRESETS.map((preset) => (
        <button
          key={preset.label}
          onClick={() => onChange(buildPresetRange(preset.days))}
          className={cn(
            'rounded px-2.5 py-1 text-xs font-medium transition-colors',
            value.label === preset.label
              ? 'bg-slate-900 text-white'
              : 'text-slate-500 hover:bg-slate-100',
          )}
        >
          {preset.label}
        </button>
      ))}
    </div>
  );
}
