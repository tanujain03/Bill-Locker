import type { ISODateTime } from './api';

export type NotificationType =
  | 'WARRANTY_EXPIRING'
  | 'WARRANTY_EXPIRED'
  | 'SERVICE_DUE'
  | 'DOCUMENT_PROCESSED'
  | 'GMAIL_BILLS_FOUND';

/** Named `AppNotification` to avoid clashing with the DOM `Notification` type. */
export interface AppNotification {
  id: string;
  productId: string | null;
  documentId: string | null;
  type: NotificationType;
  title: string;
  message: string;
  scheduledAt: ISODateTime;
  read: boolean;
  createdAt: ISODateTime;
}

export interface UnreadCount {
  count: number;
}
