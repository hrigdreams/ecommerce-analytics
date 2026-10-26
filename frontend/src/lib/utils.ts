import { type ClassValue, clsx } from 'clsx';
import { twMerge } from 'tailwind-merge';

/**
 * Standard clsx + tailwind-merge combinator. Used throughout components/ui
 * so variant classes and caller-supplied `className` overrides compose
 * safely instead of producing duplicate/conflicting Tailwind utilities.
 */
export function cn(...inputs: ClassValue[]): string {
  return twMerge(clsx(inputs));
}
