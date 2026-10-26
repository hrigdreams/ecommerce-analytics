
export const APP_TIMEZONE = 'Asia/Kathmandu';

/** Guaranteed non-NaN fallback for anything `parseFlexibleTimestamp` cannot make sense of. Never shown as a real time (see `safeFormat`) -- exists purely so arithmetic on the result (`.getTime()`, sort comparators) can never produce `NaN`. */
export const UNPARSEABLE_TIMESTAMP = new Date(0);

const timeFormatter = new Intl.DateTimeFormat('en-GB', {
  timeZone: APP_TIMEZONE,
  hour: '2-digit',
  minute: '2-digit',
  second: '2-digit',
  hour12: false,
});

const minuteFormatter = new Intl.DateTimeFormat('en-GB', {
  timeZone: APP_TIMEZONE,
  hour: '2-digit',
  minute: '2-digit',
  hour12: false,
});

const dayLabelFormatter = new Intl.DateTimeFormat('en-US', {
  timeZone: APP_TIMEZONE,
  month: 'short',
  day: 'numeric',
});

/** USD currency formatter for revenue figures. Whole-dollar precision -- these are aggregate revenue sums, not unit prices, so cents are noise at this scale. */
export function formatCurrency(value: number): string {
  return new Intl.NumberFormat('en-US', {
    style: 'currency',
    currency: 'USD',
    maximumFractionDigits: 0,
  }).format(value);
}

/** Compact number formatter for high-cardinality counters (salesCount, popularityScore) -- e.g. 5200 -> "5.2K", 1_300_000 -> "1.3M". */
export function formatCompactNumber(value: number): string {
  return new Intl.NumberFormat('en-US', {
    notation: 'compact',
    maximumFractionDigits: 1,
  }).format(value);
}

/**
 * Anything a timestamp might arrive as in this app: the backend's two
 * observed string shapes (`2026-10-17T07:28:00.589257`,
 * `2026-10-17 07:28:00`), an ISO string with an explicit offset or `Z`, a
 * raw Unix timestamp (Recharts sometimes hands a formatter the numeric
 * axis/domain value instead of the original data string), or a native
 * `Date` (also something Recharts can pass through a tickFormatter).
 */
export type TimestampInput = string | number | Date | null | undefined;

/**
 * Matches the backend's naive shapes (`T` or space separator, optional
 * fractional seconds up to microseconds/nanoseconds, optional `Z`/±offset
 * suffix) so they can be rewritten into the one ISO form every JS engine
 * parses identically before being handed to `Date`.
 */
const TIMESTAMP_PATTERN =
  /^(\d{4})-(\d{2})-(\d{2})[T ](\d{2}):(\d{2}):(\d{2})(?:\.(\d{1,9}))?(Z|[+-]\d{2}:?\d{2})?$/;

/**
 * Rewrites a recognized timestamp string into a canonical, spec-conformant
 * ISO string: `YYYY-MM-DDTHH:mm:ss.sss` followed by `Z` (if the input had no
 * zone info -- see the file-level doc comment for why naive strings are
 * treated as UTC) or its original offset, colon-normalized. Returns `null`
 * if `raw` doesn't match the recognized shape at all.
 */
function normalizeToCanonicalUtcIso(raw: string): string | null {
  const match = raw.match(TIMESTAMP_PATTERN);
  if (!match) return null;

  const [, y, mo, d, h, mi, s, fraction, offset] = match;
  const ms = (fraction ? fraction.slice(0, 3) : '000').padEnd(3, '0');
  const canonical = `${y}-${mo}-${d}T${h}:${mi}:${s}.${ms}`;

  if (!offset || offset === 'Z') {
    // No zone suffix at all -- this backend's Spring/Spark timestamps are
    // written as naive UTC instants (an `Instant`/`LocalDateTime`
    // serialized without a zone). Forcing `Z` here is the single most
    // important line in this file: it's what makes every engine agree to
    // parse this as UTC instead of (browser-dependent) local time.
    return `${canonical}Z`;
  }

  // Explicit offset already present (e.g. "+0545" or "+05:45") -- normalize
  // to the colon-delimited form; native Date parsing of an explicitly
  // zone-suffixed canonical ISO string is reliable on every engine.
  const normalizedOffset = offset.length === 5 ? `${offset.slice(0, 3)}:${offset.slice(3)}` : offset;
  return `${canonical}${normalizedOffset}`;
}

function warnAndFallback(input: unknown): Date {
  if (import.meta.env.DEV) {
    // eslint-disable-next-line no-console
    console.warn('[formatters] Could not parse timestamp, falling back to epoch:', input);
  }
  return UNPARSEABLE_TIMESTAMP;
}

