import { Check, Copy } from 'lucide-react';
import { useState } from 'react';
import { cn } from '@/lib/cn';
import { initials } from '@/utils/format';

export type ProgressTone = 'brand' | 'success' | 'warning' | 'danger' | 'neutral';

/** Meter: the unfilled track is a lighter step of the fill's own hue. */
const TRACK: Record<ProgressTone, string> = {
  brand: 'bg-brand-100',
  success: 'bg-emerald-100',
  warning: 'bg-amber-100',
  danger: 'bg-rose-100',
  neutral: 'bg-slate-100',
};
const FILL: Record<ProgressTone, string> = {
  brand: 'bg-brand-600',
  success: 'bg-emerald-500',
  warning: 'bg-amber-500',
  danger: 'bg-rose-500',
  neutral: 'bg-slate-400',
};

interface ProgressBarProps {
  value: number;
  tone?: ProgressTone;
  label: string;
  className?: string;
}

export function ProgressBar({ value, tone = 'brand', label, className }: ProgressBarProps) {
  const clamped = Math.min(100, Math.max(0, value));
  return (
    <div
      role="progressbar"
      aria-label={label}
      aria-valuemin={0}
      aria-valuemax={100}
      aria-valuenow={Math.round(clamped)}
      className={cn('h-1.5 w-full overflow-hidden rounded-full', TRACK[tone], className)}
    >
      <div className={cn('h-full rounded-full transition-[width] duration-500', FILL[tone])} style={{ width: `${clamped}%` }} />
    </div>
  );
}

export function Avatar({ name, className }: { name: string | null | undefined; className?: string }) {
  return (
    <span
      aria-hidden
      className={cn(
        'inline-flex size-9 shrink-0 items-center justify-center rounded-full bg-brand-100 text-sm font-semibold text-brand-700',
        className,
      )}
    >
      {initials(name)}
    </span>
  );
}

interface FilterTabsProps<T extends string> {
  value: T;
  onChange: (value: T) => void;
  options: { value: T; label: string; count?: number }[];
  label: string;
  className?: string;
}

/** Segmented filter control (toggle buttons with aria-pressed). */
export function FilterTabs<T extends string>({ value, onChange, options, label, className }: FilterTabsProps<T>) {
  return (
    <div
      role="group"
      aria-label={label}
      className={cn('scrollbar-thin inline-flex max-w-full gap-1 overflow-x-auto rounded-xl bg-slate-100 p-1', className)}
    >
      {options.map((option) => {
        const selected = option.value === value;
        return (
          <button
            key={option.value}
            type="button"
            aria-pressed={selected}
            onClick={() => onChange(option.value)}
            className={cn(
              'inline-flex shrink-0 items-center gap-1.5 whitespace-nowrap rounded-lg px-3 py-1.5 text-sm font-medium transition-colors',
              selected ? 'bg-white text-slate-900 shadow-sm' : 'text-slate-600 hover:text-slate-900',
            )}
          >
            {option.label}
            {option.count !== undefined && (
              <span
                className={cn(
                  'tabular rounded-full px-1.5 text-xs',
                  selected ? 'bg-slate-100 text-slate-700' : 'bg-slate-200/80 text-slate-600',
                )}
              >
                {option.count}
              </span>
            )}
          </button>
        );
      })}
    </div>
  );
}

interface SwitchProps {
  checked: boolean;
  onChange: (checked: boolean) => void;
  label: string;
  disabled?: boolean;
}

export function Switch({ checked, onChange, label, disabled }: SwitchProps) {
  return (
    <button
      type="button"
      role="switch"
      aria-checked={checked}
      aria-label={label}
      disabled={disabled}
      onClick={() => onChange(!checked)}
      className={cn(
        'relative inline-flex h-6 w-11 shrink-0 items-center rounded-full transition-colors disabled:opacity-50',
        checked ? 'bg-brand-600' : 'bg-slate-300',
      )}
    >
      <span
        className={cn(
          'inline-block size-5 rounded-full bg-white shadow transition-transform',
          checked ? 'translate-x-5.5' : 'translate-x-0.5',
        )}
      />
    </button>
  );
}

export function CopyButton({ value, label }: { value: string; label: string }) {
  const [copied, setCopied] = useState(false);

  async function copy() {
    try {
      await navigator.clipboard.writeText(value);
      setCopied(true);
      window.setTimeout(() => setCopied(false), 1500);
    } catch {
      // Clipboard access denied — the value is still visible to copy manually.
    }
  }

  return (
    <button
      type="button"
      onClick={copy}
      aria-label={copied ? 'Copied' : label}
      title={copied ? 'Copied' : label}
      className="inline-flex size-7 items-center justify-center rounded-md text-slate-400 transition-colors hover:bg-slate-100 hover:text-slate-700"
    >
      {copied ? <Check className="size-3.5 text-emerald-600" aria-hidden /> : <Copy className="size-3.5" aria-hidden />}
    </button>
  );
}
