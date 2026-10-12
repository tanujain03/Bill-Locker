/**
 * Documents: the shapes the backend sends (same as its Java records), the labels
 * we show, and one small function per endpoint. All HTTP goes through api.ts.
 */
import { api, fetchBlob } from './api';

export type DocumentType = 'INVOICE' | 'WARRANTY_CARD' | 'RECEIPT' | 'OTHER';
/** UPLOADED = not read yet, EXTRACTED = AI filled it (needs review), SAVED = the user saved it. */
export type DocumentStatus = 'UPLOADED' | 'EXTRACTED' | 'SAVED';

/** What a bill or receipt was for (only documents of type RECEIPT have one). */
export type BillCategory =
  | 'TRAVEL'
  | 'FOOD'
  | 'GROCERIES'
  | 'FUEL'
  | 'UTILITIES'
  | 'PHONE_INTERNET'
  | 'SHOPPING'
  | 'HEALTH'
  | 'OTHER';

export const BILL_CATEGORY_LABELS: Record<BillCategory, string> = {
  TRAVEL: 'Travel',
  FOOD: 'Food',
  GROCERIES: 'Groceries',
  FUEL: 'Fuel',
  UTILITIES: 'Utilities',
  PHONE_INTERNET: 'Phone & internet',
  SHOPPING: 'Shopping',
  HEALTH: 'Health',
  OTHER: 'Other',
};

/** Where a product's warranty registration link came from (task 5). */
export type RegistrationSource = 'DOCUMENT' | 'QR_CODE' | 'WEB_SEARCH' | 'SEARCH' | 'USER';

export type DocumentItem = {
  productName: string | null;
  modelNumber: string | null;
  serialNumber: string | null;
  unitPrice: number | null;
  warrantyPeriodMonths: number | null;
  warrantyStartDate: string | null;
  warrantyEndDate: string | null;
  warrantyProvider: string | null;
  /** The manufacturer's brand, e.g. "Noise": used to find its registration page. */
  brand: string | null;
  registrationUrl: string | null;
  registrationSource: RegistrationSource | null;
};

/** The details of a bill: what the AI reads and what the user saves. */
export type DocumentDetails = {
  documentType: DocumentType | null;
  documentNumber: string | null;
  sellerName: string | null;
  sellerAddress: string | null;
  sellerContact: string | null;
  buyerName: string | null;
  buyerAddress: string | null;
  buyerEmail: string | null;
  purchaseDate: string | null;
  taxAmount: number | null;
  totalAmount: number | null;
  items: DocumentItem[];
  /** Bills and receipts only. */
  category: BillCategory | null;
};

export type DocumentDetail = DocumentDetails & {
  id: string;
  fileName: string;
  contentType: string;
  sizeBytes: number;
  status: DocumentStatus;
  documentUrl: string;
  /** Waiting for the background AI read. */
  readQueued: boolean;
  /** Why the last background read failed (status stays UPLOADED). */
  readError: string | null;
  /** The Gmail address an imported document came from; null for uploads. */
  sourceGmail: string | null;
  createdAt: string;
  updatedAt: string;
};

export type DocumentSummary = {
  id: string;
  fileName: string;
  contentType: string;
  sizeBytes: number;
  status: DocumentStatus;
  documentType: DocumentType | null;
  documentNumber: string | null;
  sellerName: string | null;
  purchaseDate: string | null;
  totalAmount: number | null;
  itemCount: number;
  firstProductName: string | null;
  category: BillCategory | null;
  readQueued: boolean;
  readError: string | null;
  sourceGmail: string | null;
  createdAt: string;
};

export const DOCUMENT_TYPE_LABELS: Record<DocumentType, string> = {
  INVOICE: 'Invoice',
  WARRANTY_CARD: 'Warranty card',
  // Rides, food, fuel, utility and phone bills: they get their own page (/bills).
  RECEIPT: 'Bill / receipt',
  OTHER: 'Other',
};

/** Field → label, in the order of invoice_warranty_fields.csv. Used by the form and by "Copy". */
export const FIELD_LABELS = {
  documentType: 'Document type',
  category: 'Category',
  documentNumber: 'Document number',
  sellerName: 'Seller name',
  sellerAddress: 'Seller address',
  sellerContact: 'Seller contact',
  buyerName: 'Buyer name',
  buyerAddress: 'Buyer address',
  buyerEmail: 'Buyer email',
  purchaseDate: 'Purchase date',
  taxAmount: 'Tax amount',
  totalAmount: 'Total amount',
} as const satisfies Partial<Record<keyof DocumentDetails, string>>;

export const ITEM_LABELS = {
  productName: 'Product name',
  modelNumber: 'Model number',
  serialNumber: 'Serial number',
  unitPrice: 'Unit price',
  warrantyPeriodMonths: 'Warranty (months)',
  warrantyStartDate: 'Warranty start',
  warrantyEndDate: 'Warranty end',
  warrantyProvider: 'Warranty provider',
  brand: 'Brand',
  registrationUrl: 'Warranty registration link',
} as const satisfies Record<Exclude<keyof DocumentItem, 'registrationSource'>, string>;

/** The page to open: the brand's official one (WEB_SEARCH) or, if none was found, a Google search (SEARCH). */
export type RegistrationPage = { url: string; source: RegistrationSource };

/**
 * When the bill has no registration link or QR code: after the user confirmed the brand,
 * the backend finds that brand's official warranty registration page (and keeps it on the product).
 */
export const findRegistrationPage = (id: string, position: number, brand: string) =>
  api<RegistrationPage>(`/documents/${id}/registration-page`, { body: { position, brand } });

export const uploadDocument = (file: File) => {
  const form = new FormData();
  form.append('file', file);
  return api<DocumentDetail>('/documents/upload', { body: form });
};

export const listDocuments = (filters: { q?: string; type?: string; status?: string }) => {
  const params = new URLSearchParams();
  for (const [key, value] of Object.entries(filters)) if (value) params.set(key, value);
  const query = params.toString();
  return api<DocumentSummary[]>(`/documents${query ? `?${query}` : ''}`);
};

export const getDocument = (id: string) => api<DocumentDetail>(`/documents/${id}`);
export const extractDocument = (id: string) => api<DocumentDetail>(`/documents/${id}/extract`, { method: 'POST' });
export const saveDocument = (id: string, details: DocumentDetails) =>
  api<DocumentDetail>(`/documents/${id}`, { method: 'PUT', body: details });
export const deleteDocument = (id: string) => api<null>(`/documents/${id}`, { method: 'DELETE' });
export const downloadDocument = (id: string) => fetchBlob(`/documents/${id}/download`);

/** "2026-09" → "Sep 2026". */
export const monthLabel = (month: string) =>
  new Date(`${month}-01T00:00:00Z`).toLocaleDateString('en-IN', { month: 'short', year: 'numeric', timeZone: 'UTC' });

/**
 * 1499 → "₹1,499.00" (Indian digit grouping). Bills don't store a currency yet, so every
 * amount is shown in rupees; a Preferences → Currency setting can change this later.
 */
const RUPEES = new Intl.NumberFormat('en-IN', { style: 'currency', currency: 'INR' });
/** `whole`: "₹50,893" — for big headline numbers, where paise only add width. */
const WHOLE_RUPEES = new Intl.NumberFormat('en-IN', { style: 'currency', currency: 'INR', maximumFractionDigits: 0 });
export const formatAmount = (amount: number, { whole = false } = {}) =>
  (whole ? WHOLE_RUPEES : RUPEES).format(amount);
