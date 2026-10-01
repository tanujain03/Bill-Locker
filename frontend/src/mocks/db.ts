import type {
  AppNotification,
  Category,
  DocumentDetail,
  DocumentSource,
  DocumentSummary,
  DocumentType,
  ExtractionResult,
  GmailAttachment,
  GmailConnection,
  GmailMessage,
  GmailMessageStatus,
  GmailSyncStatus,
  NotificationType,
  ProcessingStage,
  ProcessingStatus,
  Product,
  ServiceRecord,
  ServiceType,
  User,
  Warranty,
} from '@/types';
import { createSeedDatabase } from './seed';
import { computeWarranty } from './warranty-engine';

// ---------------------------------------------------------------------------
// Storage model (mirrors the backend tables; every row is owned by a user)
// ---------------------------------------------------------------------------

export interface DbUser {
  id: string;
  name: string;
  email: string;
  /** Mock hash — the real backend uses BCrypt. Plaintext is never stored. */
  passwordHash: string;
  createdAt: string;
}

export interface DbProduct {
  id: string;
  userId: string;
  categoryId: string | null;
  name: string;
  brand: string | null;
  model: string | null;
  serialNumber: string | null;
  purchaseDate: string | null;
  purchasePrice: number | null;
  currency: string;
  seller: string | null;
  invoiceNumber: string | null;
  createdAt: string;
  updatedAt: string;
}

export interface DbWarranty {
  id: string;
  productId: string;
  warrantyMonths: number | null;
  startDate: string | null;
  sourceDocumentId: string | null;
  createdAt: string;
  updatedAt: string;
}

export interface DbDocument {
  id: string;
  userId: string;
  productId: string | null;
  documentType: DocumentType;
  fileName: string;
  mimeType: string;
  fileSize: number;
  source: DocumentSource;
  processingStatus: ProcessingStatus;
  processingStage: ProcessingStage | null;
  processingStartedAt: number | null;
  attempts: number;
  failFirstAttempt: boolean;
  templateKey: string | null;
  /** Type chosen by the user at upload time (the AI respects it). */
  documentTypeHint: DocumentType | null;
  extraction: ExtractionResult | null;
  extractedText: string | null;
  errorMessage: string | null;
  createdAt: string;
  updatedAt: string;
}

export interface DbServiceRecord {
  id: string;
  productId: string;
  serviceDate: string;
  serviceType: ServiceType;
  serviceCenter: string | null;
  cost: number | null;
  nextServiceDate: string | null;
  notes: string | null;
  createdAt: string;
  updatedAt: string;
}

export interface DbNotification {
  id: string;
  userId: string;
  productId: string | null;
  documentId: string | null;
  type: NotificationType;
  title: string;
  message: string;
  scheduledAt: string;
  read: boolean;
  createdAt: string;
  /** Prevents the reminder job from creating the same reminder twice. */
  dedupeKey: string | null;
}

export interface DbGmailConnection {
  userId: string;
  email: string;
  connectedAt: string;
  lastSyncedAt: string | null;
  autoSync: boolean;
  syncStatus: GmailSyncStatus;
  syncStartedAt: number | null;
  lastError: string | null;
  syncCount: number;
}

export interface DbGmailMessage {
  id: string;
  userId: string;
  fromName: string;
  fromEmail: string;
  subject: string;
  snippet: string;
  receivedAt: string;
  attachments: GmailAttachment[];
  detectedType: DocumentType | null;
  confidence: number;
  status: GmailMessageStatus;
  documentIds: string[];
  templateKey: string;
}

export interface MockDatabase {
  version: 1;
  users: DbUser[];
  categories: Category[];
  products: DbProduct[];
  warranties: DbWarranty[];
  documents: DbDocument[];
  serviceRecords: DbServiceRecord[];
  notifications: DbNotification[];
  gmailConnections: DbGmailConnection[];
  gmailMessages: DbGmailMessage[];
}

// ---------------------------------------------------------------------------
// Persistence (localStorage so the demo survives reloads; files stay in memory)
// ---------------------------------------------------------------------------

const STORAGE_KEY = 'billlocker.mock-db.v1';
let database: MockDatabase | null = null;

/** Uploaded files for the current page session (too large for localStorage). */
export const fileStore = new Map<string, Blob>();

function load(): MockDatabase | null {
  try {
    const raw = localStorage.getItem(STORAGE_KEY);
    if (!raw) return null;
    const parsed = JSON.parse(raw) as MockDatabase;
    return parsed.version === 1 ? parsed : null;
  } catch {
    return null;
  }
}

export function db(): MockDatabase {
  if (!database) {
    database = load() ?? createSeedDatabase();
    persist();
  }
  return database;
}

export function persist(): void {
  try {
    localStorage.setItem(STORAGE_KEY, JSON.stringify(database));
  } catch {
    // Quota exceeded / storage blocked: keep working in memory.
  }
}

export function resetDatabase(): void {
  database = createSeedDatabase();
  fileStore.clear();
  persist();
}

// ---------------------------------------------------------------------------
// DTO mappers (what the REST API returns)
// ---------------------------------------------------------------------------

export function toUserDto(user: DbUser): User {
  return { id: user.id, name: user.name, email: user.email, createdAt: user.createdAt };
}

