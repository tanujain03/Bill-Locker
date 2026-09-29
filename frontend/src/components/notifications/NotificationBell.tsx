import { Bell, BellOff, CheckCheck } from 'lucide-react';
import { useCallback, useEffect, useId, useRef, useState } from 'react';
import { Link } from 'react-router';
import { Skeleton } from '@/components/ui/feedback';
import {
  useMarkAllNotificationsRead,
  useNotifications,
  useOpenNotification,
  useUnreadCount,
} from '@/hooks/useNotifications';
import { cn } from '@/lib/cn';
import { NotificationItem } from './NotificationItem';

/** Top-bar bell with unread badge and a popover of the latest notifications. */
export function NotificationBell() {
  const [open, setOpen] = useState(false);
  const rootRef = useRef<HTMLDivElement>(null);
  const panelId = useId();
  const { data: unread = 0 } = useUnreadCount();
  const notifications = useNotifications({ enabled: open });
  const markAll = useMarkAllNotificationsRead();
  const close = useCallback(() => setOpen(false), []);
  const openNotification = useOpenNotification(close);

  useEffect(() => {
    if (!open) return;
    function onPointerDown(event: PointerEvent) {
      if (!rootRef.current?.contains(event.target as Node)) setOpen(false);
    }
    function onKeyDown(event: KeyboardEvent) {
      if (event.key === 'Escape') setOpen(false);
    }
    document.addEventListener('pointerdown', onPointerDown);
    document.addEventListener('keydown', onKeyDown);
    return () => {
      document.removeEventListener('pointerdown', onPointerDown);
      document.removeEventListener('keydown', onKeyDown);
    };
  }, [open]);

  const latest = notifications.data?.slice(0, 6) ?? [];

  return (
    <div ref={rootRef} className="relative">
      <button
        type="button"
        onClick={() => setOpen((value) => !value)}
        aria-expanded={open}
        aria-controls={open ? panelId : undefined}
        aria-label={unread > 0 ? `Notifications, ${unread} unread` : 'Notifications'}
        className="relative inline-flex size-10 items-center justify-center rounded-lg text-slate-600 transition-colors hover:bg-slate-100 hover:text-slate-900"
      >
        <Bell className="size-5" aria-hidden />
        {unread > 0 && (
          <span className="absolute top-1.5 right-1.5 flex h-4 min-w-4 items-center justify-center rounded-full bg-rose-600 px-1 text-[10px] font-semibold text-white ring-2 ring-white">
            {unread > 9 ? '9+' : unread}
          </span>
        )}
      </button>

      {open && (
        <div
          id={panelId}
          role="dialog"
          aria-label="Notifications"
          className="fixed inset-x-3 top-16 z-40 animate-fade-in rounded-2xl border border-slate-200 bg-white shadow-elevated sm:absolute sm:inset-x-auto sm:top-auto sm:right-0 sm:mt-2 sm:w-96"
        >
          <div className="flex items-center justify-between border-b border-slate-100 px-4 py-3">
            <p className="text-sm font-semibold text-slate-900">Notifications</p>
            <button
              type="button"
              disabled={unread === 0 || markAll.isPending}
              onClick={() => markAll.mutate()}
              className="inline-flex items-center gap-1 text-xs font-medium text-brand-700 hover:text-brand-800 disabled:text-slate-400"
            >
              <CheckCheck className="size-3.5" aria-hidden />
              Mark all read
            </button>
          </div>
          <div className="scrollbar-thin max-h-[60vh] overflow-y-auto p-2">
            {notifications.isPending ? (
              <div className="space-y-3 p-3">
                {[0, 1, 2].map((key) => (
                  <Skeleton key={key} className="h-12 w-full" />
                ))}
              </div>
            ) : notifications.isError ? (
              <p className="p-4 text-center text-sm text-slate-500">Could not load notifications.</p>
            ) : latest.length === 0 ? (
              <div className="flex flex-col items-center gap-2 p-6 text-center text-sm text-slate-500">
                <BellOff className="size-6 text-slate-300" aria-hidden />
                You’re all caught up.
              </div>
            ) : (
              latest.map((notification) => (
                <NotificationItem key={notification.id} notification={notification} onOpen={openNotification} />
              ))
            )}
          </div>
          <Link
            to="/notifications"
            onClick={close}
            className={cn(
              'block border-t border-slate-100 px-4 py-3 text-center text-sm font-medium text-brand-700 hover:bg-slate-50',
              'rounded-b-2xl',
            )}
          >
            View all notifications
          </Link>
        </div>
      )}
    </div>
  );
}
