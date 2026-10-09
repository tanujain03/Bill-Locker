/**
 * The review form keeps every value as text (that's what inputs hold); these
 * helpers convert between that and the backend's DocumentDetails, and build the
 * text that the "Copy" buttons put on the clipboard.
 */
import {
  DOCUMENT_TYPE_LABELS,
  FIELD_LABELS,
  ITEM_LABELS,
  type DocumentDetails,
  type DocumentItem,
  type BillCategory,
  type DocumentType,
  type RegistrationSource,
  BILL_CATEGORY_LABELS,
} from './documents';

export type ItemValues = Record<keyof DocumentItem, string>;
export type FormValues = Record<keyof typeof FIELD_LABELS, string> & { items: ItemValues[] };

/** The product fields shown with a label (and copied by "Copy"). */
const LABELLED_ITEM_FIELDS = Object.keys(ITEM_LABELS) as (keyof typeof ITEM_LABELS)[];
/** …plus where the registration link came from, kept with the form but not shown as a box. */
const ITEM_FIELDS: (keyof DocumentItem)[] = [...LABELLED_ITEM_FIELDS, 'registrationSource'];
const FIELDS = Object.keys(FIELD_LABELS) as (keyof typeof FIELD_LABELS)[];

export const emptyItem = (): ItemValues =>
  Object.fromEntries(ITEM_FIELDS.map((field) => [field, ''])) as ItemValues;

/** Backend → form: null becomes "", numbers become text. */
export function toForm(details: DocumentDetails): FormValues {
  const values = Object.fromEntries(FIELDS.map((field) => [field, String(details[field] ?? '')]));
  const items = details.items.map(
    (item) => Object.fromEntries(ITEM_FIELDS.map((field) => [field, String(item[field] ?? '')])) as ItemValues,
  );
  return { ...(values as Record<keyof typeof FIELD_LABELS, string>), items };
}

/** Form → backend: "" becomes null, number fields become numbers. */
export function fromForm(values: FormValues): DocumentDetails {
  const text = (value: string) => value.trim() || null;
  const number = (value: string) => (value.trim() === '' ? null : Number(value));
  return {
    documentType: (values.documentType || null) as DocumentType | null,
    // Only a bill or receipt keeps its category (the backend drops it for other types too).
    category: values.documentType === 'RECEIPT' ? ((values.category || null) as BillCategory | null) : null,
    documentNumber: text(values.documentNumber),
    sellerName: text(values.sellerName),
    sellerAddress: text(values.sellerAddress),
    sellerContact: text(values.sellerContact),
    buyerName: text(values.buyerName),
    buyerAddress: text(values.buyerAddress),
    buyerEmail: text(values.buyerEmail),
    purchaseDate: text(values.purchaseDate),
    taxAmount: number(values.taxAmount),
    totalAmount: number(values.totalAmount),
    items: values.items.map((item) => ({
      productName: text(item.productName),
      modelNumber: text(item.modelNumber),
      serialNumber: text(item.serialNumber),
      unitPrice: number(item.unitPrice),
      warrantyPeriodMonths: number(item.warrantyPeriodMonths),
      warrantyStartDate: text(item.warrantyStartDate),
      warrantyEndDate: text(item.warrantyEndDate),
      warrantyProvider: text(item.warrantyProvider),
      brand: text(item.brand),
      registrationUrl: text(item.registrationUrl),
      registrationSource: item.registrationUrl.trim() ? ((item.registrationSource || 'USER') as RegistrationSource) : null,
    })),
  };
}

/**
 * The warranty end the backend fills in on save when it's left empty:
 * start + months − 1 day (10 Jan 2026 + 12 → 9 Jan 2027). Like Java's plusMonths,
 * 31 Jan + 1 month is 28/29 Feb, not 3 Mar. null when start or months are missing.
 */
export function warrantyEndFor(start: string, months: string): string | null {
  const [y, m, d] = start.split('-').map(Number);
  const count = Number(months);
  if (!y || !m || !d || !Number.isInteger(count) || count <= 0) return null;
  const lastDay = new Date(Date.UTC(y, m - 1 + count + 1, 0)).getUTCDate(); // day 0 = last day of the month before
  const end = new Date(Date.UTC(y, m - 1 + count, Math.min(d, lastDay)));
  end.setUTCDate(end.getUTCDate() - 1);
  return end.toISOString().slice(0, 10);
}

/** "2027-01-09" → "9 Jan 2027" for hints. */
export const formatDate = (iso: string) =>
  new Date(`${iso}T00:00:00Z`).toLocaleDateString('en-IN', { day: 'numeric', month: 'short', year: 'numeric', timeZone: 'UTC' });

/** One product as "Label: value" lines; empty fields are left out. */
export function itemToText(item: ItemValues, index: number): string {
  const lines = LABELLED_ITEM_FIELDS.filter((field) => item[field].trim()).map(
    (field) => `${ITEM_LABELS[field]}: ${item[field].trim()}`,
  );
  return [`Product ${index + 1}`, ...lines].join('\n');
}

/** The whole document as text: bill details, then every product. */
export function detailsToText(values: FormValues): string {
  const lines = FIELDS.filter((field) => values[field].trim()).map((field) => {
    const value =
      field === 'documentType'
        ? DOCUMENT_TYPE_LABELS[values.documentType as DocumentType]
        : field === 'category'
          ? BILL_CATEGORY_LABELS[values.category as BillCategory]
          : values[field].trim();
    return `${FIELD_LABELS[field]}: ${value}`;
  });
  const items = values.items.map((item, i) => itemToText(item, i));
  return [lines.join('\n'), ...items].filter(Boolean).join('\n\n');
}
