import type { DocumentType, ExtractionField, ExtractionResult } from '@/types';
import { addDays, toISODate } from '@/utils/date';

/**
 * Mock "OCR + AI" results. The real backend extracts these from the file; the
 * mock picks a template by file name (or by the Gmail email) so demos are
 * realistic. All sellers, invoice numbers and serials are fictional.
 */
export interface ExtractionTemplate {
  key: string;
  /** Matched against uploaded file names. */
  keywords?: RegExp;
  documentType: DocumentType;
  categorySlug: string | null;
  productName: string | null;
  brand: string | null;
  model: string | null;
  serialNumber: string | null;
  purchaseDaysAgo: number | null;
  price: number | null;
  seller: string | null;
  invoiceNumber: string | null;
  warrantyMonths: number | null;
  warrantyText: string | null;
  confidence: Partial<Record<ExtractionField, number>>;
}

const HIGH = {
  documentType: 0.97,
  productName: 0.96,
  brand: 0.98,
  model: 0.91,
  serialNumber: 0.9,
  purchaseDate: 0.97,
  purchasePrice: 0.99,
  currency: 0.99,
  seller: 0.95,
  invoiceNumber: 0.94,
  warrantyMonths: 0.88,
} satisfies Partial<Record<ExtractionField, number>>;

