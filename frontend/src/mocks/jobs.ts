import { daysUntil } from '@/utils/date';
import { formatDate } from '@/utils/format';
import { upcomingServices } from '@/utils/service';
import type { DbDocument, DbNotification, MockDatabase } from './db';
import { createGmailMessages } from './gmail-fixtures';
import { templateByKey, templateToExtraction, templateToOcrText } from './templates';
import { newId, nowIso } from './utils';
import { computeWarranty } from './warranty-engine';

// ---------------------------------------------------------------------------
// Reminder job — what the backend's scheduled task does once a day
// ---------------------------------------------------------------------------

const SERVICE_REMINDER_DAYS = 7;
const RECENTLY_EXPIRED_DAYS = 60;

function addNotification(
  data: MockDatabase,
  notification: Omit<DbNotification, 'id' | 'read' | 'createdAt' | 'scheduledAt'> & { createdAt?: string },
): boolean {
  if (notification.dedupeKey && data.notifications.some((item) => item.dedupeKey === notification.dedupeKey)) return false;
  const createdAt = notification.createdAt ?? nowIso();
  data.notifications.push({ ...notification, id: newId('ntf'), read: false, createdAt, scheduledAt: createdAt });
  return true;
}

export function runReminderJob(data: MockDatabase, userId: string, today = new Date(), createdAt?: string): void {
  const products = data.products.filter((product) => product.userId === userId);

  for (const product of products) {
    const warrantyRow = data.warranties.find((warranty) => warranty.productId === product.id);
    const warranty = computeWarranty(warrantyRow, today);
    if (!warranty?.expiryDate || warranty.daysRemaining === null) continue;

    if (warranty.status === 'EXPIRING_SOON') {
      addNotification(data, {
        userId,
        productId: product.id,
        documentId: null,
        type: 'WARRANTY_EXPIRING',
        title: 'Warranty expiring soon',
        message: `Your ${product.name} warranty expires in ${warranty.daysRemaining} days (${formatDate(warranty.expiryDate)}).`,
        dedupeKey: `WARRANTY_EXPIRING:${warranty.id}:${warranty.expiryDate}`,
        createdAt,
      });
    } else if (warranty.status === 'EXPIRED' && warranty.daysRemaining >= -RECENTLY_EXPIRED_DAYS) {
      addNotification(data, {
        userId,
        productId: product.id,
        documentId: null,
        type: 'WARRANTY_EXPIRED',
        title: 'Warranty expired',
        message: `The warranty for your ${product.name} ended on ${formatDate(warranty.expiryDate)}.`,
        dedupeKey: `WARRANTY_EXPIRED:${warranty.id}:${warranty.expiryDate}`,
        createdAt,
      });
    }
  }

  const productIds = new Set(products.map((product) => product.id));
  const records = data.serviceRecords.filter((record) => productIds.has(record.productId));
  // upcomingServices only needs the fields below; product names are joined here.
  for (const record of upcomingServices(records.map((item) => ({ ...item, productName: '', currency: 'INR' })))) {
    const days = daysUntil(record.nextServiceDate, today);
    if (days > SERVICE_REMINDER_DAYS || days < -30) continue;
    const product = products.find((item) => item.id === record.productId);
    addNotification(data, {
      userId,
      productId: record.productId,
      documentId: null,
      type: 'SERVICE_DUE',
      title: days < 0 ? 'Service overdue' : 'Service due soon',
      message:
        days < 0
          ? `Your ${product?.name ?? 'product'} service was due on ${formatDate(record.nextServiceDate)}.`
          : `Your ${product?.name ?? 'product'} service is due in ${days} ${days === 1 ? 'day' : 'days'} (${formatDate(record.nextServiceDate)}).`,
      dedupeKey: `SERVICE_DUE:${record.id}:${record.nextServiceDate}`,
      createdAt,
    });
  }
}

// ---------------------------------------------------------------------------
// Document pipeline — simulates OCR → AI extraction → indexing over ~6 s
// ---------------------------------------------------------------------------

const STAGE_OCR_AT = 700;
const STAGE_EXTRACTION_AT = 2200;
const STAGE_INDEXING_AT = 4600;
const DONE_AT = 5600;
const FAIL_AT = 3000;

