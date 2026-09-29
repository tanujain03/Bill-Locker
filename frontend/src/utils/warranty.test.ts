import { describe, expect, it } from 'vitest';
import type { WarrantySummary } from '@/types';
import { describeWarranty, formatWarrantyPeriod, warrantyElapsedPercent, warrantyStatusOf } from './warranty';

const warranty = (overrides: Partial<WarrantySummary>): WarrantySummary => ({
  id: 'w1',
  warrantyMonths: 24,
  startDate: '2026-01-01',
  expiryDate: '2027-12-31',
  status: 'ACTIVE',
  daysRemaining: 421,
  ...overrides,
});

describe('describeWarranty', () => {
  it('describes active warranties with days remaining', () => {
    expect(describeWarranty(warranty({ daysRemaining: 421 }))).toBe('Expires in 421 days');
  });

  it('describes the last days', () => {
    expect(describeWarranty(warranty({ status: 'EXPIRING_SOON', daysRemaining: 1 }))).toBe('Expires tomorrow');
    expect(describeWarranty(warranty({ status: 'EXPIRING_SOON', daysRemaining: 0 }))).toBe('Expires today');
  });

  it('describes expired warranties', () => {
    expect(describeWarranty(warranty({ status: 'EXPIRED', daysRemaining: -50 }))).toBe('Expired 50 days ago');
  });

  it('never invents an expiry for unknown warranties', () => {
    expect(describeWarranty(null)).toBe('Warranty period not found');
    expect(describeWarranty(warranty({ status: 'UNKNOWN', expiryDate: null, daysRemaining: null }))).toBe(
      'Warranty period not found',
    );
    expect(warrantyStatusOf(null)).toBe('UNKNOWN');
  });
});

describe('warrantyElapsedPercent', () => {
  it('returns the share of the period already used', () => {
    const value = warrantyElapsedPercent(warranty({ startDate: '2026-01-01', expiryDate: '2026-12-31' }), new Date(2026, 6, 2));
    expect(value).toBeGreaterThanOrEqual(49);
    expect(value).toBeLessThanOrEqual(51);
  });

  it('clamps to 0..100 and handles unknown dates', () => {
    expect(warrantyElapsedPercent(warranty({}), new Date(2030, 0, 1))).toBe(100);
    expect(warrantyElapsedPercent(warranty({}), new Date(2020, 0, 1))).toBe(0);
    expect(warrantyElapsedPercent(warranty({ startDate: null }))).toBeNull();
  });
});

describe('formatWarrantyPeriod', () => {
  it('uses years when possible', () => {
    expect(formatWarrantyPeriod(12)).toBe('1 year');
    expect(formatWarrantyPeriod(24)).toBe('2 years');
    expect(formatWarrantyPeriod(18)).toBe('18 months');
    expect(formatWarrantyPeriod(null)).toBe('—');
  });
});
