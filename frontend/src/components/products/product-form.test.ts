import { describe, expect, it } from 'vitest';
import type { Product } from '@/types';
import {
  extractionToFormValues,
  mergeProductWithExtraction,
  productFormSchema,
  productToFormValues,
  toProductInput,
  type ProductFormValues,
} from './product-form';

const values = (overrides: Partial<ProductFormValues> = {}): ProductFormValues => ({
  ...productToFormValues(null),
  name: 'Dell Inspiron 15',
  ...overrides,
});

describe('productFormSchema', () => {
  it('requires a product name', () => {
    const result = productFormSchema.safeParse(values({ name: '   ' }));
    expect(result.success).toBe(false);
  });

  it('rejects future purchase dates and malformed amounts', () => {
    expect(productFormSchema.safeParse(values({ purchaseDate: '2999-01-01' })).success).toBe(false);
    expect(productFormSchema.safeParse(values({ purchasePrice: '12,000' })).success).toBe(false);
    expect(productFormSchema.safeParse(values({ purchasePrice: '12000.505' })).success).toBe(false);
    expect(productFormSchema.safeParse(values({ warrantyMonths: '1.5' })).success).toBe(false);
    expect(productFormSchema.safeParse(values({ warrantyMonths: '300' })).success).toBe(false);
  });

  it('accepts a complete product', () => {
    const result = productFormSchema.safeParse(
      values({ purchaseDate: '2026-01-15', purchasePrice: '62990', warrantyMonths: '12', brand: 'Dell' }),
    );
    expect(result.success).toBe(true);
  });
});

describe('toProductInput', () => {
  it('converts empty strings to null and numbers to numbers', () => {
    const input = toProductInput(values({ purchasePrice: '1499.50', warrantyMonths: '', brand: '  ' }));
    expect(input).toMatchObject({
      name: 'Dell Inspiron 15',
      brand: null,
      purchasePrice: 1499.5,
      warrantyMonths: null,
      categoryId: null,
      currency: 'INR',
    });
  });
});

describe('extractionToFormValues', () => {
  it('leaves fields the AI could not find empty (never guessed)', () => {
    const form = extractionToFormValues(
      {
        documentType: 'INVOICE',
        productName: 'Philips Air Fryer',
        brand: 'Philips',
        model: null,
        serialNumber: null,
        purchaseDate: 'not-a-date',
        purchasePrice: 8999,
        currency: 'INR',
        seller: null,
        invoiceNumber: null,
        warrantyMonths: null,
        suggestedCategorySlug: null,
        confidence: {},
      },
      'cat_kitchen',
    );
    expect(form.name).toBe('Philips Air Fryer');
    expect(form.serialNumber).toBe('');
    expect(form.purchaseDate).toBe('');
    expect(form.warrantyMonths).toBe('');
    expect(form.purchasePrice).toBe('8999');
    expect(form.categoryId).toBe('cat_kitchen');
  });
});

describe('mergeProductWithExtraction', () => {
  it('keeps saved values and only fills empty ones from the document', () => {
    const product = {
      id: 'p1',
      name: 'Sony Bravia 55" TV',
      categoryId: 'cat_tv',
      brand: 'Sony',
      model: 'KD-55X74L',
      serialNumber: null,
      purchaseDate: '2026-03-12',
      purchasePrice: 64990,
      currency: 'INR',
      seller: 'Amazon.in',
      invoiceNumber: 'IN-1',
      warranty: { id: 'w1', warrantyMonths: 24, startDate: '2026-03-12', expiryDate: '2028-03-11', status: 'ACTIVE', daysRemaining: 500 },
    } as Product;
    const extracted = values({ name: 'Sony Bravia TV', serialNumber: 'SN-123', warrantyMonths: '36' });
    const merged = mergeProductWithExtraction(product, extracted);
    expect(merged.name).toBe('Sony Bravia 55" TV');
    expect(merged.warrantyMonths).toBe('24');
    expect(merged.serialNumber).toBe('SN-123');
  });
});
