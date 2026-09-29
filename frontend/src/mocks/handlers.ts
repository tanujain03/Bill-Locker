import { delay, http, HttpResponse } from 'msw';
import { decodeJwtPayload } from '@/lib/token-storage';
import type {
  ConfirmDocumentRequest,
  DashboardSummary,
  DocumentType,
  ProductInput,
  ServiceRecordInput,
  ServiceType,
  Warranty,
  WarrantyStatus,
} from '@/types';
import { DOCUMENT_TYPES, SERVICE_TYPES, WARRANTY_STATUSES } from '@/types';
import { daysUntil, isValidISODate, todayISO } from '@/utils/date';
import { ALLOWED_MIME_TYPES } from '@/utils/file';
import { upcomingServices } from '@/utils/service';
import { answerQuestion, applySearch, explainSearch, interpretSearch } from './ai';
import {
  db,
  documentsOf,
  fileStore,
  findOwnedDocument,
  findOwnedProduct,
  persist,
  productsOf,
  resetDatabase,
  serviceRecordsOf,
  toDocumentDetail,
  toDocumentSummary,
  toGmailConnectionDto,
  toGmailMessageDto,
  toNotificationDto,
  toProductDto,
  toServiceRecordDto,
  toUserDto,
  toWarrantyDto,
  warrantyForProduct,
  type DbDocument,
  type DbProduct,
  type DbUser,
} from './db';
import { advanceGmailSync, advanceProcessing, runReminderJob } from './jobs';
import { createInvoicePdf } from './pdf';
import { templateByKey, templateForFileName } from './templates';
import { hashPassword, newId, nowIso } from './utils';

const API = '*/api';
const route = (path: string) => `${API}${path}`;
const IS_TEST = import.meta.env.MODE === 'test';
const MAX_UPLOAD_BYTES = 10 * 1024 * 1024;
const TOKEN_TTL_SECONDS = 24 * 60 * 60;

// ---------------------------------------------------------------------------
// Plumbing
// ---------------------------------------------------------------------------

function latency(base = 220, jitter = 260): number {
  return IS_TEST ? 0 : base + Math.round(Math.random() * jitter);
}

function apiError(status: number, code: string, message: string, fieldErrors?: Record<string, string>) {
  return HttpResponse.json({ success: false as const, code, message, ...(fieldErrors ? { fieldErrors } : {}) }, { status });
}

const unauthorized = () => apiError(401, 'UNAUTHORIZED', 'Your session has expired. Please sign in again.');
const validationError = (fieldErrors: Record<string, string>) =>
  apiError(400, 'VALIDATION_ERROR', 'Some of the information provided is invalid.', fieldErrors);
const productNotFound = () => apiError(404, 'PRODUCT_NOT_FOUND', 'The requested product was not found.');
const documentNotFound = () => apiError(404, 'DOCUMENT_NOT_FOUND', 'The requested document was not found.');

function base64Url(value: string): string {
  return btoa(value).replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/, '');
}

function issueToken(userId: string): { token: string; expiresAt: string } {
  const now = Math.floor(Date.now() / 1000);
  const exp = now + TOKEN_TTL_SECONDS;
  const token = `${base64Url(JSON.stringify({ alg: 'none', typ: 'JWT' }))}.${base64Url(
    JSON.stringify({ sub: userId, iat: now, exp }),
  )}.mock-signature`;
  return { token, expiresAt: new Date(exp * 1000).toISOString() };
}

/** Resolves the user from the Bearer token — never from anything the client claims. */
async function currentUser(request: Request, wait = latency()): Promise<DbUser | null> {
  await delay(wait);
  const header = request.headers.get('Authorization');
  if (!header?.startsWith('Bearer ')) return null;
  const payload = decodeJwtPayload(header.slice(7));
  if (!payload?.sub || !payload.exp || payload.exp * 1000 < Date.now()) return null;
  return db().users.find((user) => user.id === payload.sub) ?? null;
}

async function readJson<T>(request: Request): Promise<Partial<T>> {
  try {
    const body = (await request.json()) as unknown;
    return body && typeof body === 'object' ? (body as Partial<T>) : {};
  } catch {
    return {};
  }
}

function param(value: string | readonly string[] | undefined): string {
  return typeof value === 'string' ? value : '';
}

function sync(userId: string): void {
  const data = db();
  const changed = advanceProcessing(data, userId) || advanceGmailSync(data, userId);
  if (changed) persist();
}

// ---------------------------------------------------------------------------
// Validation (the real backend does this with Bean Validation)
// ---------------------------------------------------------------------------

type Errors = Record<string, string>;

function optionalText(value: unknown, field: string, max: number, errors: Errors): string | null {
  if (value === null || value === undefined || value === '') return null;
  if (typeof value !== 'string') {
    errors[field] = 'Invalid value';
    return null;
  }
  const trimmed = value.trim();
  if (trimmed.length > max) errors[field] = `Keep it under ${max} characters`;
  return trimmed || null;
}