/**
 * Bulletproof parse of any shape this app's data sources produce (backend
 * timestamp strings, Recharts-supplied numbers or `Date`s) into a `Date`.
 * **Always returns a valid `Date`** -- never `null`, never something whose
 * `.getTime()` is `NaN` -- so callers that need it for sorting or arithmetic
 * (see `EventVelocityChart.tsx`, `CatalogPage.tsx`) can use the result
 * directly without a null-check, and a sort comparator built on this can
 * never return `NaN` and destabilize the sort.
 */
export function parseFlexibleTimestamp(input: TimestampInput): Date {
  if (input instanceof Date) {
    return Number.isNaN(input.getTime()) ? warnAndFallback(input) : input;
  }

  if (typeof input === 'number') {
    if (!Number.isFinite(input)) return warnAndFallback(input);
    // A Unix timestamp under ~1e12 in magnitude is almost certainly
    // seconds, not milliseconds (that threshold is roughly September 2001
    // in ms terms) -- some axis/chart code normalizes to epoch seconds, so
    // this scales it up rather than producing a date in 1970.
    const ms = Math.abs(input) < 1e12 ? input * 1000 : input;
    const date = new Date(ms);
    return Number.isNaN(date.getTime()) ? warnAndFallback(input) : date;
  }

  if (typeof input !== 'string') return warnAndFallback(input);
  const raw = input.trim();
  if (!raw) return warnAndFallback(input);

  const canonical = normalizeToCanonicalUtcIso(raw);
  if (canonical) {
    const date = new Date(canonical);
    if (!Number.isNaN(date.getTime())) return date;
  }

  // Last-resort native parse for any shape the pattern above doesn't
  // recognize (a bare date, some other ISO variant) -- still validated
  // before being trusted.
  const fallback = new Date(raw);
  return Number.isNaN(fallback.getTime()) ? warnAndFallback(input) : fallback;
}

/**
 * Runs `formatter` against a safely-parsed date. Catches both the
 * epoch-fallback sentinel (a genuinely unparseable input) and any
 * unexpected exception from `formatter` itself, rendering `fallback`
 * instead -- so one bad data point shows a visible placeholder rather than
 * throwing out of a Recharts tickFormatter/labelFormatter and freezing
 * that chart's whole render loop.
 */
function safeFormat(input: TimestampInput, formatter: (date: Date) => string, fallback = '--'): string {
  try {
    const date = parseFlexibleTimestamp(input);
    if (date.getTime() === UNPARSEABLE_TIMESTAMP.getTime()) return fallback;
    return formatter(date);
  } catch {
    return fallback;
  }
}

/** Axis/table label for a Spark batch trend day, e.g. "2026-10-17" -> "Oct 17" (Kathmandu calendar day). */
export function formatDayLabel(input: TimestampInput): string {
  return safeFormat(input, (d) => dayLabelFormatter.format(d));
}

/** Axis label for a live 1-minute sliding window timestamp, e.g. "13:13" -- strictly 24-hour, Kathmandu wall clock. */
export function formatWindowTime(input: TimestampInput): string {
  return safeFormat(input, (d) => minuteFormatter.format(d));
}

/** Full timestamp for tooltips, e.g. "Oct 17, 13:13:00" -- strictly 24-hour, Kathmandu wall clock. */
export function formatFullTimestamp(input: TimestampInput): string {
  return safeFormat(input, (d) => `${dayLabelFormatter.format(d)}, ${timeFormatter.format(d)}`);
}

/**
 * Parses a backend timestamp (any shape -- see `TimestampInput`) into a
 * `Date`. Kept as its own export since this is the name the rest of the app
 * already calls it by; it's a direct alias of `parseFlexibleTimestamp`
 * (same guaranteed-non-NaN contract).
 */
export function parseSparkUtcDate(input: TimestampInput): Date {
  return parseFlexibleTimestamp(input);
}

/** Local (Kathmandu) wall-clock time with second-level resolution, e.g. "13:13:00" -- strictly 24-hour -- used by the Live Transaction Feed. */
export function formatLocalTime(input: TimestampInput): string {
  return safeFormat(input, (d) => timeFormatter.format(d));
}

/**
 * Parses a RecentSimEvent.detail string (e.g.
 * "orderId=482 userId=17 total=129.50 status=PENDING") into key/value
 * pairs. The backend writes this as a flat, pre-formatted string, so this
 * is a simple whitespace/`=`-split, deliberately tolerant -- any token
 * without `=` is skipped rather than thrown on, since the exact fields
 * present differ per event type.
 */
export function parseEventDetail(detail: string): Record<string, string> {
  const fields: Record<string, string> = {};
  for (const token of detail.split(/\s+/)) {
    const eq = token.indexOf('=');
    if (eq <= 0) continue;
    fields[token.slice(0, eq)] = token.slice(eq + 1);
  }
  return fields;
}
