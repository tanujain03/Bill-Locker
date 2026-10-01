import type { ISODateTime } from './api';
import type { DocumentSummary, DocumentType } from './document';

export type GmailSyncStatus = 'IDLE' | 'SYNCING' | 'ERROR';

export interface GmailConnection {
  connected: boolean;
  email: string | null;
  connectedAt: ISODateTime | null;
  lastSyncedAt: ISODateTime | null;
  autoSync: boolean;
  syncStatus: GmailSyncStatus;
  lastError: string | null;
}

export type GmailMessageStatus = 'NEW' | 'IMPORTED' | 'IGNORED';

export interface GmailAttachment {
  fileName: string;
  mimeType: string;
  size: number;
}

/** An email the backend's scan classified as a likely bill / invoice / warranty. */
export interface GmailMessage {
  id: string;
  fromName: string;
  fromEmail: string;
  subject: string;
  snippet: string;
  receivedAt: ISODateTime;
  /** Empty when the bill is in the email body itself. */
  attachments: GmailAttachment[];
  detectedType: DocumentType | null;
  /** 0..1 — how likely the email contains a purchase document. */
  confidence: number;
  status: GmailMessageStatus;
  /** Documents created when the email was imported. */
  documentIds: string[];
}

export interface ConnectGmailResponse {
  /** Google OAuth consent URL (or the app callback URL in mock mode). */
  authorizationUrl: string;
}

export interface ImportGmailRequest {
  messageIds: string[];
}

export interface ImportGmailResponse {
  documents: DocumentSummary[];
}

export interface GmailSettingsRequest {
  autoSync: boolean;
}