export const TEMPLATES: ExtractionTemplate[] = [
  {
    key: 'dell-laptop',
    keywords: /dell|inspiron|laptop|notebook/i,
    documentType: 'INVOICE',
    categorySlug: 'computers',
    productName: 'Dell Inspiron 14 5440 Laptop',
    brand: 'Dell',
    model: 'Inspiron 14 5440',
    serialNumber: 'DL5440-3HQ8Z21',
    purchaseDaysAgo: 2,
    price: 71990,
    seller: 'Metro Electronics, Bengaluru',
    invoiceNumber: 'ME/2026-27/006631',
    warrantyMonths: 12,
    warrantyText: '1 Year Onsite Manufacturer Warranty',
    confidence: HIGH,
  },
  {
    key: 'iphone',
    keywords: /iphone|apple/i,
    documentType: 'INVOICE',
    categorySlug: 'mobile-phones',
    productName: 'Apple iPhone 16 (128 GB, Teal)',
    brand: 'Apple',
    model: 'MYEF3HN/A',
    serialNumber: 'G7TQ2HX9LM4R',
    purchaseDaysAgo: 3,
    price: 79900,
    seller: 'Amazon.in',
    invoiceNumber: 'IN-DEL4-2026-11873',
    warrantyMonths: 12,
    warrantyText: 'One (1) year limited warranty',
    confidence: HIGH,
  },
  {
    key: 'samsung-fridge',
    keywords: /samsung|fridge|refrigerator/i,
    documentType: 'INVOICE',
    categorySlug: 'home-appliances',
    productName: 'Samsung 236 L 3 Star Double Door Refrigerator',
    brand: 'Samsung',
    model: 'RT28C3053S8/HL',
    serialNumber: 'SMRT-236C-55210',
    purchaseDaysAgo: 5,
    price: 27490,
    seller: 'HomeStyle Appliances',
    invoiceNumber: 'HSA-INV-11852',
    warrantyMonths: 12,
    warrantyText: '1 year comprehensive, 20 years on compressor',
    confidence: { ...HIGH, warrantyMonths: 0.71 },
  },
  {
    key: 'sony-tv',
    keywords: /sony|bravia|\btv\b|television/i,
    documentType: 'INVOICE',
    categorySlug: 'tv-entertainment',
    productName: 'Sony Bravia 65" 4K Ultra HD Google TV',
    brand: 'Sony',
    model: 'K-65S30',
    serialNumber: 'SNY65S30-10477',
    purchaseDaysAgo: 4,
    price: 89990,
    seller: 'Amazon.in',
    invoiceNumber: 'IN-BLR7-2026-55120',
    warrantyMonths: 24,
    warrantyText: '2 years (1 year standard + 1 year extended)',
    confidence: HIGH,
  },
  {
    key: 'lg-washer',
    keywords: /\blg\b|washing|washer/i,
    documentType: 'INVOICE',
    categorySlug: 'home-appliances',
    productName: 'LG 7 kg 5 Star Top Load Washing Machine',
    brand: 'LG',
    model: 'T70SPSF2Z',
    serialNumber: null,
    purchaseDaysAgo: 6,
    price: 17990,
    seller: "Vijay's Home Store",
    invoiceNumber: 'VHS/26-27/2209',
    warrantyMonths: 24,
    warrantyText: '2 years on product, 10 years on motor',
    confidence: { ...HIGH, serialNumber: 0 },
  },
  {
    key: 'voltas-ac',
    keywords: /voltas|\bac\b|air ?condition/i,
    documentType: 'INVOICE',
    categorySlug: 'home-appliances',
    productName: 'Voltas 1 Ton 3 Star Inverter Split AC',
    brand: 'Voltas',
    model: '123V Vectra Elite',
    serialNumber: 'VLT-123V-81230',
    purchaseDaysAgo: 3,
    price: 32990,
    seller: 'CoolAir Solutions',
    invoiceNumber: 'CAS-2026-0918',
    warrantyMonths: 12,
    warrantyText: '1 year comprehensive, 5 years on compressor',
    confidence: HIGH,
  },
  {
    key: 'boat-earbuds',
    keywords: /boat|headphone|earbud|airdopes|rockerz/i,
    documentType: 'INVOICE',
    categorySlug: 'audio',
    productName: 'boAt Airdopes 141 Wireless Earbuds',
    brand: 'boAt',
    model: 'Airdopes 141',
    serialNumber: null,
    purchaseDaysAgo: 1,
    price: 1299,
    seller: 'Flipkart',
    invoiceNumber: 'FAWRX2609144',
    warrantyMonths: 12,
    warrantyText: '1 year manufacturer warranty',
    confidence: { ...HIGH, serialNumber: 0, model: 0.83 },
  },
  {
    key: 'hp-printer',
    keywords: /\bhp\b|printer|deskjet|inkjet/i,
    documentType: 'INVOICE',
    categorySlug: 'computers',
    productName: 'HP Smart Tank 580 All-in-One Printer',
    brand: 'HP',
    model: 'Smart Tank 580',
    serialNumber: 'CN4C81Q0MZ',
    purchaseDaysAgo: 2,
    price: 12499,
    seller: 'Metro Electronics, Bengaluru',
    invoiceNumber: 'ME/2026-27/006702',
    warrantyMonths: 12,
    warrantyText: '1 year limited warranty',
    confidence: HIGH,
  },
  {
    key: 'philips-airfryer',
    documentType: 'INVOICE',
    categorySlug: 'kitchen',
    productName: 'Philips Air Fryer HD9252/90',
    brand: 'Philips',
    model: 'HD9252/90',
    serialNumber: null,
    purchaseDaysAgo: 1,
    price: 8999,
    seller: 'Metro Electronics, Bengaluru',
    invoiceNumber: 'ME/2026-27/006745',
    warrantyMonths: 24,
    warrantyText: '2 years',
    confidence: { ...HIGH, serialNumber: 0, warrantyMonths: 0.74, model: 0.8 },
  },
  {
    key: 'sony-wh1000xm5',
    documentType: 'INVOICE',
    categorySlug: 'audio',
    productName: 'Sony WH-1000XM5 Wireless Noise Cancelling Headphones',
    brand: 'Sony',
    model: 'WH-1000XM5',
    serialNumber: '5021884',
    purchaseDaysAgo: 2,
    price: 29990,
    seller: 'Amazon.in',
    invoiceNumber: 'IN-BOM3-2026-40811',
    warrantyMonths: 12,
    warrantyText: '1 year manufacturer warranty',
    confidence: HIGH,
  },
  {
    key: 'bajaj-iron',
    documentType: 'INVOICE',
    categorySlug: 'home-appliances',
    productName: 'Bajaj Majesty DX-11 1000W Dry Iron',
    brand: 'Bajaj',
    model: 'Majesty DX-11',
    serialNumber: null,
    purchaseDaysAgo: 5,
    price: 749,
    seller: 'Flipkart',
    invoiceNumber: 'FAWRX2608871',
    warrantyMonths: 24,
    warrantyText: '2 years',
    confidence: { ...HIGH, serialNumber: 0 },
  },
  {
    key: 'galaxy-watch',
    documentType: 'INVOICE',
    categorySlug: 'audio',
    productName: 'Samsung Galaxy Watch6 Classic (47 mm, LTE)',
    brand: 'Samsung',
    model: 'SM-R965F',
    serialNumber: 'R9WT60K4ZQA',
    purchaseDaysAgo: 9,
    price: 36999,
    seller: 'ElectroMart Online',
    invoiceNumber: 'EM-88213',
    warrantyMonths: 12,
    warrantyText: '1 year',
    confidence: HIGH,
  },
  {
    key: 'ac-service',
    documentType: 'SERVICE_RECEIPT',
    categorySlug: 'home-appliances',
    productName: 'Voltas 1.5 Ton 5 Star Inverter Split AC',
    brand: 'Voltas',
    model: '185V Vectra CAR',
    serialNumber: null,
    purchaseDaysAgo: 16,
    price: 1200,
    seller: 'CoolAir Solutions',
    invoiceNumber: 'CAS-SRV-2291',
    warrantyMonths: null,
    warrantyText: null,
    confidence: { ...HIGH, documentType: 0.88, serialNumber: 0, warrantyMonths: 0, purchasePrice: 0.93 },
  },
  {
    key: 'sony-extended-warranty',
    documentType: 'WARRANTY_CARD',
    categorySlug: 'tv-entertainment',
    productName: 'Sony Bravia 55" 4K Google TV',
    brand: 'Sony',
    model: 'KD-55X74L',
    serialNumber: 'SNYTV-55X74L-88121',
    purchaseDaysAgo: null,
    price: null,
    seller: 'Sony India',
    invoiceNumber: null,
    warrantyMonths: 36,
    warrantyText: 'Extended warranty — total coverage 3 years from purchase',
    confidence: { ...HIGH, documentType: 0.91, purchaseDate: 0, purchasePrice: 0, invoiceNumber: 0, warrantyMonths: 0.86 },
  },
  {
    key: 'food-order',
    documentType: 'OTHER',
    categorySlug: null,
    productName: null,
    brand: null,
    model: null,
    serialNumber: null,
    purchaseDaysAgo: 30,
    price: 642,
    seller: 'Meghana Foods',
    invoiceNumber: null,
    warrantyMonths: null,
    warrantyText: null,
    confidence: { documentType: 0.41, purchaseDate: 0.62, purchasePrice: 0.58, seller: 0.66, currency: 0.9 },
  },
  {
    // Fallback for file names we can't match: a partially readable bill.
    key: 'generic',
    documentType: 'INVOICE',
    categorySlug: 'home-appliances',
    productName: 'Havells Instanio Prime 15 L Water Heater',
    brand: 'Havells',
    model: null,
    serialNumber: null,
    purchaseDaysAgo: 3,
    price: 8799,
    seller: 'Metro Electronics, Bengaluru',
    invoiceNumber: 'ME/2026-27/006759',
    warrantyMonths: null,
    warrantyText: null,
    confidence: {
      documentType: 0.9,
      productName: 0.78,
      brand: 0.93,
      purchaseDate: 0.84,
      purchasePrice: 0.66,
      currency: 0.95,
      seller: 0.88,
      invoiceNumber: 0.52,
    },
  },
];