function finishDocument(data: MockDatabase, document: DbDocument, userId: string, now: number): void {
  const template = templateByKey(document.templateKey);
  const extraction = templateToExtraction(template, new Date(now));
  if (document.documentTypeHint) {
    extraction.documentType = document.documentTypeHint;
    extraction.confidence = { ...extraction.confidence, documentType: 1 };
  }
  const user = data.users.find((item) => item.id === userId);
  document.processingStatus = 'REVIEW_REQUIRED';
  document.processingStage = null;
  document.extraction = extraction;
  document.documentType = extraction.documentType ?? document.documentType;
  document.extractedText = templateToOcrText(template, extraction, user?.name ?? 'Customer');
  document.errorMessage = null;
  addNotification(data, {
    userId,
    productId: document.productId,
    documentId: document.id,
    type: 'DOCUMENT_PROCESSED',
    title: 'Document ready for review',
    message: `We’ve finished reading ${document.fileName}. Review the details to save it to your locker.`,
    dedupeKey: `DOCUMENT_PROCESSED:${document.id}:${document.attempts}`,
  });
}

/** Moves in-flight documents forward based on elapsed time. Returns true if anything changed. */
export function advanceProcessing(data: MockDatabase, userId: string, now = Date.now()): boolean {
  let changed = false;
  for (const document of data.documents) {
    if (document.userId !== userId || document.processingStartedAt === null) continue;
    if (document.processingStatus !== 'UPLOADED' && document.processingStatus !== 'PROCESSING') continue;

    const elapsed = now - document.processingStartedAt;
    const before = `${document.processingStatus}/${document.processingStage}`;

    if (elapsed < STAGE_OCR_AT) {
      document.processingStatus = 'UPLOADED';
      document.processingStage = null;
    } else if (elapsed < STAGE_EXTRACTION_AT) {
      document.processingStatus = 'PROCESSING';
      document.processingStage = 'OCR';
    } else if (document.failFirstAttempt && document.attempts === 1 && elapsed >= FAIL_AT) {
      document.processingStatus = 'FAILED';
      document.processingStage = 'EXTRACTION';
      document.errorMessage =
        'The image is too blurry to read the invoice details. Try again, upload a clearer photo, or enter the details manually.';
    } else if (elapsed < STAGE_INDEXING_AT) {
      document.processingStatus = 'PROCESSING';
      document.processingStage = 'EXTRACTION';
    } else if (elapsed < DONE_AT) {
      document.processingStatus = 'PROCESSING';
      document.processingStage = 'INDEXING';
    } else {
      finishDocument(data, document, userId, now);
    }

    if (before !== `${document.processingStatus}/${document.processingStage}`) {
      document.updatedAt = new Date(now).toISOString();
      changed = true;
    }
  }
  return changed;
}

// ---------------------------------------------------------------------------
// Gmail scan — a sync takes ~3 s; the first one "finds" the demo emails
// ---------------------------------------------------------------------------

const SYNC_DURATION = 3200;

export function advanceGmailSync(data: MockDatabase, userId: string, now = Date.now()): boolean {
  const connection = data.gmailConnections.find((item) => item.userId === userId);
  if (!connection || connection.syncStatus !== 'SYNCING' || connection.syncStartedAt === null) return false;
  if (now - connection.syncStartedAt < SYNC_DURATION) return false;

  const found = connection.syncCount === 0 ? createGmailMessages(userId, new Date(now)) : [];
  data.gmailMessages.push(...found);
  connection.syncCount += 1;
  connection.syncStatus = 'IDLE';
  connection.syncStartedAt = null;
  connection.lastSyncedAt = new Date(now).toISOString();
  connection.lastError = null;

  const likelyBills = found.filter((message) => message.confidence >= 0.6).length;
  if (likelyBills > 0) {
    addNotification(data, {
      userId,
      productId: null,
      documentId: null,
      type: 'GMAIL_BILLS_FOUND',
      title: 'New bills found in Gmail',
      message: `Found ${likelyBills} bills and warranty documents in your inbox. Review and import them.`,
      dedupeKey: `GMAIL_BILLS_FOUND:${userId}:${connection.syncCount}`,
    });
  }
  return true;
}
