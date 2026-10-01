import type { Category, DocumentSource, DocumentType, ExtractionResult, ServiceType } from '@/types';
import { addDays, parseISODate, toISODate } from '@/utils/date';
import { DEMO_CREDENTIALS } from '@/lib/config';
import type { DbDocument, DbProduct, DbServiceRecord, DbWarranty, MockDatabase } from './db';
import { runReminderJob } from './jobs';
import { templateByKey, templateToExtraction, templateToOcrText } from './templates';
import { daysAgo, hashPassword, hoursAgo } from './utils';

export const DEMO_USER_ID = 'user_demo';
const DEMO_USER_NAME = 'Asha Verma';

export const CATEGORIES: Category[] = [
  { id: 'cat_mobile', name: 'Mobile Phones', slug: 'mobile-phones' },
  { id: 'cat_computers', name: 'Computers & Accessories', slug: 'computers' },
  { id: 'cat_tv', name: 'TV & Entertainment', slug: 'tv-entertainment' },
  { id: 'cat_audio', name: 'Audio & Wearables', slug: 'audio' },
  { id: 'cat_appliances', name: 'Home Appliances', slug: 'home-appliances' },
  { id: 'cat_kitchen', name: 'Kitchen Appliances', slug: 'kitchen' },
  { id: 'cat_furniture', name: 'Furniture', slug: 'furniture' },
  { id: 'cat_vehicles', name: 'Vehicles', slug: 'vehicles' },
  { id: 'cat_other', name: 'Other', slug: 'other' },
];

interface SeedProduct {
  key: string;
  categoryId: string;
  name: string;
  brand: string;
  model: string | null;
  serialNumber: string | null;
  purchaseDaysAgo: number;
  price: number;
  seller: string;
  invoiceNumber: string;
  warrantyMonths: number | null;
  invoiceFile: string;
  source: DocumentSource;
}

/**
 * Demo purchases (spec: Dell laptop, Samsung refrigerator, Sony TV, LG washing
 * machine, Voltas AC, Apple iPhone, boAt headphones, HP printer) plus a sofa
 * with no warranty info. Dates are relative to today so statuses stay stable:
 * 4 active, 2 expiring soon, 2 expired, 1 unknown.
 */
