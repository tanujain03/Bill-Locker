import { Barcode, ExternalLink, QrCode, ReceiptText } from 'lucide-react';
import { Card, CardBody, CardHeader } from '@/components/ui/Card';
import type { ScannedCode } from '@/types';
import { formatCurrency, formatDate } from '@/utils/format';
import { safeWebLink } from './scanned-codes';

interface ScannedCodesProps {
  codes: ScannedCode[];
}

/**
 * The barcodes and QR codes found on a document. They are read exactly, so values
 * taken from them are marked "High confidence"; web links (e.g. warranty registration)
 * open in a new tab, and only http(s) addresses are ever made clickable.
 */
export function ScannedCodes({ codes }: ScannedCodesProps) {
  return (
    <Card>
      <CardHeader
        icon={<QrCode className="size-4 text-brand-600" aria-hidden />}
        title="Codes on this document"
        description="Read exactly from barcodes and QR codes — no guessing from the text."
      />
      <CardBody>
        <ul className="divide-y divide-slate-100">
          {codes.map((code) => (
            <li key={`${code.format}:${code.value}`} className="flex items-start gap-3 py-3">
              <span className="mt-0.5 flex size-8 shrink-0 items-center justify-center rounded-lg bg-slate-100 text-slate-600">
                {code.kind === 'BARCODE' ? <Barcode className="size-4" aria-hidden /> : code.kind === 'GST_E_INVOICE' ? <ReceiptText className="size-4" aria-hidden /> : <QrCode className="size-4" aria-hidden />}
              </span>
              <div className="min-w-0 flex-1 text-sm">
                <CodeDetails code={code} />
              </div>
            </li>
          ))}
        </ul>
      </CardBody>
    </Card>
  );
}

function CodeDetails({ code }: { code: ScannedCode }) {
  if (code.kind === 'GST_E_INVOICE' && code.invoice) {
    const { invoiceNumber, invoiceDate, total } = code.invoice;
    return (
      <>
        <p className="font-medium text-slate-900">GST e-invoice QR code</p>
        <p className="text-slate-600">
          {[invoiceNumber, invoiceDate ? formatDate(invoiceDate) : null, total !== null ? formatCurrency(total, 'INR') : null]
            .filter(Boolean)
            .join(' · ')}
        </p>
      </>
    );
  }
  const link = code.kind === 'LINK' ? safeWebLink(code.value) : null;
  if (link) {
    return (
      <>
        <p className="font-medium text-slate-900">Link in a QR code</p>
        <a
          href={link.href}
          target="_blank"
          rel="noopener noreferrer"
          className="inline-flex max-w-full items-center gap-1 font-medium text-brand-700 hover:text-brand-800"
        >
          <span className="truncate">{link.host}</span>
          <ExternalLink className="size-3.5 shrink-0" aria-hidden />
        </a>
        <p className="truncate text-xs text-slate-400">{link.href}</p>
      </>
    );
  }
  return (
    <>
      <p className="font-medium text-slate-900">{code.kind === 'BARCODE' ? `Barcode (${code.format})` : 'QR code'}</p>
      <p className="font-mono break-all text-slate-600">{code.value.length > 200 ? `${code.value.slice(0, 200)}…` : code.value}</p>
    </>
  );
}
