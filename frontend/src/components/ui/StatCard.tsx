import type { ReactNode } from 'react';
import { Link } from 'react-router';
import { cn } from '@/lib/cn';
import { Skeleton } from './feedback';

export type StatTone = 'brand' | 'success' | 'warning' | 'danger' | 'neutral';

const ICON_TONES: Record<StatTone, string> = {
  brand: 'bg-brand-50 text-brand-600',
  success: 'bg-emerald-50 text-emerald-600',
  warning: 'bg-amber-50 text-amber-600',
  danger: 'bg-rose-50 text-rose-600',
  neutral: 'bg-slate-100 text-slate-600',
};

interface StatCardProps {
  label: string;
  value: ReactNode;
  icon: ReactNode;
  tone?: StatTone;
  hint?: ReactNode;
  to?: string;
  loading?: boolean;
}

export function StatCard({ label, value, icon, tone = 'neutral', hint, to, loading = false }: StatCardProps) {
  const content = (
    <>
      <div className="flex items-start justify-between gap-3">
        <span className="text-sm font-medium text-slate-500">{label}</span>
        <span
          className={cn('flex size-9 shrink-0 items-center justify-center rounded-xl [&>svg]:size-[18px]', ICON_TONES[tone])}
        >
          {icon}
        </span>
      </div>
      <div className="mt-2 text-2xl font-semibold tracking-tight text-slate-900">
        {loading ? <Skeleton className="h-8 w-20" /> : value}
      </div>
      {hint && <div className="mt-1 text-xs text-slate-500">{hint}</div>}
    </>
  );

  const className = 'block rounded-2xl border border-slate-200/80 bg-white p-4 shadow-card sm:p-5';
  if (to) {
    return (
      <Link to={to} className={cn(className, 'transition hover:-translate-y-px hover:border-slate-300 hover:shadow-md')}>
        {content}
      </Link>
    );
  }
  return <div className={className}>{content}</div>;
}