const SEED_PRODUCTS: SeedProduct[] = [
  {
    key: 'dell',
    categoryId: 'cat_computers',
    name: 'Dell Inspiron 15 3530 Laptop',
    brand: 'Dell',
    model: 'Inspiron 15 3530',
    serialNumber: 'DL3530-7XK2P91',
    purchaseDaysAgo: 340,
    price: 62990,
    seller: 'Metro Electronics, Bengaluru',
    invoiceNumber: 'ME/2025-26/004812',
    warrantyMonths: 12,
    invoiceFile: 'Dell_Inspiron_Invoice_ME-004812.pdf',
    source: 'UPLOAD',
  },
  {
    key: 'samsung',
    categoryId: 'cat_appliances',
    name: 'Samsung 653 L Side-by-Side Refrigerator',
    brand: 'Samsung',
    model: 'RS76CG8003S9HL',
    serialNumber: 'SMRF-0K4Z1123',
    purchaseDaysAgo: 400,
    price: 82990,
    seller: 'HomeStyle Appliances',
    invoiceNumber: 'HSA-INV-10293',
    warrantyMonths: 24,
    invoiceFile: 'Samsung_Refrigerator_Invoice.pdf',
    source: 'UPLOAD',
  },
  {
    key: 'sony',
    categoryId: 'cat_tv',
    name: 'Sony Bravia 55" 4K Google TV',
    brand: 'Sony',
    model: 'KD-55X74L',
    serialNumber: 'SNYTV-55X74L-88121',
    purchaseDaysAgo: 200,
    price: 64990,
    seller: 'Amazon.in',
    invoiceNumber: 'IN-BLR5-2026-7731',
    warrantyMonths: 24,
    invoiceFile: 'Sony_Bravia_Amazon_Invoice.pdf',
    source: 'UPLOAD',
  },
  {
    key: 'lg',
    categoryId: 'cat_appliances',
    name: 'LG 8 kg Front Load Washing Machine',
    brand: 'LG',
    model: 'FHP1208Z3M',
    serialNumber: 'LGWM-1208Z3M-4410',
    purchaseDaysAgo: 780,
    price: 36490,
    seller: "Vijay's Home Store",
    invoiceNumber: 'VHS/2024-25/1188',
    warrantyMonths: 24,
    invoiceFile: 'LG_Washing_Machine_Bill.pdf',
    source: 'UPLOAD',
  },
  {
    key: 'voltas',
    categoryId: 'cat_appliances',
    name: 'Voltas 1.5 Ton 5 Star Inverter Split AC',
    brand: 'Voltas',
    model: '185V Vectra CAR',
    serialNumber: 'VLT-185V-22019',
    purchaseDaysAgo: 410,
    price: 43990,
    seller: 'CoolAir Solutions',
    invoiceNumber: 'CAS-2025-0457',
    warrantyMonths: 60,
    invoiceFile: 'Voltas_AC_Invoice_CAS-0457.pdf',
    source: 'UPLOAD',
  },
  {
    key: 'iphone',
    categoryId: 'cat_mobile',
    name: 'Apple iPhone 15 (128 GB, Black)',
    brand: 'Apple',
    model: 'MTP03HN/A',
    serialNumber: 'F4GXK2LMN7Q1',
    purchaseDaysAgo: 120,
    price: 69900,
    seller: 'Amazon.in',
    invoiceNumber: 'IN-DEL2-2026-30418',
    warrantyMonths: 12,
    invoiceFile: 'iPhone15_Invoice_Amazon.pdf',
    source: 'GMAIL',
  },
  {
    key: 'boat',
    categoryId: 'cat_audio',
    name: 'boAt Rockerz 450 Bluetooth Headphones',
    brand: 'boAt',
    model: 'Rockerz 450',
    serialNumber: null,
    purchaseDaysAgo: 350,
    price: 1499,
    seller: 'Flipkart',
    invoiceNumber: 'FAWRX2211934',
    warrantyMonths: 12,
    invoiceFile: 'boAt_Rockerz_450_Invoice.pdf',
    source: 'GMAIL',
  },
  {
    key: 'hp',
    categoryId: 'cat_computers',
    name: 'HP DeskJet 2331 All-in-One Printer',
    brand: 'HP',
    model: 'DeskJet 2331',
    serialNumber: 'CN2B4PQ0XY',
    purchaseDaysAgo: 430,
    price: 3999,
    seller: 'Metro Electronics, Bengaluru',
    invoiceNumber: 'ME/2025-26/001377',
    warrantyMonths: 12,
    invoiceFile: 'HP_DeskJet_Invoice.pdf',
    source: 'UPLOAD',
  },
  {
    key: 'sofa',
    categoryId: 'cat_furniture',
    name: 'Woodcraft Oslo 3-Seater Fabric Sofa',
    brand: 'Woodcraft Living',
    model: 'Oslo 3S',
    serialNumber: null,
    purchaseDaysAgo: 260,
    price: 28500,
    seller: 'Urban Nest Furnishings',
    invoiceNumber: 'UNF-7781',
    warrantyMonths: null,
    invoiceFile: 'Woodcraft_Sofa_Receipt.pdf',
    source: 'UPLOAD',
  },
];

function ocrText(product: DbProduct, heading: string, customer: string, extra: string[] = []): string {
  return [
    heading,
    product.seller ?? '',
    'GSTIN: 29AAAAA0000A1Z5',
    `Invoice No: ${product.invoiceNumber ?? '—'}`,
    product.purchaseDate ? `Date: ${product.purchaseDate.split('-').reverse().join('/')}` : '',
    `Bill To: ${customer}`,
    '',
    `Item: ${product.name}`,
    product.model ? `Model: ${product.model}` : '',
    product.serialNumber ? `S/N: ${product.serialNumber}` : '',
    product.purchasePrice !== null ? `TOTAL: Rs. ${product.purchasePrice.toLocaleString('en-IN')}.00 (incl. GST)` : '',
    ...extra,
  ]
    .filter((line, index) => line !== '' || index === 6)
    .join('\n');
}

function confirmedExtraction(product: DbProduct, warrantyMonths: number | null, slug: string | null): ExtractionResult {
  const score = (value: unknown, high: number) => (value === null ? 0 : high);
  return {
    documentType: 'INVOICE',
    productName: product.name,
    brand: product.brand,
    model: product.model,
    serialNumber: product.serialNumber,
    purchaseDate: product.purchaseDate,
    purchasePrice: product.purchasePrice,
    currency: product.currency,
    seller: product.seller,
    invoiceNumber: product.invoiceNumber,
    warrantyMonths,
    suggestedCategorySlug: slug,
    confidence: {
      documentType: 0.98,
      productName: 0.96,
      brand: 0.98,
      model: score(product.model, 0.92),
      serialNumber: score(product.serialNumber, 0.9),
      purchaseDate: 0.97,
      purchasePrice: 0.99,
      currency: 0.99,
      seller: 0.95,
      invoiceNumber: 0.94,
      warrantyMonths: score(warrantyMonths, 0.89),
    },
  };
}