function validateProductInput(body: Partial<ProductInput>): { value?: ProductInput; errors: Errors } {
  const errors: Errors = {};
  const name = typeof body.name === 'string' ? body.name.trim() : '';
  if (!name) errors.name = 'Product name is required';
  else if (name.length > 120) errors.name = 'Keep it under 120 characters';

  const categoryId = body.categoryId ?? null;
  if (categoryId !== null && !db().categories.some((category) => category.id === categoryId)) errors.categoryId = 'Unknown category';

  const purchaseDate = body.purchaseDate ?? null;
  if (purchaseDate !== null && !isValidISODate(purchaseDate)) errors.purchaseDate = 'Enter a valid date';
  else if (purchaseDate !== null && purchaseDate > todayISO()) errors.purchaseDate = 'Purchase date cannot be in the future';

  const purchasePrice = body.purchasePrice ?? null;
  if (purchasePrice !== null && (typeof purchasePrice !== 'number' || !Number.isFinite(purchasePrice) || purchasePrice < 0 || purchasePrice > 100_000_000)) {
    errors.purchasePrice = 'Enter a valid amount';
  }

  const warrantyMonths = body.warrantyMonths ?? null;
  if (warrantyMonths !== null && (!Number.isInteger(warrantyMonths) || warrantyMonths < 0 || warrantyMonths > 240)) {
    errors.warrantyMonths = 'Enter a whole number of months (0–240)';
  }

  const currency = typeof body.currency === 'string' && /^[A-Z]{3}$/.test(body.currency) ? body.currency : 'INR';

  const value: ProductInput = {
    name,
    categoryId,
    brand: optionalText(body.brand, 'brand', 80, errors),
    model: optionalText(body.model, 'model', 80, errors),
    serialNumber: optionalText(body.serialNumber, 'serialNumber', 80, errors),
    purchaseDate,
    purchasePrice,
    currency,
    seller: optionalText(body.seller, 'seller', 120, errors),
    invoiceNumber: optionalText(body.invoiceNumber, 'invoiceNumber', 80, errors),
    warrantyMonths,
  };
  return Object.keys(errors).length > 0 ? { errors } : { value, errors };
}

function validateServiceInput(body: Partial<ServiceRecordInput>): { value?: ServiceRecordInput; errors: Errors } {
  const errors: Errors = {};
  const productId = typeof body.productId === 'string' ? body.productId : '';
  if (!productId) errors.productId = 'Choose a product';
  const serviceDate = body.serviceDate ?? '';
  if (!isValidISODate(serviceDate)) errors.serviceDate = 'Enter the service date';
  else if (serviceDate > todayISO()) errors.serviceDate = 'Service date cannot be in the future';
  const serviceType = body.serviceType as ServiceType;
  if (!SERVICE_TYPES.includes(serviceType)) errors.serviceType = 'Choose a service type';
  const cost = body.cost ?? null;
  if (cost !== null && (typeof cost !== 'number' || cost < 0 || !Number.isFinite(cost))) errors.cost = 'Enter a valid amount';
  const nextServiceDate = body.nextServiceDate ?? null;
  if (nextServiceDate !== null && !isValidISODate(nextServiceDate)) errors.nextServiceDate = 'Enter a valid date';
  else if (nextServiceDate !== null && isValidISODate(serviceDate) && nextServiceDate <= serviceDate) {
    errors.nextServiceDate = 'The next service must be after the service date';
  }
  const value: ServiceRecordInput = {
    productId,
    serviceDate,
    serviceType,
    serviceCenter: optionalText(body.serviceCenter, 'serviceCenter', 120, errors),
    cost,
    nextServiceDate,
    notes: optionalText(body.notes, 'notes', 500, errors),
  };
  return Object.keys(errors).length > 0 ? { errors } : { value, errors };
}

// ---------------------------------------------------------------------------
// Domain operations
// ---------------------------------------------------------------------------

function applyProductInput(product: DbProduct, input: ProductInput, sourceDocumentId: string | null): void {
  const now = nowIso();
  Object.assign(product, {
    categoryId: input.categoryId,
    name: input.name,
    brand: input.brand,
    model: input.model,
    serialNumber: input.serialNumber,
    purchaseDate: input.purchaseDate,
    purchasePrice: input.purchasePrice,
    currency: input.currency,
    seller: input.seller,
    invoiceNumber: input.invoiceNumber,
    updatedAt: now,
  });
  const data = db();
  const warranty = warrantyForProduct(product.id);
  if (warranty) {
    warranty.warrantyMonths = input.warrantyMonths;
    warranty.startDate = input.purchaseDate;
    if (sourceDocumentId) warranty.sourceDocumentId = sourceDocumentId;
    warranty.updatedAt = now;
  } else {
    data.warranties.push({
      id: newId('wty'),
      productId: product.id,
      warrantyMonths: input.warrantyMonths,
      startDate: input.purchaseDate,
      sourceDocumentId,
      createdAt: now,
      updatedAt: now,
    });
  }
}

