import type { ISODate, ISODateTime } from './api';
import type { WarrantyStatus, WarrantySummary } from './warranty';

export interface Category {
  id: string;
  name: string;
  /** Stable key used for icons, e.g. `computers`, `home-appliances`. */
  slug: string;
}

export interface Product {
  id: string;
  categoryId: string | null;
  categoryName: string | null;
  categorySlug: string | null;
  name: string;
  brand: string | null;
  model: string | null;
  serialNumber: string | null;
  purchaseDate: ISODate | null;
  purchasePrice: number | null;
  currency: string;
  seller: string | null;
  invoiceNumber: string | null;
  warranty: WarrantySummary | null;
  nextServiceDate: ISODate | null;
  documentCount: number;
  createdAt: ISODateTime;
  updatedAt: ISODateTime;
}

/** Create/update payload. `warrantyMonths` creates or updates the product's warranty. */
export interface ProductInput {
  name: string;
  categoryId: string | null;
  brand: string | null;
  model: string | null;
  serialNumber: string | null;
  purchaseDate: ISODate | null;
  purchasePrice: number | null;
  currency: string;
  seller: string | null;
  invoiceNumber: string | null;
  warrantyMonths: number | null;
}

export interface ProductFilters {
  search?: string;
  categoryId?: string;
  warrantyStatus?: WarrantyStatus;
}
