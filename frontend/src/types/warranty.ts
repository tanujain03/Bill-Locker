import type { ISODate } from './api';

export type WarrantyStatus = 'ACTIVE' | 'EXPIRING_SOON' | 'EXPIRED' | 'UNKNOWN';

export const WARRANTY_STATUSES: WarrantyStatus[] = ['ACTIVE', 'EXPIRING_SOON', 'EXPIRED', 'UNKNOWN'];

/** Warranty data embedded in a product. Dates/status are computed by the backend. */
export interface WarrantySummary {
  id: string;
  warrantyMonths: number | null;
  startDate: ISODate | null;
  expiryDate: ISODate | null;
  status: WarrantyStatus;
  /** Days until expiry (negative once expired); null when unknown. */
  daysRemaining: number | null;
}

export interface Warranty extends WarrantySummary {
  productId: string;
  productName: string;
  productBrand: string | null;
  categoryName: string | null;
  categorySlug: string | null;
  sourceDocumentId: string | null;
}

export interface WarrantyStats {
  total: number;
  active: number;
  expiringSoon: number;
  expired: number;
  unknown: number;
}
