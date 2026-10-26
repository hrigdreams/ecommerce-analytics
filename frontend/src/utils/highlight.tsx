import type { ReactNode } from 'react';

/**

 * Splits the query on whitespace and highlights each non-empty term
 * independently (so "wireless mouse" bolds both words wherever they
 * appear, not just as one exact phrase), case-insensitively.
 */
export function highlightMatch(text: string, query: string): ReactNode {
  const terms = query
    .trim()
    .split(/\s+/)
    .filter(Boolean)
    .map((t) => t.toLowerCase());

  if (terms.length === 0) return text;

  // Single regex alternation over all terms, so overlapping/adjacent
  // matches are handled in one pass instead of nested replacements that
  // could double-wrap a substring matched by two different terms.
  const pattern = new RegExp(`(${terms.map(escapeRegExp).join('|')})`, 'gi');
  const parts = text.split(pattern);

  if (parts.length === 1) return text;

  return parts.map((part, i) =>
    terms.includes(part.toLowerCase()) ? (
      <mark key={i} className="rounded-sm bg-amber-200/70 px-0.5 text-inherit">
        {part}
      </mark>
    ) : (
      <span key={i}>{part}</span>
    ),
  );
}

function escapeRegExp(s: string): string {
  return s.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
}
