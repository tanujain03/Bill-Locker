/**
 * Documents: the shapes the backend sends (same as its Java records), the labels
 * we show, and one small function per endpoint. All HTTP goes through api.ts.
 */
import { api, fetchBlob } from './api';

export type DocumentType = 'INVOICE' | 'WARRANTY_CARD' | 'RECEIPT' | 'OTHER';
/** UPLOADED = not read yet, EXTRACTED = AI filled it (needs review), SAVED = the user saved it. */
export type DocumentStatus = 'UPLOADED' | 'EXTRACTED' | 'SAVED';

export type DocumentItem = {
  productName: string | null;
  modelNumber: string | null;
  serialNumber: string | null;
  unitPrice: number | null;
  warrantyPeriodMonths: number | null;
  warrantyStartDate: string | null;
  warrantyEndDate: string | null;
  warrantyProvider: string | null;
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
  readQueued: boolean;
  readError: string | null;
  sourceGmail: string | null;
  createdAt: string;
};

export const DOCUMENT_TYPE_LABELS: Record<DocumentType, string> = {
  INVOICE: 'Invoice',
  WARRANTY_CARD: 'Warranty card',
  RECEIPT: 'Receipt',
  OTHER: 'Other',
};

/** Field → label, in the order of invoice_warranty_fields.csv. Used by the form and by "Copy". */
export const FIELD_LABELS = {
  documentType: 'Document type',
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
} as const satisfies Record<keyof DocumentItem, string>;

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

/** 1499 → "1,499.00" (Indian digit grouping; bills don't tell us the currency yet). */
export const formatAmount = (amount: number) =>
  amount.toLocaleString('en-IN', { minimumFractionDigits: 2, maximumFractionDigits: 2 });
