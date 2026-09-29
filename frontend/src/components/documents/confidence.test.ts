import { describe, expect, it } from 'vitest';
import type { ExtractionResult } from '@/types';
import { confidenceLevel, summarizeExtraction } from './confidence';

describe('confidenceLevel', () => {
  it('marks missing values as not found regardless of score', () => {
    expect(confidenceLevel(null, 0.99)).toBe('missing');
    expect(confidenceLevel('', 0.99)).toBe('missing');
    expect(confidenceLevel('   ', undefined)).toBe('missing');
  });

  it('buckets scores into high / medium / low', () => {
    expect(confidenceLevel('Dell', 0.96)).toBe('high');
    expect(confidenceLevel('Dell', 0.85)).toBe('high');
    expect(confidenceLevel('Dell', 0.7)).toBe('medium');
    expect(confidenceLevel('Dell', 0.4)).toBe('low');
  });

  it('does not invent a level when the provider gave no score', () => {
    expect(confidenceLevel(12, undefined)).toBe('unscored');
  });
});

describe('summarizeExtraction', () => {
  const extraction: ExtractionResult = {
    documentType: 'INVOICE',
    productName: 'Philips Air Fryer HD9252/90',
    brand: 'Philips',
    model: 'HD9252/90',
    serialNumber: null,
    purchaseDate: '2026-09-27',
    purchasePrice: 8999,
    currency: 'INR',
    seller: 'Metro Electronics',
    invoiceNumber: 'ME/1',
    warrantyMonths: 24,
    suggestedCategorySlug: 'kitchen',
    confidence: {
      productName: 0.96,
      brand: 0.98,
      model: 0.8,
      purchaseDate: 0.97,
      purchasePrice: 0.99,
      seller: 0.95,
      invoiceNumber: 0.94,
      warrantyMonths: 0.74,
    },
  };

  it('counts found fields and fields needing attention', () => {
    const stats = summarizeExtraction(extraction);
    expect(stats.total).toBe(9);
    expect(stats.found).toBe(8);
    expect(stats.highConfidence).toBe(6);
    // serial missing + model medium + warranty medium
    expect(stats.needsAttention).toBe(3);
  });

  it('handles a failed extraction', () => {
    expect(summarizeExtraction(null)).toEqual({ total: 9, found: 0, highConfidence: 0, needsAttention: 9 });
  });
});
