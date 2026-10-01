import { describe, expect, it } from 'vitest';
import { describeDueDate, formatCurrency, formatDate, formatFileSize, initials, pluralize } from './format';

describe('formatCurrency', () => {
  it('formats rupees with Indian digit grouping', () => {
    expect(formatCurrency(62990)).toBe('₹62,990');
    expect(formatCurrency(395348)).toBe('₹3,95,348');
  });

  it('keeps paise when present', () => {
    expect(formatCurrency(1499.5)).toBe('₹1,499.50');
  });

  it('shows a dash for unknown amounts', () => {
    expect(formatCurrency(null)).toBe('—');
    expect(formatCurrency(undefined)).toBe('—');
  });

  it('supports other currencies', () => {
    expect(formatCurrency(1200, 'USD')).toContain('1,200');
  });
});

describe('formatDate', () => {
  it('formats ISO dates without shifting the day', () => {
    expect(formatDate('2026-06-15')).toBe('15 Jun 2026');
  });

  it('shows a dash when missing', () => {
    expect(formatDate(null)).toBe('—');
  });
});

describe('describeDueDate', () => {
  const from = new Date(2026, 5, 10);
  it('describes upcoming and overdue dates', () => {
    expect(describeDueDate('2026-06-10', from)).toBe('Due today');
    expect(describeDueDate('2026-06-11', from)).toBe('Due tomorrow');
    expect(describeDueDate('2026-06-17', from)).toBe('Due in 7 days');
    expect(describeDueDate('2026-06-08', from)).toBe('2 days overdue');
  });
});

describe('small formatters', () => {
  it('formats file sizes', () => {
    expect(formatFileSize(512)).toBe('512 B');
    expect(formatFileSize(84_213)).toBe('82 KB');
    expect(formatFileSize(3_500_000)).toBe('3.3 MB');
  });

  it('builds initials', () => {
    expect(initials('Asha Verma')).toBe('AV');
    expect(initials('asha')).toBe('AS');
    expect(initials('')).toBe('?');
  });

  it('pluralizes', () => {
    expect(pluralize(1, 'document')).toBe('1 document');
    expect(pluralize(3, 'document')).toBe('3 documents');
  });
});