function createProduct(userId: string, input: ProductInput, sourceDocumentId: string | null): DbProduct {
  const now = nowIso();
  const product: DbProduct = {
    id: newId('prd'),
    userId,
    categoryId: null,
    name: input.name,
    brand: null,
    model: null,
    serialNumber: null,
    purchaseDate: null,
    purchasePrice: null,
    currency: 'INR',
    seller: null,
    invoiceNumber: null,
    createdAt: now,
    updatedAt: now,
  };
  db().products.push(product);
  applyProductInput(product, input, sourceDocumentId);
  return product;
}

function createDocument(
  user: DbUser,
  fields: Pick<DbDocument, 'fileName' | 'mimeType' | 'fileSize' | 'source' | 'productId' | 'templateKey' | 'documentTypeHint'>,
): DbDocument {
  const now = nowIso();
  const document: DbDocument = {
    id: newId('doc'),
    userId: user.id,
    documentType: fields.documentTypeHint ?? 'OTHER',
    processingStatus: 'UPLOADED',
    processingStage: null,
    processingStartedAt: Date.now(),
    attempts: 1,
    failFirstAttempt: /fail|corrupt|blurry/i.test(fields.fileName),
    extraction: null,
    extractedText: null,
    errorMessage: null,
    createdAt: now,
    updatedAt: now,
    ...fields,
  };
  db().documents.push(document);
  return document;
}

