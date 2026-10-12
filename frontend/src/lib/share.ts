/**
 * Sharing a saved bill on WhatsApp. Two ways, picked by what the browser can do:
 *
 * 1. The file itself (phones, and Chrome/Edge on Windows): the system share sheet
 *    (navigator.share) opens; the user picks WhatsApp and a contact, and the PDF/photo
 *    goes as an attachment with a short message.
 * 2. Otherwise (e.g. Firefox): WhatsApp's own "click to chat" link (wa.me) opens with the
 *    message, and the file is downloaded so the user can attach it there with 📎.
 *    A web page can't attach a file to wa.me by itself.
 *
 * Nothing goes through our backend: the file is already in the browser (the preview).
 */
import { formatDate } from './document-form';
import { formatAmount, type DocumentDetail } from './documents';

/** "Bill from Croma · 12 Mar 2026 · Total ₹54,990.00", products and warranty end dates. */
export function billShareText(d: DocumentDetail): string {
  const head = [
    d.sellerName ? `Bill from ${d.sellerName}` : `Bill: ${d.fileName}`,
    d.purchaseDate && formatDate(d.purchaseDate),
    d.totalAmount != null && `Total ${formatAmount(d.totalAmount)}`,
  ]
    .filter(Boolean)
    .join(' · ');
  const products = d.items
    .filter((item) => item.productName)
    .map((item) =>
      item.warrantyEndDate
        ? `• ${item.productName} (warranty until ${formatDate(item.warrantyEndDate)})`
        : `• ${item.productName}`,
    );
  return [head, ...products, '', 'Shared from Bill Locker'].join('\n');
}

export type ShareResult = 'shared' | 'cancelled' | 'link';

export async function shareOnWhatsApp(file: File, text: string): Promise<ShareResult> {
  if (navigator.canShare?.({ files: [file] })) {
    try {
      await navigator.share({ files: [file], text });
      return 'shared';
    } catch (error) {
      // Closing the share sheet is an AbortError: the user changed their mind, not a failure.
      if (error instanceof DOMException && error.name === 'AbortError') return 'cancelled';
      // Anything else (e.g. the OS refused the file type): fall through to the link.
    }
  }

  // Download the file first, so it's ready to attach when WhatsApp opens.
  const url = URL.createObjectURL(file);
  Object.assign(document.createElement('a'), { href: url, download: file.name }).click();
  URL.revokeObjectURL(url);
  window.open(`https://wa.me/?text=${encodeURIComponent(text)}`, '_blank', 'noopener,noreferrer');
  return 'link';
}