interface DocumentSeed {
  id: string;
  productId: string | null;
  documentType: DocumentType;
  fileName: string;
  fileSize: number;
  source: DocumentSource;
  createdAt: string;
  extraction: ExtractionResult | null;
  extractedText: string | null;
  status?: DbDocument['processingStatus'];
  templateKey?: string | null;
}

function makeDocument(seed: DocumentSeed): DbDocument {
  return {
    id: seed.id,
    userId: DEMO_USER_ID,
    productId: seed.productId,
    documentType: seed.documentType,
    fileName: seed.fileName,
    mimeType: 'application/pdf',
    fileSize: seed.fileSize,
    source: seed.source,
    processingStatus: seed.status ?? 'CONFIRMED',
    processingStage: null,
    processingStartedAt: null,
    attempts: 1,
    failFirstAttempt: false,
    templateKey: seed.templateKey ?? null,
    documentTypeHint: null,
    extraction: seed.extraction,
    extractedText: seed.extractedText,
    errorMessage: null,
    createdAt: seed.createdAt,
    updatedAt: seed.createdAt,
  };
}

export function createSeedDatabase(today = new Date()): MockDatabase {
  const data: MockDatabase = {
    version: 1,
    users: [
      {
        id: DEMO_USER_ID,
        name: DEMO_USER_NAME,
        email: DEMO_CREDENTIALS.email,
        passwordHash: hashPassword(DEMO_CREDENTIALS.password),
        createdAt: hoursAgo(24 * 45),
      },
    ],
    categories: CATEGORIES,
    products: [],
    warranties: [],
    documents: [],
    serviceRecords: [],
    notifications: [],
    gmailConnections: [],
    gmailMessages: [],
  };

  const productIds: Record<string, string> = {};

  SEED_PRODUCTS.forEach((seed, index) => {
    const productId = `prd_${seed.key}`;
    productIds[seed.key] = productId;
    const purchaseDate = daysAgo(seed.purchaseDaysAgo, today);
    const createdAt = hoursAgo(24 * (30 - index * 3) + index);
    const product: DbProduct = {
      id: productId,
      userId: DEMO_USER_ID,
      categoryId: seed.categoryId,
      name: seed.name,
      brand: seed.brand,
      model: seed.model,
      serialNumber: seed.serialNumber,
      purchaseDate,
      purchasePrice: seed.price,
      currency: 'INR',
      seller: seed.seller,
      invoiceNumber: seed.invoiceNumber,
      createdAt,
      updatedAt: createdAt,
    };
    const invoiceId = `doc_${seed.key}_invoice`;
    const warranty: DbWarranty = {
      id: `wty_${seed.key}`,
      productId,
      warrantyMonths: seed.warrantyMonths,
      startDate: purchaseDate,
      sourceDocumentId: invoiceId,
      createdAt,
      updatedAt: createdAt,
    };
    const slug = CATEGORIES.find((category) => category.id === seed.categoryId)?.slug ?? null;
    data.products.push(product);
    data.warranties.push(warranty);
    data.documents.push(
      makeDocument({
        id: invoiceId,
        productId,
        documentType: 'INVOICE',
        fileName: seed.invoiceFile,
        fileSize: 96_000 + index * 13_517,
        source: seed.source,
        createdAt,
        extraction: confirmedExtraction(product, seed.warrantyMonths, slug),
        extractedText: ocrText(product, 'TAX INVOICE', DEMO_USER_NAME, [
          seed.warrantyMonths ? `Warranty: ${seed.warrantyMonths} months from date of purchase` : '',
        ]),
      }),
    );
  });

  const product = (key: string) => data.products.find((item) => item.id === productIds[key]) as DbProduct;

  // Supporting documents.
  data.documents.push(
    makeDocument({
      id: 'doc_samsung_warranty',
      productId: productIds.samsung,
      documentType: 'WARRANTY_CARD',
      fileName: 'Samsung_Warranty_Card.pdf',
      fileSize: 58_240,
      source: 'UPLOAD',
      createdAt: hoursAgo(24 * 26),
      extraction: { ...confirmedExtraction(product('samsung'), 24, 'home-appliances'), documentType: 'WARRANTY_CARD' },
      extractedText: ocrText(product('samsung'), 'WARRANTY CERTIFICATE', DEMO_USER_NAME, [
        'Coverage: 2 years comprehensive, 20 years on digital inverter compressor',
      ]),
    }),
    makeDocument({
      id: 'doc_voltas_service',
      productId: productIds.voltas,
      documentType: 'SERVICE_RECEIPT',
      fileName: 'Voltas_AC_Service_Receipt.pdf',
      fileSize: 41_880,
      source: 'UPLOAD',
      createdAt: hoursAgo(24 * 20),
      extraction: null,
      extractedText: 'SERVICE RECEIPT\nCoolAir Solutions\nJob: Routine maintenance — filter cleaning, gas pressure check\nAmount: Rs. 1,200.00',
    }),
    makeDocument({
      id: 'doc_lg_repair',
      productId: productIds.lg,
      documentType: 'REPAIR_RECEIPT',
      fileName: 'LG_Drum_Repair_Receipt.pdf',
      fileSize: 39_115,
      source: 'UPLOAD',
      createdAt: hoursAgo(24 * 18),
      extraction: null,
      extractedText: 'REPAIR RECEIPT\nAuthorised service centre\nDrum bearing replaced\nAmount: Rs. 2,450.00',
    }),
  );

  // One bill already processed by AI and waiting for review — a ready-made demo of the review screen.
  const airFryer = templateByKey('philips-airfryer');
  const airFryerExtraction = templateToExtraction(airFryer, today);
  data.documents.push(
    makeDocument({
      id: 'doc_airfryer_review',
      productId: null,
      documentType: 'INVOICE',
      fileName: 'Philips_Air_Fryer_Invoice.pdf',
      fileSize: 73_402,
      source: 'UPLOAD',
      createdAt: hoursAgo(20),
      status: 'REVIEW_REQUIRED',
      templateKey: airFryer.key,
      extraction: airFryerExtraction,
      extractedText: templateToOcrText(airFryer, airFryerExtraction, DEMO_USER_NAME),
    }),
  );

  // Service history.
  const service = (
    id: string,
    key: string,
    serviceType: ServiceType,
    serviceDate: string,
    serviceCenter: string,
    cost: number | null,
    nextServiceDate: string | null,
    notes: string | null,
  ): DbServiceRecord => ({
    id,
    productId: productIds[key],
    serviceDate,
    serviceType,
    serviceCenter,
    cost,
    nextServiceDate,
    notes,
    createdAt: hoursAgo(24 * 20),
    updatedAt: hoursAgo(24 * 20),
  });
  const plusDays = (date: string, days: number) => toISODate(addDays(parseISODate(date), days));

  data.serviceRecords.push(
    service('svc_voltas_install', 'voltas', 'INSTALLATION', plusDays(product('voltas').purchaseDate as string, 1), 'CoolAir Solutions', 1500, null, 'Wall-mounted in the living room, 3 m copper piping.'),
    service('svc_voltas_routine', 'voltas', 'ROUTINE_MAINTENANCE', daysAgo(173, today), 'CoolAir Solutions', 1200, daysAgo(-7, today), 'Filter cleaning and gas pressure check.'),
    service('svc_lg_repair', 'lg', 'REPAIR', daysAgo(95, today), 'Authorised service centre, Indiranagar', 2450, null, 'Drum bearing replaced. 3-month warranty on the part.'),
    service('svc_sony_install', 'sony', 'INSTALLATION', plusDays(product('sony').purchaseDate as string, 2), 'Brand installation partner', 0, null, 'Wall-mount installation.'),
    service('svc_samsung_check', 'samsung', 'INSPECTION', daysAgo(30, today), 'HomeStyle Appliances', 0, daysAgo(-45, today), 'Annual check-up: compressor and door seals OK.'),
  );

  // Reminders the daily job would have produced, plus the "document ready" notice.
  runReminderJob(data, DEMO_USER_ID, today, hoursAgo(6));
  for (const notification of data.notifications) {
    if (notification.type === 'WARRANTY_EXPIRED') notification.read = true;
  }
  data.notifications.push({
    id: 'ntf_airfryer_ready',
    userId: DEMO_USER_ID,
    productId: null,
    documentId: 'doc_airfryer_review',
    type: 'DOCUMENT_PROCESSED',
    title: 'Document ready for review',
    message: 'We’ve finished reading Philips_Air_Fryer_Invoice.pdf. Review the details to save it to your locker.',
    scheduledAt: hoursAgo(20),
    read: false,
    createdAt: hoursAgo(20),
    dedupeKey: 'DOCUMENT_PROCESSED:doc_airfryer_review:1',
  });

  return data;
}
