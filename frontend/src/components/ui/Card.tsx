import type { ComponentProps, ReactNode } from 'react';
import { cn } from '@/lib/cn';

export function Card({ className, ...props }: ComponentProps<'div'>) {
  return <div className={cn('rounded-2xl border border-slate-200/80 bg-white shadow-card', className)} {...props} />;
}

interface CardHeaderProps {
  title: ReactNode;
  description?: ReactNode;
  action?: ReactNode;
  icon?: ReactNode;
  className?: string;
  /** Heading level; pages own the h1, so cards default to h2. */
  as?: 'h2' | 'h3';
}

export function CardHeader({ title, description, action, icon, className, as: Heading = 'h2' }: CardHeaderProps) {
  return (
    <div className={cn('flex items-start justify-between gap-4 px-5 pt-5 sm:px-6', className)}>
      <div className="min-w-0">
        <Heading className="flex items-center gap-2 text-base font-semibold text-slate-900">
          {icon}
          {title}
        </Heading>
        {description && <p className="mt-1 text-sm text-slate-500">{description}</p>}
      </div>
      {action && <div className="shrink-0">{action}</div>}
    </div>
  );
}

export function CardBody({ className, ...props }: ComponentProps<'div'>) {
  return <div className={cn('px-5 py-5 sm:px-6', className)} {...props} />;
}

export function CardFooter({ className, ...props }: ComponentProps<'div'>) {
  return <div className={cn('border-t border-slate-100 px-5 py-4 sm:px-6', className)} {...props} />;
}
