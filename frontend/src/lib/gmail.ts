/**
 * Gmail import: the shapes the backend sends (same as GmailViews.java), the labels
 * we show, and one small function per endpoint. All HTTP goes through api.ts.
 */
import { api } from './api';
import type { DocumentStatus } from './documents';

export type ScanRange = 'SIX_MONTHS' | 'ONE_YEAR' | 'TWO_YEARS' | 'FIVE_YEARS';
export type EmailView = 'TO_REVIEW' | 'IGNORED' | 'IMPORTED';
export type EmailKind = 'INVOICE' | 'WARRANTY' | 'SERVICE' | 'RECEIPT' | 'ORDER' | 'UNSURE';
export type GmailFileStatus = 'NEW' | 'IGNORED' | 'IMPORTING' | 'IMPORTED' | 'FAILED';
export type GmailScanStatus = 'IDLE' | 'QUEUED' | 'SCANNING' | 'ERROR';

export type GmailAccount = {
  id: string;
  email: string;
  scanStatus: GmailScanStatus;
  lastScannedAt: string | null;
  lastError: string | null;
  connectedAt: string;
};

export type GmailOverview = {
  configured: boolean;
  accounts: GmailAccount[];
  /** Counted in files, not emails. */
  counts: { toReview: number; ignored: number; imported: number };
};

export type GmailFile = {
  id: string;
  fileName: string;
  contentType: string;
  sizeBytes: number;
  status: GmailFileStatus;
  error: string | null;
  /** The document an imported file became; null if never imported or deleted since. */
  document: { id: string; status: DocumentStatus; readQueued: boolean } | null;
};

export type GmailEmail = {
  id: string;
  accountEmail: string;
  fromName: string | null;
  fromEmail: string | null;
  subject: string | null;
  snippet: string | null;
  receivedAt: string | null;
  kind: EmailKind;
  files: GmailFile[];
};

export const SCAN_RANGE_LABELS: Record<ScanRange, string> = {
  SIX_MONTHS: 'Last 6 months',
  ONE_YEAR: 'Last year',
  TWO_YEARS: 'Last 2 years',
  FIVE_YEARS: 'Last 5 years',
};

export const EMAIL_KIND_LABELS: Record<EmailKind, string> = {
  INVOICE: 'Invoice',
  WARRANTY: 'Warranty / guarantee',
  SERVICE: 'Service receipt',
  RECEIPT: 'Receipt',
  ORDER: 'Order',
  UNSURE: 'Might be a bill',
};

/** The ?error= values the backend's callback redirect can send back. */
export const CALLBACK_ERRORS: Record<string, string> = {
  denied: "You didn't allow access in Google, so nothing was connected.",
  expired: 'That connection attempt expired. Please try again.',
  failed: 'Google could not confirm the connection. Please try again.',
};

const BASE = '/integrations/gmail';

export const getGmail = () => api<GmailOverview>(BASE);
// The response also sets an HttpOnly cookie (same origin through the proxy); JS never touches it.
export const connectGmail = () => api<{ authorizationUrl: string }>(`${BASE}/connect`, { method: 'POST' });
export const scanAccount = (id: string, range: ScanRange) =>
  api<GmailAccount>(`${BASE}/accounts/${id}/scan`, { body: { range } });
export const disconnectAccount = (id: string) => api<null>(`${BASE}/accounts/${id}`, { method: 'DELETE' });
export const listEmails = (view: EmailView) => api<GmailEmail[]>(`${BASE}/emails?view=${view}`);
export const importFiles = (fileIds: string[]) => api<GmailFile[]>(`${BASE}/files/import`, { body: { fileIds } });
export const ignoreFiles = (fileIds: string[]) => api<GmailFile[]>(`${BASE}/files/ignore`, { body: { fileIds } });
export const restoreFiles = (fileIds: string[]) => api<GmailFile[]>(`${BASE}/files/restore`, { body: { fileIds } });

/** 2048 bytes → "2 KB", 3.5 MB → "3.5 MB". */
export function formatSize(bytes: number): string {
  const mb = bytes / (1024 * 1024);
  return mb < 1 ? `${Math.max(1, Math.round(bytes / 1024))} KB` : `${mb.toFixed(1)} MB`;
}

const relative = new Intl.RelativeTimeFormat('en', { numeric: 'auto' });

/** "5 minutes ago", "yesterday". */
export function timeAgo(iso: string): string {
  const seconds = (new Date(iso).getTime() - Date.now()) / 1000;
  const steps: [Intl.RelativeTimeFormatUnit, number][] = [
    ['day', 86400],
    ['hour', 3600],
    ['minute', 60],
  ];
  for (const [unit, size] of steps) {
    if (Math.abs(seconds) >= size) return relative.format(Math.round(seconds / size), unit);
  }
  return 'just now';
}
