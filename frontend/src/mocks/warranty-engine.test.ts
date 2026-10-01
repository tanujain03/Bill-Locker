import { describe, expect, it } from 'vitest';
import { computeWarranty } from './warranty-engine';

const today = new Date(2026, 8, 28); // 28 Sep 2026

describe('computeWarranty (mirrors the backend WarrantyService)', () => {
  it('calculates expiry deterministically', () => {
    const warranty = computeWarranty({ id: 'w', warrantyMonths: 24, startDate: '2026-09-15' }, today);
    expect(warranty?.expiryDate).toBe('2028-09-14');
    expect(warranty?.status).toBe('ACTIVE');
  });

  it('is EXPIRING_SOON within 30 days (inclusive) and on the last day', () => {
    // expires 2026-10-28 → 30 days left
    expect(computeWarranty({ id: 'w', warrantyMonths: 12, startDate: '2025-10-29' }, today)?.status).toBe('EXPIRING_SOON');
    // expires today → 0 days left, still covered
    const lastDay = computeWarranty({ id: 'w', warrantyMonths: 12, startDate: '2025-09-29' }, today);
    expect(lastDay?.daysRemaining).toBe(0);
    expect(lastDay?.status).toBe('EXPIRING_SOON');
  });

  it('is ACTIVE with 31 days left', () => {
    expect(computeWarranty({ id: 'w', warrantyMonths: 12, startDate: '2025-10-30' }, today)?.status).toBe('ACTIVE');
  });

  it('is EXPIRED the day after expiry', () => {
    const expired = computeWarranty({ id: 'w', warrantyMonths: 12, startDate: '2025-09-28' }, today);
    expect(expired?.expiryDate).toBe('2026-09-27');
    expect(expired?.daysRemaining).toBe(-1);
    expect(expired?.status).toBe('EXPIRED');
  });

  it('is UNKNOWN without a period or start date — never guessed', () => {
    expect(computeWarranty({ id: 'w', warrantyMonths: null, startDate: '2026-01-01' }, today)?.status).toBe('UNKNOWN');
    expect(computeWarranty({ id: 'w', warrantyMonths: 12, startDate: null }, today)?.expiryDate).toBeNull();
    expect(computeWarranty(undefined, today)).toBeNull();
  });
});
