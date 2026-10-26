import { useEffect, useRef, useState } from 'react';
import type { RecentSimEvent } from '../../types';
import { formatLocalTime, parseEventDetail } from '../../utils/formatters';
import { cn } from '../../lib/utils';

/**
 * Live Transaction Feed, polling GET /api/v1/simulator/events/recent
 * every 2s (useRecentSimEvents). 
 *
 * Columns, per spec: Local Timestamp (parseSparkUtcDate-based, via
 * formatLocalTime 
 */

const EVENT_COLOR: Record<string, string> = {
  CREATE_CART: 'text-sky-400',
  ADD_CART_ITEM: 'text-sky-400',
  UPDATE_CART_ITEM: 'text-sky-400',
  REMOVE_CART_ITEM: 'text-slate-400',
  CREATE_ORDER: 'text-emerald-400',
  CREATE_PAYMENT: 'text-emerald-400',
  CHANGE_ORDER_STATUS: 'text-amber-400',
  CREATE_REVIEW: 'text-violet-400',
  UPDATE_REVIEW: 'text-violet-400',
};

function eventColor(eventType: string): string {
  return EVENT_COLOR[eventType] ?? 'text-slate-300';
}

/** Known detail keys worth a human label, in display order. Anything else in the parsed map is dropped silently rather than dumped raw. */
const DETAIL_LABELS: [key: string, label: string, format?: (v: string) => string][] = [
  ['userId', 'User', (v) => `User ${v}`],
  ['orderId', 'Order', (v) => `Order ${v}`],
  ['reviewId', 'Review', (v) => `Review ${v}`],
  ['productId', 'Product', (v) => `Product ${v}`],
  ['total', 'Amount', (v) => `$${v}`],
  ['amount', 'Amount', (v) => `$${v}`],
  ['rating', 'Rating', (v) => `${v}★`],
  ['qty', 'Qty', (v) => `×${v}`],
  ['status', 'Status', (v) => v],
  ['method', 'Method', (v) => v],
];

function DetailFragment({ detail }: { detail: string }) {
  const fields = parseEventDetail(detail);
  const shown = DETAIL_LABELS.filter(([key]) => key in fields);

  if (shown.length === 0) {
    // Nothing recognized -- fall back to the raw string rather than showing nothing.
    return <span className="text-slate-400">{detail}</span>;
  }

  return (
    <span className="text-slate-300">
      {shown.map(([key, , fmt], i) => (
        <span key={key}>
          {i > 0 && <span className="text-slate-600"> · </span>}
          {(fmt ?? ((v: string) => v))(fields[key])}
        </span>
      ))}
    </span>
  );
}

export function LiveTransactionFeed({ events, isLoading }: { events: RecentSimEvent[]; isLoading: boolean }) {
  const scrollRef = useRef<HTMLDivElement>(null);
  // Auto-scroll to the newest row only if the user hasn't scrolled up to
  // read older entries -- a feed that yanks the viewport back to the top
  // every 2s while someone's trying to read row 40 would be actively
  // hostile, not "real-time."
  const [autoScroll, setAutoScroll] = useState(true);

  useEffect(() => {
    if (autoScroll && scrollRef.current) {
      scrollRef.current.scrollTop = 0;
    }
  }, [events, autoScroll]);

  function handleScroll() {
    if (!scrollRef.current) return;
    setAutoScroll(scrollRef.current.scrollTop < 8);
  }

  if (isLoading && events.length === 0) {
    return (
      <div className="flex h-64 items-center justify-center rounded-lg border border-slate-800 bg-slate-950 text-xs text-slate-500">
        Connecting to simulator event stream...
      </div>
    );
  }

  if (events.length === 0) {
    return (
      <div className="flex h-64 flex-col items-center justify-center gap-1 rounded-lg border border-slate-800 bg-slate-950 text-center text-xs text-slate-500">
        <p>No events yet.</p>
        <p className="text-slate-600">Start the burst simulator or continuous pulse below to see live activity here.</p>
      </div>
    );
  }

  return (
    <div className="overflow-hidden rounded-lg border border-slate-800 bg-slate-950">
      <div className="flex items-center justify-between border-b border-slate-800 bg-slate-900/60 px-3 py-1.5">
        <div className="flex items-center gap-1.5">
          <span className="h-2 w-2 rounded-full bg-rose-500" />
          <span className="h-2 w-2 rounded-full bg-amber-500" />
          <span className="h-2 w-2 rounded-full bg-emerald-500" />
          <span className="ml-2 font-mono text-[11px] text-slate-500">simulator-events --follow</span>
        </div>
        {!autoScroll && (
          <button
            onClick={() => {
              setAutoScroll(true);
              if (scrollRef.current) scrollRef.current.scrollTop = 0;
            }}
            className="rounded-full border border-slate-700 bg-slate-800 px-2 py-0.5 font-mono text-[10px] text-slate-300 hover:bg-slate-700"
          >
            scrolled up -- click to resume
          </button>
        )}
      </div>

      <div
        ref={scrollRef}
        onScroll={handleScroll}
        className="max-h-80 overflow-y-auto font-mono text-xs leading-relaxed"
      >
        <table className="w-full min-w-full border-collapse">
          <thead className="sticky top-0 z-10 bg-slate-950">
            <tr className="border-b border-slate-800 text-[10px] uppercase tracking-wide text-slate-600">
              <th className="px-3 py-1.5 text-left font-medium">Time</th>
              <th className="px-3 py-1.5 text-left font-medium">Mode</th>
              <th className="px-3 py-1.5 text-left font-medium">Event</th>
              <th className="px-3 py-1.5 text-left font-medium">Payload</th>
            </tr>
          </thead>
          <tbody className="divide-y divide-slate-900">
            {events.map((event, i) => (
              <tr key={`${event.occurredAt}-${i}`} className="hover:bg-slate-900/50">
                <td className="whitespace-nowrap px-3 py-1 text-slate-500">
                  {formatLocalTime(event.occurredAt)}
                </td>
                <td className="whitespace-nowrap px-3 py-1">
                  <span
                    className={cn(
                      'rounded px-1.5 py-0.5 text-[10px] font-semibold',
                      event.mode === 'PULSE'
                        ? 'bg-indigo-500/15 text-indigo-300'
                        : 'bg-slate-700/40 text-slate-300',
                    )}
                  >
                    {event.mode}
                  </span>
                </td>
                <td className={cn('whitespace-nowrap px-3 py-1 font-semibold', eventColor(event.eventType))}>
                  {event.eventType}
                </td>
                <td className="px-3 py-1">
                  <DetailFragment detail={event.detail} />
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </div>
  );
}
