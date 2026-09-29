import type { WarrantySummary } from '@/types';
import { calculateWarrantyExpiry, daysUntil } from '@/utils/date';

/** Mirrors the backend rule: expiring soon = 0..30 days left. */
export const EXPIRING_SOON_DAYS = 30;

export interface WarrantyRow {
  id: string;
  warrantyMonths: number | null;
  startDate: string | null;
}

/**
 * Deterministic warranty calculation (never done by the LLM):
 * expiry = start + months − 1 day; status from days remaining vs today.
 */
export function computeWarranty(warranty: WarrantyRow | undefined, today = new Date()): WarrantySummary | null {
  if (!warranty) return null;
  if (!warranty.warrantyMonths || !warranty.startDate) {
    return {
      id: warranty.id,
      warrantyMonths: warranty.warrantyMonths,
      startDate: warranty.startDate,
      expiryDate: null,
      status: 'UNKNOWN',
      daysRemaining: null,
    };
  }
  const expiryDate = calculateWarrantyExpiry(warranty.startDate, warranty.warrantyMonths);
  const daysRemaining = daysUntil(expiryDate, today);
  const status = daysRemaining < 0 ? 'EXPIRED' : daysRemaining <= EXPIRING_SOON_DAYS ? 'EXPIRING_SOON' : 'ACTIVE';
  return {
    id: warranty.id,
    warrantyMonths: warranty.warrantyMonths,
    startDate: warranty.startDate,
    expiryDate,
    status,
    daysRemaining,
  };
}
