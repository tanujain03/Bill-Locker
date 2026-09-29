import type { Product } from '@/types';

/** "Dell · Inspiron 15 3530", falling back to the category. */
export function productSubtitle(product: Pick<Product, 'brand' | 'model' | 'categoryName'>): string {
  return [product.brand, product.model].filter(Boolean).join(' · ') || product.categoryName || 'No brand details';
}
