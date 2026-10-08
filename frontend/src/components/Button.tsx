import { LoaderCircle, type LucideIcon } from 'lucide-react';
import type { ButtonHTMLAttributes } from 'react';

/**
 * One look for every button in the app.
 * - primary: the one main action on a screen (filled indigo)
 * - secondary: other actions (white with a border)
 * - ghost: quiet actions in rows and toolbars (text only)
 * - danger: confirms something destructive (filled red), used in dialogs
 * - dangerGhost: a destructive action that sits among others (red text only)
 */
export type ButtonVariant = 'primary' | 'secondary' | 'ghost' | 'danger' | 'dangerGhost';
/** md = 40px high (most buttons); sm = 32px (inside rows and lists). */
export type ButtonSize = 'sm' | 'md';

const VARIANTS: Record<ButtonVariant, string> = {
  primary: 'bg-brand-600 font-semibold text-white shadow-sm hover:bg-brand-700',
  secondary: 'border border-slate-300 bg-white font-medium text-slate-700 shadow-xs hover:bg-slate-50',
  ghost: 'font-medium text-slate-700 hover:bg-slate-100',
  danger: 'bg-rose-600 font-semibold text-white shadow-sm hover:bg-rose-700',
  dangerGhost: 'font-medium text-rose-700 hover:bg-rose-50',
};

const SIZES: Record<ButtonSize, string> = {
  sm: 'gap-1.5 px-3 py-1.5',
  md: 'gap-2 px-4 py-2.5',
};

/** The classes, for things that look like a button but are links (<Link className={buttonClass(…)}>). */
export function buttonClass({ variant = 'secondary', size = 'md', full = false } = {} as {
  variant?: ButtonVariant;
  size?: ButtonSize;
  full?: boolean;
}) {
  return `inline-flex items-center justify-center rounded-lg text-sm whitespace-nowrap transition disabled:pointer-events-none disabled:opacity-60 ${
    VARIANTS[variant]
  } ${SIZES[size]} ${full ? 'w-full' : ''}`;
}

type Props = ButtonHTMLAttributes<HTMLButtonElement> & {
  variant?: ButtonVariant;
  size?: ButtonSize;
  /** Full width. */
  full?: boolean;
  /** Shown before the label; a spinner takes its place while busy, so the width stays the same. */
  icon?: LucideIcon;
  /** Working on it: spinner, can't be clicked. Pass a label like "Saving…" as children. */
  busy?: boolean;
};

export function Button({ variant, size, full, icon: Icon, busy, disabled, className = '', children, type = 'button', ...rest }: Props) {
  return (
    <button
      type={type}
      disabled={disabled || busy}
      aria-busy={busy || undefined}
      className={`${buttonClass({ variant, size, full })} ${className}`}
      {...rest}
    >
      {busy ? (
        <LoaderCircle className="size-4 shrink-0 animate-spin" aria-hidden />
      ) : (
        Icon && <Icon className="size-4 shrink-0" aria-hidden />
      )}
      {children}
    </button>
  );
}
