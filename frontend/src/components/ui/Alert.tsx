import { CircleAlert, CircleCheck, Info, TriangleAlert } from 'lucide-react';
import type { ReactNode } from 'react';
import { cn } from '@/lib/cn';

type AlertTone = 'info' | 'success' | 'warning' | 'danger';

const TONES: Record<AlertTone, { className: string; icon: ReactNode }> = {
  info: { className: 'border-brand-100 bg-brand-50 text-brand-900', icon: <Info className="size-4 text-brand-600" aria-hidden /> },
  success: {
    className: 'border-emerald-100 bg-emerald-50 text-emerald-900',
    icon: <CircleCheck className="size-4 text-emerald-600" aria-hidden />,
  },
  warning: {
    className: 'border-amber-200 bg-amber-50 text-amber-900',
    icon: <TriangleAlert className="size-4 text-amber-600" aria-hidden />,
  },
  danger: { className: 'border-rose-200 bg-rose-50 text-rose-900', icon: <CircleAlert className="size-4 text-rose-600" aria-hidden /> },
};

interface AlertProps {
  tone?: AlertTone;
  title?: ReactNode;
  children?: ReactNode;
  action?: ReactNode;
  className?: string;
}

export function Alert({ tone = 'info', title, children, action, className }: AlertProps) {
  const { className: toneClass, icon } = TONES[tone];
  return (
    <div
      role={tone === 'danger' ? 'alert' : 'status'}
      className={cn('flex items-start gap-3 rounded-xl border px-4 py-3 text-sm', toneClass, className)}
    >
      <span className="mt-0.5 shrink-0">{icon}</span>
      <div className="min-w-0 flex-1">
        {title && <p className="font-semibold">{title}</p>}
        {children && <div className={cn(title && 'mt-0.5', 'opacity-90')}>{children}</div>}
      </div>
      {action && <div className="shrink-0 self-center">{action}</div>}
    </div>
  );
}
