/**
 * Product pages: one product of one bill with everything about it. The backend gathers it
 * (ProductService); this file holds its shape, the call and the link.
 */
import { api } from './api';
import type { BillCategory, DocumentSummary } from './documents';
import type { WarrantyStatus } from './warranties';

export type RelatedDocument = { document: DocumentSummary; matchedBy: 'serial number' | 'model number' };

export type ProductView = {
  documentId: string;
  productId: string;
  position: number;
  productName: string | null;
  brand: string | null;
  modelNumber: string | null;
  serialNumber: string | null;
  price: number | null;
  category: BillCategory | null;
  sellerName: string | null;
  invoiceNumber: string | null;
  purchaseDate: string | null;
  warrantyProvider: string | null;
  warrantyMonths: number | null;
  warrantyStartDate: string | null;
  /** Printed, or start + months − 1 day. */
  warrantyEndDate: string | null;
  daysLeft: number | null;
  status: WarrantyStatus;
  registrationUrl: string | null;
  warrantyRegistered: boolean;
  /** Days since the purchase; null without a purchase date. */
  ownedDays: number | null;
  /** Price ÷ whole months owned (at least 1). */
  costPerMonth: number | null;
  /** The bill this product is on. */
  bill: DocumentSummary;
  /** Other documents with the same serial number, or the same model number. */
  related: RelatedDocument[];
  /** e.g. ["Serial number"]: add them on the bill. */
  missing: string[];
  serviceHistory: import('./service-records').ServiceRecord[];
  totalServiceCost: number;
  lastServiceDate: string | null;
  nextServiceDue: string | null;
};

export const getProduct = (productId: string) =>
  api<ProductView>(`/products/${productId}`);

export const setWarrantyRegistration = (productId: string, registered: boolean) =>
  api<ProductView>(`/products/${productId}/registration`, { method: 'PUT', body: { registered } });

export const productLink = (productId: string) => `/products/${productId}`;

/** 273 days → "9 months", 40 → "1 month", 5 → "5 days", 400 → "1 year 1 month". */
export function ownedFor(days: number): string {
  if (days < 30) return days === 1 ? '1 day' : `${days} days`;
  const months = Math.floor(days / 30.44);
  const years = Math.floor(months / 12);
  const rest = months % 12;
  const m = (n: number) => (n === 1 ? '1 month' : `${n} months`);
  if (years === 0) return m(months);
  return `${years === 1 ? '1 year' : `${years} years`}${rest ? ` ${m(rest)}` : ''}`;
}
