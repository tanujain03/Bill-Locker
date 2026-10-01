import { apiClient } from '@/lib/api-client';
import type { AppNotification, UnreadCount } from '@/types';

export const notificationService = {
  async list(): Promise<AppNotification[]> {
    const { data } = await apiClient.get<AppNotification[]>('/notifications');
    return data;
  },

  async unreadCount(): Promise<number> {
    const { data } = await apiClient.get<UnreadCount>('/notifications/unread-count');
    return data.count;
  },

  async markRead(id: string): Promise<AppNotification> {
    const { data } = await apiClient.patch<AppNotification>(`/notifications/${encodeURIComponent(id)}/read`);
    return data;
  },

  async markAllRead(): Promise<void> {
    await apiClient.post('/notifications/read-all');
  },
};
