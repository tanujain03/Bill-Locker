import { BellOff, CheckCheck } from 'lucide-react';
import { useState } from 'react';
import { NotificationItem } from '@/components/notifications/NotificationItem';
import { Button } from '@/components/ui/Button';
import { Card } from '@/components/ui/Card';
import { EmptyState, ErrorState, Skeleton } from '@/components/ui/feedback';
import { FilterTabs } from '@/components/ui/misc';
import { PageHeader } from '@/components/ui/PageHeader';
import { useToast } from '@/components/ui/toast-context';
import { useDocumentTitle } from '@/hooks/useDocumentTitle';
import { useMarkAllNotificationsRead, useNotifications, useOpenNotification } from '@/hooks/useNotifications';
import { getErrorMessage } from '@/lib/api-client';

export function NotificationsPage() {
  useDocumentTitle('Notifications');
  const notifications = useNotifications();
  const markAll = useMarkAllNotificationsRead();
  const openNotification = useOpenNotification();
  const toast = useToast();
  const [tab, setTab] = useState<'all' | 'unread'>('all');

  const list = notifications.data ?? [];
  const unread = list.filter((notification) => !notification.read);
  const visible = tab === 'unread' ? unread : list;

  async function markAllRead() {
    try {
      await markAll.mutateAsync();
    } catch (error) {
      toast.error('Could not mark notifications as read', getErrorMessage(error));
    }
  }

  return (
    <div className="mx-auto max-w-3xl">
      <PageHeader
        title="Notifications"
        description="Warranty expiries, services due and documents ready for review."
        actions={
          <Button
            variant="secondary"
            onClick={() => void markAllRead()}
            disabled={unread.length === 0}
            loading={markAll.isPending}
            leftIcon={<CheckCheck className="size-4" aria-hidden />}
          >
            Mark all as read
          </Button>
        }
      />

      <FilterTabs
        label="Filter notifications"
        value={tab}
        onChange={setTab}
        className="mb-4"
        options={[
          { value: 'all', label: 'All', count: list.length },
          { value: 'unread', label: 'Unread', count: unread.length },
        ]}
      />

      <Card className="p-2">
        {notifications.isPending ? (
          <div className="space-y-3 p-3" aria-busy="true" aria-label="Loading notifications">
            {Array.from({ length: 4 }, (_, index) => (
              <Skeleton key={index} className="h-16 w-full" />
            ))}
          </div>
        ) : notifications.isError ? (
          <ErrorState title="Could not load notifications" error={notifications.error} onRetry={() => void notifications.refetch()} />
        ) : visible.length === 0 ? (
          <EmptyState
            icon={<BellOff aria-hidden />}
            title={tab === 'unread' ? 'No unread notifications' : 'You’re all caught up'}
            description="We’ll let you know before a warranty expires or a service is due."
          />
        ) : (
          <ul className="space-y-1">
            {visible.map((notification) => (
              <li key={notification.id}>
                <NotificationItem notification={notification} onOpen={openNotification} />
              </li>
            ))}
          </ul>
        )}
      </Card>
    </div>
  );
}
