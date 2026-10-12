/**
 * Warranties: every product on your saved bills and how its warranty stands
 * today. The backend works the status out (WarrantyRules); we only show it.
 */
import { api } from './api';

/** ACTIVE: more than 30 days left. EXPIRING_SOON: 0–30 days. NO_INFO: no end date known. */
export type WarrantyStatus = 'ACTIVE' | 'EXPIRING_SOON' | 'EXPIRED' | 'NO_INFO';

export type WarrantyView = {
  documentId: string;
  productName: string | null;
  modelNumber: string | null;
  serialNumber: string | null;
  sellerName: string | null;
  warrantyProvider: string | null;
  purchaseDate: string | null;
  startDate: string | null;
  /** The printed end date, or start + months − 1 day. */
  endDate: string | null;
  /** Negative once expired; null when the end is unknown. */
  daysLeft: number | null;
  status: WarrantyStatus;
  /** The brand's warranty registration page (task 5), or null. */
  registrationUrl: string | null;
};

export type WarrantyCounts = { all: number; active: number; expiringSoon: number; expired: number; noInfo: number };

export type WarrantyList = { counts: WarrantyCounts; items: WarrantyView[] };

export const WARRANTY_STATUS_LABELS: Record<WarrantyStatus, string> = {
  ACTIVE: 'Active',
  EXPIRING_SOON: 'Expiring soon',
  EXPIRED: 'Expired',
  NO_INFO: 'No info',
};

/** The tab order on the warranties page and the dashboard legend. */
export const WARRANTY_STATUSES: WarrantyStatus[] = ['EXPIRING_SOON', 'ACTIVE', 'EXPIRED', 'NO_INFO'];

export function listWarranties(params: { status?: WarrantyStatus; q?: string }): Promise<WarrantyList> {
  const query = new URLSearchParams();
  if (params.status) query.set('status', params.status);
  if (params.q?.trim()) query.set('q', params.q.trim());
  const suffix = query.toString();
  return api<WarrantyList>(`/warranties${suffix ? `?${suffix}` : ''}`);
}

/** Link to the warranties page, optionally on one tab. */
export const warrantiesLink = (status?: WarrantyStatus) => (status ? `/warranties?status=${status}` : '/warranties');
