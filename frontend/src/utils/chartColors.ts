/**

 * 15 entries -- one per product category, so the Category Revenue Share
 * donut never has to repeat a color across its 15 slices. Chosen for
 * mutual distinctness at adjacent positions (no two neighbors in the same
 * hue family) rather than a smooth gradient, since this is categorical
 * data with no inherent order.
 */
export const CATEGORICAL_PALETTE = [
  '#6366f1', // indigo
  '#10b981', // emerald
  '#f59e0b', // amber
  '#ef4444', // red
  '#06b6d4', // cyan
  '#8b5cf6', // violet
  '#ec4899', // pink
  '#84cc16', // lime
  '#f97316', // orange
  '#14b8a6', // teal
  '#a855f7', // purple
  '#eab308', // yellow
  '#f43f5e', // rose
  '#0ea5e9', // sky
  '#22c55e', // green
] as const;

export function colorForIndex(index: number): string {
  return CATEGORICAL_PALETTE[index % CATEGORICAL_PALETTE.length];
}

/**
 * Stable color-per-key lookup (e.g. per event_type in the live event
 * velocity chart), so the same key always renders in the same color across
 * re-renders/re-sorts instead of shifting when the set of keys present in a
 * given polling window changes order.
 */
export function colorForKey(key: string, knownKeys: string[]): string {
  const index = knownKeys.indexOf(key);
  return colorForIndex(index === -1 ? 0 : index);
}
