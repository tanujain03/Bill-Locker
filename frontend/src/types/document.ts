import type { ISODate, ISODateTime } from './api';
import type { Product, ProductInput } from './product';

export type DocumentType = 'INVOICE' | 'WARRANTY_CARD' | 'SERVICE_RECEIPT' | 'REPAIR_RECEIPT' | 'OTHER';

export const DOCUMENT_TYPES: DocumentType[] = ['INVOICE', 'WARRANTY_CARD', 'SERVICE_RECEIPT', 'REPAIR_RECEIPT', 'OTHER'];

export type ProcessingStatus = 'UPLOADED' | 'PROCESSING' | 'PROCESSED' | 'REVIEW_REQUIRED' | 'CONFIRMED' | 'FAILED';

/** Optional finer-grained progress while `processingStatus === 'PROCESSING'`. */
export type ProcessingStage = 'OCR' | 'EXTRACTION' | 'INDEXING';

export type DocumentSource = 'UPLOAD' | 'GMAIL';

export interface DocumentSummary {
  id: string;
  productId: string | null;
  productName: string | null;
  documentType: DocumentType;
  fileName: string;
  mimeType: string;
  fileSize: number;
  processingStatus: ProcessingStatus;
  processingStage: ProcessingStage | null;
  source: DocumentSource;
  errorMessage: string | null;
  createdAt: ISODateTime;
  updatedAt: ISODateTime;
}

export const EXTRACTION_FIELDS = [
  'documentType',
  'productName',
  'brand',
  'model',
  'serialNumber',
  'purchaseDate',
  'purchasePrice',
  'currency',
  'seller',
  'invoiceNumber',
  'warrantyMonths',
] as const;

export type ExtractionField = (typeof EXTRACTION_FIELDS)[number];

/**
 * Structured data the AI extracted from a document. Missing values are `null`
 * (the backend normalises `NOT_FOUND` to `null`) — the AI must never guess.
 */
export interface ExtractionResult {
  documentType: DocumentType | null;
  productName: string | null;
  brand: string | null;
  model: string | null;
  serialNumber: string | null;
  purchaseDate: ISODate | null;
  purchasePrice: number | null;
  currency: string | null;
  seller: string | null;
  invoiceNumber: string | null;
  warrantyMonths: number | null;
  /** Category slug suggested by the AI (see `Category.slug`). */
  suggestedCategorySlug: string | null;
  /** 0..1 per field; absent when the provider gives no score. */
  confidence: Partial<Record<ExtractionField, number>>;
  /** Barcodes and QR codes found on the document (read exactly). Absent/null before step 7. */
  codes?: ScannedCode[] | null;
}

export type ScannedCodeKind = 'GST_E_INVOICE' | 'LINK' | 'BARCODE' | 'TEXT';

/** A barcode or QR code on a document. `invoice` is set for a GST e-invoice QR code. */
export interface ScannedCode {
  /** Symbology, e.g. `QR_CODE` or `CODE_128`. */
  format: string;
  kind: ScannedCodeKind;
  value: string;
  invoice: {
    invoiceNumber: string | null;
    invoiceDate: ISODate | null;
    total: number | null;
    sellerGstin: string | null;
  } | null;
}

export interface DocumentDetail extends DocumentSummary {
  extraction: ExtractionResult | null;
  extractedText: string | null;
}

export interface DocumentFilters {
  productId?: string;
  status?: ProcessingStatus;
  documentType?: DocumentType;
}

export interface UploadDocumentParams {
  file: File;
  documentType?: DocumentType;
  productId?: string;
  onProgress?: (percent: number) => void;
  signal?: AbortSignal;
}

/** Sent after the user reviewed (and possibly edited) the AI extraction. */
export interface ConfirmDocumentRequest {
  documentType: DocumentType;
  /** Existing product to update/link; `null` creates a new product. */
  productId: string | null;
  product: ProductInput;
}

export interface ConfirmDocumentResponse {
  document: DocumentSummary;
  product: Product;
}
