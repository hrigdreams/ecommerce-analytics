import type { HTMLAttributes, ReactNode } from 'react';
import { cn } from '../../lib/utils';

type AccentColor = 'indigo' | 'emerald' | 'slate' | 'rose' | 'amber' | 'none';

const ACCENT_CLASSES: Record<AccentColor, string> = {
  indigo: 'border-l-indigo-500',
  emerald: 'border-l-emerald-500',
  slate: 'border-l-slate-400',
  rose: 'border-l-rose-500',
  amber: 'border-l-amber-500',
  none: 'border-l-transparent',
};

interface CardProps extends HTMLAttributes<HTMLDivElement> {
  /**
   * Thin left-edge accent bar used to signal a card's data domain at a
   * glance (indigo = live/streaming, emerald = catalog/stock,
   * slate = historical/batch) instead of giving every card an identical
   * shadow with no hierarchy between them.
   */
  accent?: AccentColor;
}

export function Card({ accent = 'none', className, children, ...props }: CardProps) {
  return (
    <div
      className={cn(
        'rounded-lg border border-slate-200 bg-white',
        accent !== 'none' && 'border-l-4',
        ACCENT_CLASSES[accent],
        className,
      )}
      {...props}
    >
      {children}
    </div>
  );
}

export function CardHeader({
  title,
  description,
  action,
  className,
}: {
  title: string;
  description?: string;
  action?: ReactNode;
  className?: string;
}) {
  return (
    <div className={cn('flex items-start justify-between gap-4 px-5 pt-5', className)}>
      <div>
        <h3 className="text-sm font-semibold text-slate-900">{title}</h3>
        {description && <p className="mt-0.5 text-sm text-slate-500">{description}</p>}
      </div>
      {action}
    </div>
  );
}

export function CardBody({ className, children }: { className?: string; children: ReactNode }) {
  return <div className={cn('p-5', className)}>{children}</div>;
}
