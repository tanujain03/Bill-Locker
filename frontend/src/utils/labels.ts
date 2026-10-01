import { isFeatureEnabled } from '@/lib/features';
import type {
  DocumentSource,
  DocumentType,
  ExtractionField,
  NotificationType,
  ProcessingStatus,
  ServiceType,
} from '@/types';

export const DOCUMENT_TYPE_LABELS: Record<DocumentType, string> = {
  INVOICE: 'Invoice',
  WARRANTY_CARD: 'Warranty card',
  SERVICE_RECEIPT: 'Service receipt',
  REPAIR_RECEIPT: 'Repair receipt',
  OTHER: 'Other',
};

export const PROCESSING_STATUS_LABELS: Record<ProcessingStatus, string> = {
  UPLOADED: 'Uploaded',
  PROCESSING: 'Processing',
  PROCESSED: 'Processed',
  REVIEW_REQUIRED: 'Needs review',
  CONFIRMED: 'Saved',
  FAILED: 'Failed',
};

export const DOCUMENT_SOURCE_LABELS: Record<DocumentSource, string> = {
  UPLOAD: 'Uploaded',
  GMAIL: 'Gmail',
};

export const SERVICE_TYPE_LABELS: Record<ServiceType, string> = {
  ROUTINE_MAINTENANCE: 'Routine maintenance',
  REPAIR: 'Repair',
  INSTALLATION: 'Installation',
  INSPECTION: 'Inspection',
  OTHER: 'Other',
};

export const NOTIFICATION_TYPE_LABELS: Record<NotificationType, string> = {
  WARRANTY_EXPIRING: 'Warranty expiring',
  WARRANTY_EXPIRED: 'Warranty expired',
  SERVICE_DUE: 'Service due',
  DOCUMENT_PROCESSED: 'Document ready',
  GMAIL_BILLS_FOUND: 'Gmail',
};

export const EXTRACTION_FIELD_LABELS: Record<ExtractionField, string> = {
  documentType: 'Document type',
  productName: 'Product',
  brand: 'Brand',
  model: 'Model',
  serialNumber: 'Serial number',
  purchaseDate: 'Purchase date',
  purchasePrice: 'Price',
  currency: 'Currency',
  seller: 'Seller',
  invoiceNumber: 'Invoice number',
  warrantyMonths: 'Warranty',
};

export const CURRENCIES = ['INR', 'USD', 'EUR', 'GBP', 'AED', 'SGD'] as const;

/** Processing states in which the backend is still working on the document. */
export function isProcessing(status: ProcessingStatus | undefined): boolean {
  // Until the backend reads documents (OCR/AI), an uploaded file is simply stored.
  if (status === 'UPLOADED') return isFeatureEnabled('documentProcessing');
  return status === 'PROCESSING';
}

/** Uploaded and stored, and nothing more will happen to it (no OCR/AI in the backend yet). */
export function isStoredOnly(status: ProcessingStatus | undefined): boolean {
  return status === 'UPLOADED' && !isFeatureEnabled('documentProcessing');
}

/** States in which the user has to review the AI extraction. */
export function needsReview(status: ProcessingStatus | undefined): boolean {
  return status === 'REVIEW_REQUIRED' || status === 'PROCESSED';
}