export function warrantyForProduct(productId: string): DbWarranty | undefined {
  return db().warranties.find((warranty) => warranty.productId === productId);
}

export function toProductDto(product: DbProduct): Product {
  const data = db();
  const category = data.categories.find((item) => item.id === product.categoryId) ?? null;
  const records = data.serviceRecords
    .filter((record) => record.productId === product.id)
    .sort((a, b) => b.serviceDate.localeCompare(a.serviceDate));
  return {
    id: product.id,
    categoryId: product.categoryId,
    categoryName: category?.name ?? null,
    categorySlug: category?.slug ?? null,
    name: product.name,
    brand: product.brand,
    model: product.model,
    serialNumber: product.serialNumber,
    purchaseDate: product.purchaseDate,
    purchasePrice: product.purchasePrice,
    currency: product.currency,
    seller: product.seller,
    invoiceNumber: product.invoiceNumber,
    warranty: computeWarranty(warrantyForProduct(product.id)),
    nextServiceDate: records[0]?.nextServiceDate ?? null,
    documentCount: data.documents.filter((document) => document.productId === product.id).length,
    createdAt: product.createdAt,
    updatedAt: product.updatedAt,
  };
}

export function toWarrantyDto(product: DbProduct): Warranty | null {
  const summary = computeWarranty(warrantyForProduct(product.id));
  if (!summary) return null;
  const category = db().categories.find((item) => item.id === product.categoryId);
  return {
    ...summary,
    productId: product.id,
    productName: product.name,
    productBrand: product.brand,
    categoryName: category?.name ?? null,
    categorySlug: category?.slug ?? null,
    sourceDocumentId: warrantyForProduct(product.id)?.sourceDocumentId ?? null,
  };
}

export function toDocumentSummary(document: DbDocument): DocumentSummary {
  const product = db().products.find((item) => item.id === document.productId);
  return {
    id: document.id,
    productId: document.productId,
    productName: product?.name ?? null,
    documentType: document.documentType,
    fileName: document.fileName,
    mimeType: document.mimeType,
    fileSize: document.fileSize,
    processingStatus: document.processingStatus,
    processingStage: document.processingStage,
    source: document.source,
    errorMessage: document.errorMessage,
    createdAt: document.createdAt,
    updatedAt: document.updatedAt,
  };
}

export function toDocumentDetail(document: DbDocument): DocumentDetail {
  const ready = !['UPLOADED', 'PROCESSING'].includes(document.processingStatus);
  return {
    ...toDocumentSummary(document),
    extraction: ready ? document.extraction : null,
    extractedText: ready ? document.extractedText : null,
  };
}

export function toServiceRecordDto(record: DbServiceRecord): ServiceRecord {
  const product = db().products.find((item) => item.id === record.productId);
  return {
    id: record.id,
    productId: record.productId,
    productName: product?.name ?? 'Unknown product',
    serviceDate: record.serviceDate,
    serviceType: record.serviceType,
    serviceCenter: record.serviceCenter,
    cost: record.cost,
    currency: product?.currency ?? 'INR',
    nextServiceDate: record.nextServiceDate,
    notes: record.notes,
    createdAt: record.createdAt,
    updatedAt: record.updatedAt,
  };
}

export function toNotificationDto(notification: DbNotification): AppNotification {
  const { id, productId, documentId, type, title, message, scheduledAt, read, createdAt } = notification;
  return { id, productId, documentId, type, title, message, scheduledAt, read, createdAt };
}

export function toGmailConnectionDto(connection: DbGmailConnection | undefined): GmailConnection {
  if (!connection) {
    return {
      connected: false,
      email: null,
      connectedAt: null,
      lastSyncedAt: null,
      autoSync: false,
      syncStatus: 'IDLE',
      lastError: null,
    };
  }
  return {
    connected: true,
    email: connection.email,
    connectedAt: connection.connectedAt,
    lastSyncedAt: connection.lastSyncedAt,
    autoSync: connection.autoSync,
    syncStatus: connection.syncStatus,
    lastError: connection.lastError,
  };
}

export function toGmailMessageDto(message: DbGmailMessage): GmailMessage {
  const { id, fromName, fromEmail, subject, snippet, receivedAt, attachments, detectedType, confidence, status, documentIds } =
    message;
  return { id, fromName, fromEmail, subject, snippet, receivedAt, attachments, detectedType, confidence, status, documentIds };
}

// ---------------------------------------------------------------------------
// Scoped queries (user isolation, like `WHERE user_id = :currentUser`)
// ---------------------------------------------------------------------------

export function productsOf(userId: string): DbProduct[] {
  return db().products.filter((product) => product.userId === userId);
}

export function documentsOf(userId: string): DbDocument[] {
  return db().documents.filter((document) => document.userId === userId);
}

export function serviceRecordsOf(userId: string): DbServiceRecord[] {
  const productIds = new Set(productsOf(userId).map((product) => product.id));
  return db().serviceRecords.filter((record) => productIds.has(record.productId));
}

export function findOwnedProduct(userId: string, id: string): DbProduct | undefined {
  return db().products.find((product) => product.id === id && product.userId === userId);
}

export function findOwnedDocument(userId: string, id: string): DbDocument | undefined {
  return db().documents.find((document) => document.id === id && document.userId === userId);
}
