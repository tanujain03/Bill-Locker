/**
 * Warranty claim kit: the backend gathers the facts (ClaimKitService); this file holds their
 * shape, the call, and the claim email written from them. Nothing is sent by the app: the
 * user copies the email or opens it in their own email app.
 */
import { api } from './api';
import { formatDate } from './document-form';
import { formatAmount } from './documents';
import type { WarrantyStatus } from './warranties';

export type ClaimKit = {
  documentId: string;
  position: number;
  /** The stored bill's file name, for "Download invoice". */
  fileName: string;
  productName: string | null;
  brand: string | null;
  modelNumber: string | null;
  serialNumber: string | null;
  unitPrice: number | null;
  sellerName: string | null;
  sellerContact: string | null;
  sellerAddress: string | null;
  invoiceNumber: string | null;
  purchaseDate: string | null;
  buyerName: string | null;
  warrantyProvider: string | null;
  warrantyStartDate: string | null;
  /** Printed, or start + months − 1 day. */
  warrantyEndDate: string | null;
  daysLeft: number | null;
  status: WarrantyStatus;
  registrationUrl: string | null;
  supportSearchUrl: string;
  serviceCentreUrl: string;
  /** e.g. ["Serial number", "Invoice number"]. */
  missing: string[];
};

export const getClaimKit = (documentId: string, position: number) =>
  api<ClaimKit>(`/documents/${documentId}/items/${position}/claim-kit`);

export const claimKitLink = (documentId: string, position: number) => `/warranties/claim/${documentId}/${position}`;

/** Quick choices for the problem box: one click fills a common description. */
export const COMMON_PROBLEMS = [
  'It does not turn on.',
  'It does not charge.',
  'The screen is damaged or shows lines.',
  'The battery drains very fast.',
  'It makes an unusual noise.',
  'It stopped working suddenly.',
];

/**
 * The claim email. Lines for facts the bill doesn't have are left out, so the email never
 * shows "Serial number: null"; an empty problem gets a clear placeholder to replace.
 */
export function claimEmail(
  kit: ClaimKit,
  input: { problem: string; since: string; name: string; email: string },
): { subject: string; body: string } {
  const product = kit.productName ?? 'product';
  const subject = ['Warranty claim', `${product}${kit.modelNumber ? ` (${kit.modelNumber})` : ''}`, kit.serialNumber && `Serial ${kit.serialNumber}`]
    .filter(Boolean)
    .join(' – ');

  const facts = [
    ['Product', product],
    ['Brand', kit.brand],
    ['Model number', kit.modelNumber],
    ['Serial number', kit.serialNumber],
    ['Purchased from', kit.sellerName],
    ['Purchase date', kit.purchaseDate && formatDate(kit.purchaseDate)],
    ['Invoice number', kit.invoiceNumber],
    ['Price', kit.unitPrice !== null ? formatAmount(kit.unitPrice) : null],
    ['Warranty valid until', kit.warrantyEndDate && formatDate(kit.warrantyEndDate)],
  ]
    .filter(([, value]) => value)
    .map(([label, value]) => `${label}: ${value}`);

  const problem = input.problem.trim() || '[Describe the problem here]';
  const body = [
    `Dear ${kit.brand ?? kit.warrantyProvider ?? 'Customer Support'} team,`,
    '',
    `I would like to request service under warranty for my ${product}.`,
    '',
    ...facts,
    '',
    `Problem: ${problem}`,
    ...(input.since ? [`The problem started on ${formatDate(input.since)}.`] : []),
    '',
    'I have attached a copy of the purchase invoice. Please let me know the next steps, or the nearest authorised service centre.',
    '',
    'Regards,',
    input.name,
    input.email,
  ].join('\n');

  return { subject, body };
}

/** Opens the user's email app with the draft (no recipient: the user adds the brand's address). */
export const mailtoLink = (subject: string, body: string) =>
  `mailto:?subject=${encodeURIComponent(subject)}&body=${encodeURIComponent(body)}`;
