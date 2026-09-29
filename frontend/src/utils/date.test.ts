import { describe, expect, it } from 'vitest';
import { addMonths, calculateWarrantyExpiry, daysUntil, isValidISODate, parseISODate, toISODate } from './date';

describe('calculateWarrantyExpiry', () => {
  it('adds the warranty period and subtracts one day (spec example)', () => {
    expect(calculateWarrantyExpiry('2026-09-15', 24)).toBe('2028-09-14');
  });

  it('handles a 12 month warranty', () => {
    expect(calculateWarrantyExpiry('2026-01-10', 12)).toBe('2027-01-09');
  });

  it('clamps to the last day of shorter months like Java LocalDate.plusMonths', () => {
    // 31 Jan + 1 month = 28 Feb (non-leap) → expiry 27 Feb
    expect(calculateWarrantyExpiry('2026-01-31', 1)).toBe('2026-02-27');
  });

  it('handles leap days', () => {
    // 29 Feb 2024 + 12 months = 28 Feb 2025 → expiry 27 Feb 2025
    expect(calculateWarrantyExpiry('2024-02-29', 12)).toBe('2025-02-27');
  });
});

describe('date helpers', () => {
  it('parses ISO dates as local calendar dates', () => {
    const date = parseISODate('2026-06-15');
    expect(date.getFullYear()).toBe(2026);
    expect(date.getMonth()).toBe(5);
    expect(date.getDate()).toBe(15);
    expect(toISODate(date)).toBe('2026-06-15');
  });

  it('counts whole days until a date', () => {
    const from = new Date(2026, 8, 28, 18, 30);
    expect(daysUntil('2026-09-28', from)).toBe(0);
    expect(daysUntil('2026-10-28', from)).toBe(30);
    expect(daysUntil('2026-09-20', from)).toBe(-8);
  });

  it('adds months across year boundaries', () => {
    expect(toISODate(addMonths(parseISODate('2026-11-30'), 3))).toBe('2027-02-28');
  });

  it('validates ISO date strings', () => {
    expect(isValidISODate('2026-02-28')).toBe(true);
    expect(isValidISODate('2026-02-30')).toBe(false);
    expect(isValidISODate('28/02/2026')).toBe(false);
    expect(isValidISODate('')).toBe(false);
    expect(isValidISODate(null)).toBe(false);
  });
});
