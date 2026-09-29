import { FileCheck, Mail, ShieldAlert, ShieldX, Wrench, type LucideIcon } from 'lucide-react';
import { cn } from '@/lib/cn';
import type { AppNotification, NotificationType } from '@/types';
import { formatRelativeTime } from '@/utils/format';

const TYPE_STYLE: Record<NotificationType, { icon: LucideIcon; className: string }> = {
  WARRANTY_EXPIRING: { icon: ShieldAlert, className: 'bg-amber-50 text-amber-600' },
  WARRANTY_EXPIRED: { icon: ShieldX, className: 'bg-rose-50 text-rose-600' },
  SERVICE_DUE: { icon: Wrench, className: 'bg-sky-50 text-sky-600' },
  DOCUMENT_PROCESSED: { icon: FileCheck, className: 'bg-emerald-50 text-emerald-600' },
  GMAIL_BILLS_FOUND: { icon: Mail, className: 'bg-brand-50 text-brand-600' },
};

interface NotificationItemProps {
  notification: AppNotification;
  onOpen: (notification: AppNotification) => void;
}

export function NotificationItem({ notification, onOpen }: NotificationItemProps) {
  const { icon: Icon, className } = TYPE_STYLE[notification.type];
  return (
    <button
      type="button"
      onClick={() => onOpen(notification)}
      className={cn(
        'flex w-full items-start gap-3 rounded-xl px-3 py-3 text-left transition-colors hover:bg-slate-50 focus-visible:bg-slate-50',
        !notification.read && 'bg-brand-50/40',
      )}
    >
      <span className={cn('flex size-9 shrink-0 items-center justify-center rounded-full', className)}>
        <Icon className="size-4" aria-hidden />
      </span>
      <span className="min-w-0 flex-1">
        <span className="flex items-start justify-between gap-2">
          <span className={cn('text-sm', notification.read ? 'font-medium text-slate-700' : 'font-semibold text-slate-900')}>
            {notification.title}
          </span>
          {!notification.read && (
            <span className="mt-1.5 size-2 shrink-0 rounded-full bg-brand-600">
              <span className="sr-only">Unread</span>
            </span>
          )}
        </span>
        <span className="mt-0.5 line-clamp-2 block text-sm text-slate-600">{notification.message}</span>
        <span className="mt-1 block text-xs text-slate-400">{formatRelativeTime(notification.createdAt)}</span>
      </span>
    </button>
  );
}
