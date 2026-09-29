import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useCallback } from 'react';
import { useNavigate } from 'react-router';
import { queryKeys } from '@/lib/query-keys';
import { notificationService } from '@/services/notification.service';
import type { AppNotification } from '@/types';

export function useNotifications({ enabled = true }: { enabled?: boolean } = {}) {
  return useQuery({
    queryKey: queryKeys.notifications.list,
    queryFn: notificationService.list,
    enabled,
  });
}

/** Where a notification leads when clicked. */
export function notificationTarget(notification: AppNotification): string | null {
  if (notification.type === 'DOCUMENT_PROCESSED' && notification.documentId) return `/documents/${notification.documentId}`;
  if (notification.type === 'GMAIL_BILLS_FOUND') return '/gmail';
  if (notification.productId) return `/products/${notification.productId}`;
  return null;
}

/** Marks a notification as read and navigates to what it is about. */
export function useOpenNotification(onNavigate?: () => void) {
  const navigate = useNavigate();
  const markRead = useMarkNotificationRead();
  return useCallback(
    (notification: AppNotification) => {
      if (!notification.read) markRead.mutate(notification.id);
      const target = notificationTarget(notification);
      if (target) {
        onNavigate?.();
        navigate(target);
      }
    },
    [markRead, navigate, onNavigate],
  );
}

export function useUnreadCount() {
  return useQuery({
    queryKey: queryKeys.notifications.unreadCount,
    queryFn: notificationService.unreadCount,
    refetchInterval: 30_000,
  });
}

export function useMarkNotificationRead() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (id: string) => notificationService.markRead(id),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: queryKeys.notifications.all }),
  });
}

export function useMarkAllNotificationsRead() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: notificationService.markAllRead,
    onSuccess: () => queryClient.invalidateQueries({ queryKey: queryKeys.notifications.all }),
  });
}
