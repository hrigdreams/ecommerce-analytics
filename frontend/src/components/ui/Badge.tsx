import type { HTMLAttributes } from 'react';
import { cn } from '../../lib/utils';

type BadgeVariant = 'success' | 'destructive' | 'neutral' | 'warning' | 'live';

interface BadgeProps extends HTMLAttributes<HTMLSpanElement> {
  variant?: BadgeVariant;
}

const VARIANT_CLASSES: Record<BadgeVariant, string> = {
  success: 'bg-emerald-50 text-emerald-700 ring-1 ring-inset ring-emerald-200',
  destructive: 'bg-rose-50 text-rose-700 ring-1 ring-inset ring-rose-200',
  neutral: 'bg-slate-100 text-slate-600 ring-1 ring-inset ring-slate-200',
  warning: 'bg-amber-50 text-amber-700 ring-1 ring-inset ring-amber-200',
  live: 'bg-indigo-50 text-indigo-700 ring-1 ring-inset ring-indigo-200',
};

/**
 * Small status pill. `success` / `destructive` map directly to the
 * in-stock / out-of-stock states CatalogPage renders per product; `live`
 * is used for the streaming-connection indicator in the header.
 */
export function Badge({ variant = 'neutral', className, children, ...props }: BadgeProps) {
  return (
    <span
      className={cn(
        'inline-flex items-center gap-1.5 rounded-full px-2.5 py-0.5 text-xs font-medium',
        VARIANT_CLASSES[variant],
        className,
      )}
      {...props}
    >
      {children}
    </span>
  );
}

/** Convenience wrapper: renders the correct Badge for a product's stock state. */
export function StockBadge({ inStock }: { inStock: boolean }) {
  return (
    <Badge variant={inStock ? 'success' : 'destructive'}>
      <span
        className={cn(
          'h-1.5 w-1.5 rounded-full',
          inStock ? 'bg-emerald-500' : 'bg-rose-500',
        )}
      />
      {inStock ? 'In stock' : 'Out of stock'}
    </Badge>
  );
}