function safeHeaderFileName(fileName: string): string {
  return fileName.replace(/[^\x20-\x7E]/g, '_').replace(/"/g, "'");
}

function dashboardFor(user: DbUser): DashboardSummary {
  const products = productsOf(user.id).map(toProductDto);
  const documents = documentsOf(user.id);
  const warranties = productsOf(user.id)
    .map(toWarrantyDto)
    .filter((warranty): warranty is Warranty => warranty !== null);
  const count = (status: WarrantyStatus) => warranties.filter((warranty) => warranty.status === status).length;

  const spending = new Map<string, { categoryName: string; categorySlug: string | null; amount: number }>();
  for (const product of products) {
    if (product.purchasePrice === null) continue;
    const key = product.categoryName ?? 'Other';
    const entry = spending.get(key) ?? { categoryName: key, categorySlug: product.categorySlug, amount: 0 };
    entry.amount += product.purchasePrice;
    spending.set(key, entry);
  }

  return {
    totalProducts: products.length,
    totalDocuments: documents.length,
    documentsToReview: documents.filter((document) => ['REVIEW_REQUIRED', 'PROCESSED'].includes(document.processingStatus)).length,
    totalSpending: products.reduce((sum, product) => sum + (product.purchasePrice ?? 0), 0),
    currency: 'INR',
    warranties: {
      total: warranties.length,
      active: count('ACTIVE'),
      expiringSoon: count('EXPIRING_SOON'),
      expired: count('EXPIRED'),
      unknown: count('UNKNOWN'),
    },
    spendingByCategory: [...spending.values()].sort((a, b) => b.amount - a.amount),
    upcomingExpirations: warranties
      .filter((warranty) => warranty.daysRemaining !== null && warranty.daysRemaining >= 0 && warranty.daysRemaining <= 90)
      .sort((a, b) => (a.daysRemaining ?? 0) - (b.daysRemaining ?? 0)),
    upcomingServices: upcomingServices(serviceRecordsOf(user.id).map(toServiceRecordDto)).filter(
      (record) => daysUntil(record.nextServiceDate) <= 60,
    ),
    recentDocuments: [...documents]
      .sort((a, b) => b.createdAt.localeCompare(a.createdAt))
      .slice(0, 5)
      .map(toDocumentSummary),
  };
}

/** Builds a preview PDF for seeded documents (the mock has no stored file for them). */
function previewPdfFor(document: DbDocument, user: DbUser): Blob {
  const product = document.productId ? findOwnedProduct(user.id, document.productId) : undefined;
  const extraction = document.extraction;
  const template = document.templateKey ? templateByKey(document.templateKey) : null;
  const isReceipt = document.documentType === 'SERVICE_RECEIPT' || document.documentType === 'REPAIR_RECEIPT';
  const receiptAmount = /Rs\.\s?([\d,]+(?:\.\d{2})?)/.exec(document.extractedText ?? '')?.[1];
  const price = product?.purchasePrice ?? extraction?.purchasePrice ?? null;
  const months = product ? (warrantyForProduct(product.id)?.warrantyMonths ?? null) : (extraction?.warrantyMonths ?? null);

  return createInvoicePdf({
    title: document.documentType === 'WARRANTY_CARD' ? 'WARRANTY CERTIFICATE' : isReceipt ? 'SERVICE / REPAIR RECEIPT' : 'TAX INVOICE',
    seller: extraction?.seller ?? product?.seller ?? template?.seller ?? 'Retail store',
    invoiceNumber: extraction?.invoiceNumber ?? product?.invoiceNumber ?? null,
    date: extraction?.purchaseDate ?? product?.purchaseDate ?? null,
    billTo: user.name,
    itemName: isReceipt ? `Service for ${product?.name ?? 'product'}` : (extraction?.productName ?? product?.name ?? 'Item'),
    model: extraction?.model ?? product?.model ?? null,
    serialNumber: extraction?.serialNumber ?? product?.serialNumber ?? null,
    amount: isReceipt && receiptAmount ? `Rs. ${receiptAmount}` : price !== null ? `Rs. ${price.toLocaleString('en-IN')}.00` : '-',
    warranty: isReceipt ? null : (template?.warrantyText ?? (months ? `${months} months from the date of purchase` : null)),
  });
}

// ---------------------------------------------------------------------------
// Handlers
// ---------------------------------------------------------------------------

export const handlers = [
  http.get(route('/health'), () => HttpResponse.json({ status: 'UP', mode: 'mock' })),

  http.post(route('/__mock/reset'), async () => {
    await delay(latency());
    resetDatabase();
    return HttpResponse.json({ success: true });
  }),

  // ----- Auth -------------------------------------------------------------
  http.post(route('/auth/register'), async ({ request }) => {
    await delay(latency(400));
    const body = await readJson<{ name: string; email: string; password: string }>(request);
    const errors: Errors = {};
    const name = typeof body.name === 'string' ? body.name.trim() : '';
    const email = typeof body.email === 'string' ? body.email.trim().toLowerCase() : '';
    const password = typeof body.password === 'string' ? body.password : '';
    if (name.length < 2) errors.name = 'Enter your name';
    if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email)) errors.email = 'Enter a valid email address';
    if (password.length < 8 || !/[A-Za-z]/.test(password) || !/\d/.test(password)) {
      errors.password = 'Use at least 8 characters with a letter and a number';
    }
    if (Object.keys(errors).length > 0) return validationError(errors);
    if (db().users.some((user) => user.email === email)) {
      return apiError(409, 'EMAIL_ALREADY_REGISTERED', 'An account with this email already exists.', {
        email: 'This email is already registered',
      });
    }
    const user: DbUser = { id: newId('usr'), name, email, passwordHash: hashPassword(password), createdAt: nowIso() };
    db().users.push(user);
    persist();
    return HttpResponse.json({ ...issueToken(user.id), tokenType: 'Bearer' as const, user: toUserDto(user) }, { status: 201 });
  }),

  http.post(route('/auth/login'), async ({ request }) => {
    await delay(latency(400));
    const body = await readJson<{ email: string; password: string }>(request);
    const email = typeof body.email === 'string' ? body.email.trim().toLowerCase() : '';
    const user = db().users.find((candidate) => candidate.email === email);
    if (!user || user.passwordHash !== hashPassword(typeof body.password === 'string' ? body.password : '')) {
      // Same message for unknown email and wrong password (no account enumeration).
      return apiError(401, 'INVALID_CREDENTIALS', 'Incorrect email or password.');
    }
    return HttpResponse.json({ ...issueToken(user.id), tokenType: 'Bearer' as const, user: toUserDto(user) });
  }),

  http.get(route('/auth/me'), async ({ request }) => {
    const user = await currentUser(request);
    return user ? HttpResponse.json(toUserDto(user)) : unauthorized();
  }),

  http.put(route('/users/me'), async ({ request }) => {
    const user = await currentUser(request);
    if (!user) return unauthorized();
    const body = await readJson<{ name: string }>(request);
    const name = typeof body.name === 'string' ? body.name.trim() : '';
    if (name.length < 2 || name.length > 80) return validationError({ name: 'Enter your name (2–80 characters)' });
    user.name = name;
    persist();
    return HttpResponse.json(toUserDto(user));
  }),

  // ----- Categories & products ------------------------------------------------
  http.get(route('/categories'), async ({ request }) => {
    const user = await currentUser(request);
    return user ? HttpResponse.json(db().categories) : unauthorized();
  }),

  http.get(route('/products'), async ({ request }) => {
    const user = await currentUser(request);
    if (!user) return unauthorized();
    const url = new URL(request.url);
    const search = url.searchParams.get('search')?.trim().toLowerCase() ?? '';
    const categoryId = url.searchParams.get('categoryId');
    const status = url.searchParams.get('warrantyStatus') as WarrantyStatus | null;
    const products = productsOf(user.id)
      .map(toProductDto)
      .filter((product) => {
        if (categoryId && product.categoryId !== categoryId) return false;
        if (status && WARRANTY_STATUSES.includes(status) && (product.warranty?.status ?? 'UNKNOWN') !== status) return false;
        if (!search) return true;
        return [product.name, product.brand, product.model, product.seller, product.serialNumber, product.invoiceNumber]
          .filter(Boolean)
          .some((value) => (value as string).toLowerCase().includes(search));
      })
      .sort((a, b) => b.createdAt.localeCompare(a.createdAt));
    return HttpResponse.json(products);
  }),

  http.get(route('/products/:id'), async ({ request, params }) => {
    const user = await currentUser(request);
    if (!user) return unauthorized();
    const product = findOwnedProduct(user.id, param(params.id));
    return product ? HttpResponse.json(toProductDto(product)) : productNotFound();
  }),

  http.post(route('/products'), async ({ request }) => {
    const user = await currentUser(request);
    if (!user) return unauthorized();
    const { value, errors } = validateProductInput(await readJson<ProductInput>(request));
    if (!value) return validationError(errors);
    const product = createProduct(user.id, value, null);
    persist();
    return HttpResponse.json(toProductDto(product), { status: 201 });
  }),

  http.put(route('/products/:id'), async ({ request, params }) => {
    const user = await currentUser(request);
    if (!user) return unauthorized();
    const product = findOwnedProduct(user.id, param(params.id));
    if (!product) return productNotFound();
    const { value, errors } = validateProductInput(await readJson<ProductInput>(request));
    if (!value) return validationError(errors);
    applyProductInput(product, value, null);
    persist();
    return HttpResponse.json(toProductDto(product));
  }),

  http.delete(route('/products/:id'), async ({ request, params }) => {
    const user = await currentUser(request);
    if (!user) return unauthorized();
    const product = findOwnedProduct(user.id, param(params.id));
    if (!product) return productNotFound();
    const data = db();
    data.products = data.products.filter((item) => item.id !== product.id);
    data.warranties = data.warranties.filter((warranty) => warranty.productId !== product.id);
    data.serviceRecords = data.serviceRecords.filter((record) => record.productId !== product.id);
    data.notifications = data.notifications.filter((notification) => notification.productId !== product.id);
    // Documents are kept (never lose a bill) — just unlinked.
    for (const document of data.documents) if (document.productId === product.id) document.productId = null;
    persist();
    return new HttpResponse(null, { status: 204 });
  }),

  // ----- Warranties & dashboard --------------------------------------------
  http.get(route('/warranties'), async ({ request }) => {
    const user = await currentUser(request);
    if (!user) return unauthorized();
    const status = new URL(request.url).searchParams.get('status');
    const warranties = productsOf(user.id)
      .map(toWarrantyDto)
      .filter((warranty): warranty is Warranty => warranty !== null)
      .filter((warranty) => !status || warranty.status === status)
      .sort((a, b) => (a.expiryDate ?? '9999').localeCompare(b.expiryDate ?? '9999'));
    return HttpResponse.json(warranties);
  }),

  http.get(route('/dashboard/summary'), async ({ request }) => {
    const user = await currentUser(request);
    if (!user) return unauthorized();
    sync(user.id);
    return HttpResponse.json(dashboardFor(user));
  }),

  // ----- Documents ---------------------------------------------------------
  http.get(route('/documents'), async ({ request }) => {
    const user = await currentUser(request, latency(150, 150));
    if (!user) return unauthorized();
    sync(user.id);
    const url = new URL(request.url);
    const productId = url.searchParams.get('productId');
    const status = url.searchParams.get('status');
    const documentType = url.searchParams.get('documentType');
    const documents = documentsOf(user.id)
      .filter((document) => !productId || document.productId === productId)
      .filter((document) => !status || document.processingStatus === status)
      .filter((document) => !documentType || document.documentType === documentType)
      .sort((a, b) => b.createdAt.localeCompare(a.createdAt))
      .map(toDocumentSummary);
    return HttpResponse.json(documents);
  }),

  http.post(route('/documents/upload'), async ({ request }) => {
    const user = await currentUser(request, latency(500, 400));
    if (!user) return unauthorized();
    let form: FormData;
    try {
      form = await request.formData();
    } catch {
      return apiError(400, 'INVALID_UPLOAD', 'The upload could not be read.');
    }
    const file = form.get('file');
    if (!(file instanceof Blob) || file.size === 0) return apiError(400, 'FILE_REQUIRED', 'Choose a non-empty file to upload.');
    const fileName = (file as File).name || 'document';
    const extension = fileName.slice(fileName.lastIndexOf('.')).toLowerCase();
    if (!(ALLOWED_MIME_TYPES as readonly string[]).includes(file.type) || !['.pdf', '.jpg', '.jpeg', '.png', '.webp'].includes(extension)) {
      return apiError(415, 'UNSUPPORTED_FILE_TYPE', 'Unsupported file type. Upload a PDF, JPG, PNG or WEBP.');
    }
    if (file.size > MAX_UPLOAD_BYTES) return apiError(413, 'FILE_TOO_LARGE', 'The file is larger than 10 MB.');

    const productId = form.get('productId');
    if (typeof productId === 'string' && productId && !findOwnedProduct(user.id, productId)) return productNotFound();
    const hint = form.get('documentType');
    const documentTypeHint = typeof hint === 'string' && DOCUMENT_TYPES.includes(hint as DocumentType) ? (hint as DocumentType) : null;

    const document = createDocument(user, {
      fileName: fileName.replace(/[\\/]/g, '_').slice(0, 180),
      mimeType: file.type,
      fileSize: file.size,
      source: 'UPLOAD',
      productId: typeof productId === 'string' && productId ? productId : null,
      templateKey: templateForFileName(fileName).key,
      documentTypeHint,
    });
    fileStore.set(document.id, file);
    persist();
    return HttpResponse.json(toDocumentSummary(document), { status: 201 });
  }),

  http.get(route('/documents/:id'), async ({ request, params }) => {
    const user = await currentUser(request, latency(120, 120));
    if (!user) return unauthorized();
    sync(user.id);
    const document = findOwnedDocument(user.id, param(params.id));
    return document ? HttpResponse.json(toDocumentDetail(document)) : documentNotFound();
  }),

  http.get(route('/documents/:id/download'), async ({ request, params }) => {
    const user = await currentUser(request);
    if (!user) return unauthorized();
    const document = findOwnedDocument(user.id, param(params.id));
    if (!document) return documentNotFound();
    const headers = {
      'Content-Type': document.mimeType,
      'Content-Disposition': `inline; filename="${safeHeaderFileName(document.fileName)}"`,
    };
    const stored = fileStore.get(document.id);
    if (stored) return new HttpResponse(stored, { headers });
    if (document.mimeType !== 'application/pdf') {
      return apiError(404, 'FILE_NOT_AVAILABLE', 'In demo mode, uploaded files are kept only until the page is reloaded.');
    }
    return new HttpResponse(previewPdfFor(document, user), { headers });
  }),

  http.post(route('/documents/:id/confirm'), async ({ request, params }) => {
    const user = await currentUser(request, latency(500, 300));
    if (!user) return unauthorized();
    sync(user.id);
    const document = findOwnedDocument(user.id, param(params.id));
    if (!document) return documentNotFound();
    if (document.processingStatus === 'CONFIRMED') return apiError(409, 'DOCUMENT_ALREADY_CONFIRMED', 'This document has already been saved.');
    if (document.processingStatus === 'UPLOADED' || document.processingStatus === 'PROCESSING') {
      return apiError(409, 'DOCUMENT_NOT_READY', 'This document is still being processed.');
    }
    const body = await readJson<ConfirmDocumentRequest>(request);
    const documentType = DOCUMENT_TYPES.includes(body.documentType as DocumentType) ? (body.documentType as DocumentType) : null;
    if (!documentType) return validationError({ documentType: 'Choose a document type' });
    const { value, errors } = validateProductInput(body.product ?? {});
    if (!value) return validationError(errors);

    let product: DbProduct;
    if (body.productId) {
      const existing = findOwnedProduct(user.id, body.productId);
      if (!existing) return productNotFound();
      applyProductInput(existing, value, document.id);
      product = existing;
    } else {
      product = createProduct(user.id, value, document.id);
    }
    document.productId = product.id;
    document.documentType = documentType;
    document.processingStatus = 'CONFIRMED';
    document.updatedAt = nowIso();
    persist();
    return HttpResponse.json({ document: toDocumentSummary(document), product: toProductDto(product) });
  }),

  http.post(route('/documents/:id/reprocess'), async ({ request, params }) => {
    const user = await currentUser(request);
    if (!user) return unauthorized();
    const document = findOwnedDocument(user.id, param(params.id));
    if (!document) return documentNotFound();
    if (document.processingStatus === 'CONFIRMED') return apiError(409, 'DOCUMENT_ALREADY_CONFIRMED', 'This document has already been saved.');
    document.processingStatus = 'UPLOADED';
    document.processingStage = null;
    document.processingStartedAt = Date.now();
    document.attempts += 1;
    document.errorMessage = null;
    document.extraction = null;
    document.updatedAt = nowIso();
    persist();
    return HttpResponse.json(toDocumentSummary(document));
  }),

  http.delete(route('/documents/:id'), async ({ request, params }) => {
    const user = await currentUser(request);
    if (!user) return unauthorized();
    const document = findOwnedDocument(user.id, param(params.id));
    if (!document) return documentNotFound();
    const data = db();
    data.documents = data.documents.filter((item) => item.id !== document.id);
    for (const warranty of data.warranties) if (warranty.sourceDocumentId === document.id) warranty.sourceDocumentId = null;
    for (const message of data.gmailMessages) message.documentIds = message.documentIds.filter((id) => id !== document.id);
    data.notifications = data.notifications.filter((notification) => notification.documentId !== document.id);
    fileStore.delete(document.id);
    persist();
    return new HttpResponse(null, { status: 204 });
  }),

  // ----- Service records ---------------------------------------------------
  http.get(route('/service-records'), async ({ request }) => {
    const user = await currentUser(request);
    if (!user) return unauthorized();
    const productId = new URL(request.url).searchParams.get('productId');
    const records = serviceRecordsOf(user.id)
      .filter((record) => !productId || record.productId === productId)
      .sort((a, b) => b.serviceDate.localeCompare(a.serviceDate))
      .map(toServiceRecordDto);
    return HttpResponse.json(records);
  }),

  http.post(route('/service-records'), async ({ request }) => {
    const user = await currentUser(request);
    if (!user) return unauthorized();
    const { value, errors } = validateServiceInput(await readJson<ServiceRecordInput>(request));
    if (!value) return validationError(errors);
    if (!findOwnedProduct(user.id, value.productId)) return productNotFound();
    const now = nowIso();
    const record = { id: newId('svc'), ...value, createdAt: now, updatedAt: now };
    db().serviceRecords.push(record);
    persist();
    return HttpResponse.json(toServiceRecordDto(record), { status: 201 });
  }),

  http.put(route('/service-records/:id'), async ({ request, params }) => {
    const user = await currentUser(request);
    if (!user) return unauthorized();
    const record = serviceRecordsOf(user.id).find((item) => item.id === param(params.id));
    if (!record) return apiError(404, 'SERVICE_RECORD_NOT_FOUND', 'The requested service record was not found.');
    const { value, errors } = validateServiceInput(await readJson<ServiceRecordInput>(request));
    if (!value) return validationError(errors);
    if (!findOwnedProduct(user.id, value.productId)) return productNotFound();
    Object.assign(record, value, { updatedAt: nowIso() });
    persist();
    return HttpResponse.json(toServiceRecordDto(record));
  }),

  http.delete(route('/service-records/:id'), async ({ request, params }) => {
    const user = await currentUser(request);
    if (!user) return unauthorized();
    const record = serviceRecordsOf(user.id).find((item) => item.id === param(params.id));
    if (!record) return apiError(404, 'SERVICE_RECORD_NOT_FOUND', 'The requested service record was not found.');
    const data = db();
    data.serviceRecords = data.serviceRecords.filter((item) => item.id !== record.id);
    persist();
    return new HttpResponse(null, { status: 204 });
  }),

  // ----- Notifications -----------------------------------------------------
  http.get(route('/notifications'), async ({ request }) => {
    const user = await currentUser(request);
    if (!user) return unauthorized();
    sync(user.id);
    runReminderJob(db(), user.id);
    persist();
    const list = db()
      .notifications.filter((notification) => notification.userId === user.id)
      .sort((a, b) => b.createdAt.localeCompare(a.createdAt))
      .map(toNotificationDto);
    return HttpResponse.json(list);
  }),

  http.get(route('/notifications/unread-count'), async ({ request }) => {
    const user = await currentUser(request, latency(100, 100));
    if (!user) return unauthorized();
    sync(user.id);
    runReminderJob(db(), user.id);
    persist();
    const count = db().notifications.filter((notification) => notification.userId === user.id && !notification.read).length;
    return HttpResponse.json({ count });
  }),

  http.patch(route('/notifications/:id/read'), async ({ request, params }) => {
    const user = await currentUser(request, latency(80, 80));
    if (!user) return unauthorized();
    const notification = db().notifications.find((item) => item.id === param(params.id) && item.userId === user.id);
    if (!notification) return apiError(404, 'NOTIFICATION_NOT_FOUND', 'The notification was not found.');
    notification.read = true;
    persist();
    return HttpResponse.json(toNotificationDto(notification));
  }),

  http.post(route('/notifications/read-all'), async ({ request }) => {
    const user = await currentUser(request);
    if (!user) return unauthorized();
    for (const notification of db().notifications) if (notification.userId === user.id) notification.read = true;
    persist();
    return new HttpResponse(null, { status: 204 });
  }),

  // ----- AI ------------------------------------------------------------------
  http.post(route('/ai/chat'), async ({ request }) => {
    const user = await currentUser(request, latency(900, 700));
    if (!user) return unauthorized();
    const body = await readJson<{ message: string; sessionId: string | null }>(request);
    const message = typeof body.message === 'string' ? body.message.trim() : '';
    if (!message) return validationError({ message: 'Ask a question' });
    if (message.length > 1000) return validationError({ message: 'Keep questions under 1000 characters' });
    sync(user.id);
    const answer = answerQuestion(message, {
      products: productsOf(user.id).map(toProductDto),
      documents: documentsOf(user.id).map(toDocumentSummary),
      services: serviceRecordsOf(user.id).map(toServiceRecordDto),
    });
    return HttpResponse.json({
      sessionId: typeof body.sessionId === 'string' && body.sessionId ? body.sessionId : newId('chat'),
      message: { id: newId('msg'), role: 'ASSISTANT' as const, content: answer.content, references: answer.references, createdAt: nowIso() },
    });
  }),

  http.post(route('/ai/search'), async ({ request }) => {
    const user = await currentUser(request, latency(600, 400));
    if (!user) return unauthorized();
    const body = await readJson<{ query: string }>(request);
    const query = typeof body.query === 'string' ? body.query.trim() : '';
    if (!query) return validationError({ query: 'Enter a search' });
    const products = productsOf(user.id).map(toProductDto);
    const filters = interpretSearch(query, db().categories, products);
    return HttpResponse.json({
      query,
      filters,
      explanation: explainSearch(filters, db().categories),
      results: applySearch(products, filters),
    });
  }),

  // ----- Gmail integration -------------------------------------------------
  http.get(route('/integrations/gmail'), async ({ request }) => {
    const user = await currentUser(request, latency(120, 120));
    if (!user) return unauthorized();
    sync(user.id);
    return HttpResponse.json(toGmailConnectionDto(db().gmailConnections.find((item) => item.userId === user.id)));
  }),

  http.post(route('/integrations/gmail/connect'), async ({ request }) => {
    const user = await currentUser(request, latency(500, 300));
    if (!user) return unauthorized();
    const data = db();
    if (!data.gmailConnections.some((item) => item.userId === user.id)) {
      // Mock of the OAuth callback: the account is connected and a first scan starts.
      data.gmailConnections.push({
        userId: user.id,
        email: user.email,
        connectedAt: nowIso(),
        lastSyncedAt: null,
        autoSync: true,
        syncStatus: 'SYNCING',
        syncStartedAt: Date.now(),
        lastError: null,
        syncCount: 0,
      });
      persist();
    }
    return HttpResponse.json({ authorizationUrl: '/gmail?status=connected' });
  }),

  http.delete(route('/integrations/gmail'), async ({ request }) => {
    const user = await currentUser(request);
    if (!user) return unauthorized();
    const data = db();
    data.gmailConnections = data.gmailConnections.filter((item) => item.userId !== user.id);
    data.gmailMessages = data.gmailMessages.filter((item) => item.userId !== user.id);
    persist();
    return new HttpResponse(null, { status: 204 });
  }),

  http.post(route('/integrations/gmail/sync'), async ({ request }) => {
    const user = await currentUser(request);
    if (!user) return unauthorized();
    const connection = db().gmailConnections.find((item) => item.userId === user.id);
    if (!connection) return apiError(409, 'GMAIL_NOT_CONNECTED', 'Connect Gmail first.');
    if (connection.syncStatus !== 'SYNCING') {
      connection.syncStatus = 'SYNCING';
      connection.syncStartedAt = Date.now();
      persist();
    }
    return HttpResponse.json(toGmailConnectionDto(connection));
  }),

  http.put(route('/integrations/gmail/settings'), async ({ request }) => {
    const user = await currentUser(request);
    if (!user) return unauthorized();
    const connection = db().gmailConnections.find((item) => item.userId === user.id);
    if (!connection) return apiError(409, 'GMAIL_NOT_CONNECTED', 'Connect Gmail first.');
    const body = await readJson<{ autoSync: boolean }>(request);
    if (typeof body.autoSync !== 'boolean') return validationError({ autoSync: 'Must be true or false' });
    connection.autoSync = body.autoSync;
    persist();
    return HttpResponse.json(toGmailConnectionDto(connection));
  }),

  http.get(route('/integrations/gmail/messages'), async ({ request }) => {
    const user = await currentUser(request);
    if (!user) return unauthorized();
    sync(user.id);
    const messages = db()
      .gmailMessages.filter((message) => message.userId === user.id)
      .sort((a, b) => b.receivedAt.localeCompare(a.receivedAt))
      .map(toGmailMessageDto);
    return HttpResponse.json(messages);
  }),

  http.post(route('/integrations/gmail/import'), async ({ request }) => {
    const user = await currentUser(request, latency(600, 400));
    if (!user) return unauthorized();
    const body = await readJson<{ messageIds: string[] }>(request);
    const ids = Array.isArray(body.messageIds) ? body.messageIds.filter((id): id is string => typeof id === 'string') : [];
    if (ids.length === 0) return validationError({ messageIds: 'Choose at least one email' });
    const messages = db().gmailMessages.filter((message) => message.userId === user.id && ids.includes(message.id));
    if (messages.length !== ids.length) return apiError(404, 'GMAIL_MESSAGE_NOT_FOUND', 'Some of the selected emails were not found.');

    const created = messages.flatMap((message) => {
      if (message.status === 'IMPORTED') return [];
      const template = templateByKey(message.templateKey);
      const sources =
        message.attachments.length > 0
          ? message.attachments
          : [{ fileName: `${message.subject.replace(/[^\w -]/g, '').slice(0, 60).trim()}.pdf`, mimeType: 'application/pdf', size: 24_000 }];
      const documents = sources.map((attachment) =>
        createDocument(user, {
          fileName: attachment.fileName,
          mimeType: attachment.mimeType,
          fileSize: attachment.size,
          source: 'GMAIL',
          productId: null,
          templateKey: template.key,
          documentTypeHint: null,
        }),
      );
      message.status = 'IMPORTED';
      message.documentIds = documents.map((document) => document.id);
      return documents;
    });
    persist();
    return HttpResponse.json({ documents: created.map(toDocumentSummary) });
  }),

  http.post(route('/integrations/gmail/messages/:id/ignore'), async ({ request, params }) => {
    const user = await currentUser(request);
    if (!user) return unauthorized();
    const message = db().gmailMessages.find((item) => item.id === param(params.id) && item.userId === user.id);
    if (!message) return apiError(404, 'GMAIL_MESSAGE_NOT_FOUND', 'The email was not found.');
    if (message.status === 'NEW') message.status = 'IGNORED';
    persist();
    return HttpResponse.json(toGmailMessageDto(message));
  }),

  // Anything else under /api is a contract gap — fail loudly instead of hitting the network.
  http.all(route('/*'), ({ request }) =>
    apiError(404, 'NOT_IMPLEMENTED_IN_MOCK', `The mock API has no handler for ${request.method} ${new URL(request.url).pathname}.`),
  ),
];
