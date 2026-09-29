import type { Category, Product, SearchFilters } from '@/types';
import { formatCurrency, formatDate } from './format';
import { WARRANTY_STATUS_LABELS } from './warranty';

const SORT_LABELS: Record<NonNullable<SearchFilters['sortBy']>, string> = {
  PURCHASE_DATE: 'purchase date',
  PRICE: 'price',
  WARRANTY_EXPIRY: 'warranty expiry',
};

/** Turns the AI's structured interpretation into readable chips. */
export function describeSearchFilters(filters: SearchFilters, categories: Category[] = []): string[] {
  const chips: string[] = [];
  if (filters.text) chips.push(`Matches “${filters.text}”`);
  if (filters.categorySlug) {
    const name = categories.find((category) => category.slug === filters.categorySlug)?.name ?? filters.categorySlug;
    chips.push(`Category: ${name}`);
  }
  if (filters.brand) chips.push(`Brand: ${filters.brand}`);
  if (filters.seller) chips.push(`Seller: ${filters.seller}`);
  if (filters.warrantyStatus) chips.push(`Warranty: ${WARRANTY_STATUS_LABELS[filters.warrantyStatus].toLowerCase()}`);
  if (typeof filters.daysUntilExpiry === 'number') chips.push(`Expires within ${filters.daysUntilExpiry} days`);
  if (filters.purchasedAfter) chips.push(`Bought after ${formatDate(filters.purchasedAfter)}`);
  if (filters.purchasedBefore) chips.push(`Bought before ${formatDate(filters.purchasedBefore)}`);
  if (typeof filters.minPrice === 'number') chips.push(`Price ≥ ${formatCurrency(filters.minPrice)}`);
  if (typeof filters.maxPrice === 'number') chips.push(`Price ≤ ${formatCurrency(filters.maxPrice)}`);
  if (filters.sortBy) {
    const direction = filters.sortDirection === 'ASC' ? 'lowest/earliest first' : 'highest/latest first';
    chips.push(`Sorted by ${SORT_LABELS[filters.sortBy]} (${direction})`);
  }
  if (typeof filters.limit === 'number') chips.push(`Top ${filters.limit}`);
  return chips;
}

export type ProductSort = 'recent' | 'purchase-date' | 'price' | 'expiry';

export const PRODUCT_SORT_LABELS: Record<ProductSort, string> = {
  recent: 'Recently added',
  'purchase-date': 'Purchase date',
  price: 'Price (high to low)',
  expiry: 'Warranty expiring first',
};

export function sortProducts(products: Product[], sort: ProductSort): Product[] {
  const copy = [...products];
  switch (sort) {
    case 'purchase-date':
      return copy.sort((a, b) => (b.purchaseDate ?? '').localeCompare(a.purchaseDate ?? ''));
    case 'price':
      return copy.sort((a, b) => (b.purchasePrice ?? -1) - (a.purchasePrice ?? -1));
    case 'expiry':
      return copy.sort((a, b) => {
        const left = a.warranty?.expiryDate ?? '9999-12-31';
        const right = b.warranty?.expiryDate ?? '9999-12-31';
        return left.localeCompare(right);
      });
    default:
      return copy.sort((a, b) => b.createdAt.localeCompare(a.createdAt));
  }
}