export function templateByKey(key: string | null | undefined): ExtractionTemplate {
  return TEMPLATES.find((template) => template.key === key) ?? TEMPLATES[TEMPLATES.length - 1];
}

/** Picks the template whose keywords match an uploaded file name. */
export function templateForFileName(fileName: string): ExtractionTemplate {
  return TEMPLATES.find((template) => template.keywords?.test(fileName)) ?? templateByKey('generic');
}

export function templateToExtraction(template: ExtractionTemplate, today = new Date()): ExtractionResult {
  const purchaseDate =
    template.purchaseDaysAgo === null ? null : toISODate(addDays(today, -template.purchaseDaysAgo));
  return {
    documentType: template.documentType,
    productName: template.productName,
    brand: template.brand,
    model: template.model,
    serialNumber: template.serialNumber,
    purchaseDate,
    purchasePrice: template.price,
    currency: template.price === null ? null : 'INR',
    seller: template.seller,
    invoiceNumber: template.invoiceNumber,
    warrantyMonths: template.warrantyMonths,
    suggestedCategorySlug: template.categorySlug,
    confidence: template.confidence,
  };
}

/** Plausible OCR output for the "View text read from the document" panel. */
export function templateToOcrText(template: ExtractionTemplate, extraction: ExtractionResult, customerName: string): string {
  const heading =
    template.documentType === 'SERVICE_RECEIPT'
      ? 'SERVICE RECEIPT'
      : template.documentType === 'WARRANTY_CARD'
        ? 'WARRANTY CERTIFICATE'
        : 'TAX INVOICE';
  const lines = [
    heading,
    template.seller ?? '',
    'GSTIN: 29AAAAA0000A1Z5',
    template.invoiceNumber ? `Invoice No: ${template.invoiceNumber}` : '',
    extraction.purchaseDate ? `Date: ${extraction.purchaseDate.split('-').reverse().join('/')}` : '',
    `Bill To: ${customerName}`,
    '',
    template.productName ? `Item: ${template.productName}` : 'Item: (unreadable)',
    template.model ? `Model: ${template.model}` : '',
    template.serialNumber ? `S/N: ${template.serialNumber}` : '',
    template.price !== null ? `Qty 1   Amount Rs. ${template.price.toLocaleString('en-IN')}.00` : '',
    template.warrantyText ? `Warranty: ${template.warrantyText}` : '',
    template.price !== null ? `TOTAL: Rs. ${template.price.toLocaleString('en-IN')}.00 (incl. GST)` : '',
  ];
  return lines.filter((line, index) => line !== '' || index === 6).join('\n');
}
