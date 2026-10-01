import { z } from 'zod';
import type { ExtractionResult, Product, ProductInput } from '@/types';
import { isValidISODate, todayISO } from '@/utils/date';

const optionalText = (max: number) => z.string().trim().max(max, `Keep it under ${max} characters`);

/** All fields are strings in the form; `toProductInput` converts them for the API. */
export const productFormSchema = z.object({
  name: z.string().trim().min(1, 'Product name is required').max(120, 'Keep it under 120 characters'),
  categoryId: z.string(),
  brand: optionalText(80),
  model: optionalText(80),
  serialNumber: optionalText(80),
  purchaseDate: z
    .string()
    .refine((value) => value === '' || isValidISODate(value), 'Enter a valid date')
    .refine((value) => value === '' || value <= todayISO(), 'Purchase date cannot be in the future'),
  purchasePrice: z
    .string()
    .trim()
    .refine((value) => value === '' || (/^\d+(\.\d{1,2})?$/.test(value) && Number(value) <= 100_000_000), {
      message: 'Enter a valid amount (up to 2 decimals)',
    }),
  currency: z.string().regex(/^[A-Z]{3}$/, 'Choose a currency'),
  seller: optionalText(120),
  invoiceNumber: optionalText(80),
  warrantyMonths: z
    .string()
    .trim()
    .refine((value) => value === '' || (/^\d{1,3}$/.test(value) && Number(value) <= 240), {
      message: 'Enter a whole number of months (0–240)',
    }),
});

export type ProductFormValues = z.infer<typeof productFormSchema>;

export const PRODUCT_FORM_FIELDS = Object.keys(productFormSchema.shape) as (keyof ProductFormValues)[];

const emptyToNull = (value: string): string | null => (value.trim() === '' ? null : value.trim());

export function toProductInput(values: ProductFormValues): ProductInput {
  return {
    name: values.name.trim(),
    categoryId: emptyToNull(values.categoryId),
    brand: emptyToNull(values.brand),
    model: emptyToNull(values.model),
    serialNumber: emptyToNull(values.serialNumber),
    purchaseDate: emptyToNull(values.purchaseDate),
    purchasePrice: values.purchasePrice.trim() === '' ? null : Number(values.purchasePrice),
    currency: values.currency,
    seller: emptyToNull(values.seller),
    invoiceNumber: emptyToNull(values.invoiceNumber),
    warrantyMonths: values.warrantyMonths.trim() === '' ? null : Number(values.warrantyMonths),
  };
}

const str = (value: string | number | null | undefined): string =>
  value === null || value === undefined ? '' : String(value);

export function productToFormValues(product?: Partial<Product> | null): ProductFormValues {
  return {
    name: str(product?.name),
    categoryId: str(product?.categoryId),
    brand: str(product?.brand),
    model: str(product?.model),
    serialNumber: str(product?.serialNumber),
    purchaseDate: str(product?.purchaseDate),
    purchasePrice: str(product?.purchasePrice),
    currency: product?.currency || 'INR',
    seller: str(product?.seller),
    invoiceNumber: str(product?.invoiceNumber),
    warrantyMonths: str(product?.warranty?.warrantyMonths),
  };
}

export function extractionToFormValues(extraction: ExtractionResult | null, categoryId = ''): ProductFormValues {
  return {
    name: str(extraction?.productName),
    categoryId,
    brand: str(extraction?.brand),
    model: str(extraction?.model),
    serialNumber: str(extraction?.serialNumber),
    purchaseDate: isValidISODate(extraction?.purchaseDate) ? (extraction?.purchaseDate as string) : '',
    purchasePrice: str(extraction?.purchasePrice),
    currency: extraction?.currency && /^[A-Z]{3}$/.test(extraction.currency) ? extraction.currency : 'INR',
    seller: str(extraction?.seller),
    invoiceNumber: str(extraction?.invoiceNumber),
    warrantyMonths: str(extraction?.warrantyMonths),
  };
}

/**
 * For "add to existing product": keep what is already saved on the product and
 * only fill its empty fields from the document the user is reviewing.
 */
export function mergeProductWithExtraction(product: Product, extracted: ProductFormValues): ProductFormValues {
  const saved = productToFormValues(product);
  const merged = { ...saved };
  for (const key of PRODUCT_FORM_FIELDS) {
    if (merged[key].trim() === '' && extracted[key].trim() !== '') merged[key] = extracted[key];
  }
  return merged;
}
