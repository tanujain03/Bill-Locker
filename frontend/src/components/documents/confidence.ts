import type { ExtractionField, ExtractionResult } from '@/types';

export type ConfidenceLevel = 'high' | 'medium' | 'low' | 'missing' | 'unscored';

/** Where the value currently shown for a field comes from. */
export type FieldSource = ConfidenceLevel | 'edited' | 'suggested' | 'saved';

export const HIGH_CONFIDENCE = 0.85;
export const MEDIUM_CONFIDENCE = 0.6;

export function confidenceLevel(value: unknown, score: number | undefined): ConfidenceLevel {
  if (value === null || value === undefined || (typeof value === 'string' && value.trim() === '')) return 'missing';
  if (typeof score !== 'number') return 'unscored';
  if (score >= HIGH_CONFIDENCE) return 'high';
  if (score >= MEDIUM_CONFIDENCE) return 'medium';
  return 'low';
}

/** Fields the user reviews, mapped to the extraction key they came from. */
export const REVIEW_FIELDS = [
  { key: 'name', extractionKey: 'productName', label: 'Product' },
  { key: 'categoryId', extractionKey: null, label: 'Category' },
  { key: 'brand', extractionKey: 'brand', label: 'Brand' },
  { key: 'model', extractionKey: 'model', label: 'Model' },
  { key: 'serialNumber', extractionKey: 'serialNumber', label: 'Serial number' },
  { key: 'purchaseDate', extractionKey: 'purchaseDate', label: 'Purchase date' },
  { key: 'purchasePrice', extractionKey: 'purchasePrice', label: 'Price' },
  { key: 'seller', extractionKey: 'seller', label: 'Seller' },
  { key: 'invoiceNumber', extractionKey: 'invoiceNumber', label: 'Invoice number' },
  { key: 'warrantyMonths', extractionKey: 'warrantyMonths', label: 'Warranty' },
] as const satisfies readonly { key: string; extractionKey: ExtractionField | null; label: string }[];

export type ReviewField = (typeof REVIEW_FIELDS)[number];

export interface ExtractionStats {
  total: number;
  found: number;
  highConfidence: number;
  needsAttention: number;
}

/** Summary shown above the review form: how much the AI found and what needs a look. */
export function summarizeExtraction(extraction: ExtractionResult | null): ExtractionStats {
  const scored = REVIEW_FIELDS.filter((field) => field.extractionKey !== null);
  let found = 0;
  let highConfidence = 0;
  let needsAttention = 0;
  for (const field of scored) {
    const key = field.extractionKey as ExtractionField;
    const level = confidenceLevel(extraction?.[key], extraction?.confidence?.[key]);
    if (level !== 'missing') found += 1;
    if (level === 'high') highConfidence += 1;
    if (level === 'missing' || level === 'low' || level === 'medium') needsAttention += 1;
  }
  return { total: scored.length, found, highConfidence, needsAttention };
}
