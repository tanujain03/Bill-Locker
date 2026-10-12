import { BadgeCheck, ExternalLink, LoaderCircle, PencilLine } from 'lucide-react';
import type { RegistrationSource } from '../../lib/documents';
import { Button } from '../Button';
import { CopyButton } from './CopyButton';

/**
 * Task 5, inside a product's Warranty section.
 *
 * - The bill has a registration link (printed, or in its QR code): it's shown as a link
 *   that opens the registration page in a new tab.
 * - It has neither: we show the brand the AI detected from the product name and ask
 *   "Is this right?". Only after the user confirms do we find that brand's official
 *   registration page and open it in a new tab. "No, change it" goes to the Brand field.
 *
 * Bill Locker only opens the brand's page; the user registers there themselves.
 */
export function RegistrationLink(props: {
  url: string;
  source: RegistrationSource | '';
  brand: string;
  /** The search for the confirmed brand's page is running. */
  finding: boolean;
  /** False for a product added since the last save (the backend doesn't know it yet). */
  canSearch: boolean;
  onConfirm: () => void;
  onChangeBrand: () => void;
}) {
  const url = props.url.trim();
  const brand = props.brand.trim();

  return (
    <div className="mt-4 rounded-lg border border-slate-200 bg-white p-3">
      <p className="text-sm font-medium text-slate-700">Warranty registration</p>

      {/^https?:\/\/\S+$/i.test(url) ? (
        <>
          <div className="mt-1.5 flex items-start gap-2">
            {/* A new tab, without giving that page a way back into ours (noopener). */}
            <a
              href={url}
              target="_blank"
              rel="noopener noreferrer"
              className="min-w-0 flex-1 text-sm font-medium break-all text-brand-700 underline decoration-brand-300 underline-offset-2 hover:text-brand-800"
            >
              {url}
              <ExternalLink className="ml-1 inline size-3.5 align-[-2px]" aria-hidden />
            </a>
            <CopyButton text={url} label="Copy registration link" />
          </div>
          <p className="mt-1 text-xs text-slate-600">{SOURCE_NOTE[props.source || 'DOCUMENT']}</p>
        </>
      ) : props.finding ? (
        <p role="status" className="mt-1.5 flex items-center gap-2 text-sm text-slate-600">
          <LoaderCircle className="size-4 animate-spin text-brand-600" aria-hidden />
          Finding {brand}’s official registration page…
        </p>
      ) : brand ? (
        // No link or QR code on the bill: ask before going anywhere.
        <div className="mt-1.5 space-y-2.5">
          <p className="text-sm text-slate-600">
            This bill has no registration link or QR code. From the product name, it looks like a{' '}
            <span className="font-semibold text-slate-900">{brand}</span> product. Is that right?
          </p>
          <div className="flex flex-wrap gap-2">
            <Button size="sm" icon={BadgeCheck} onClick={props.onConfirm} disabled={!props.canSearch}>
              Yes, open {brand}’s registration page
            </Button>
            <Button size="sm" variant="ghost" icon={PencilLine} onClick={props.onChangeBrand}>
              No, change the brand
            </Button>
          </div>
          {!props.canSearch && <p className="text-xs text-slate-600">Save the bill first, then confirm.</p>}
        </div>
      ) : (
        <p className="mt-1.5 text-sm text-slate-600">
          This bill has no registration link or QR code. Enter the product’s <strong>Brand</strong> above to find its
          official registration page.
        </p>
      )}
    </div>
  );
}

/** Where the link came from, so the user knows how far to trust it. */
const SOURCE_NOTE: Record<RegistrationSource, string> = {
  DOCUMENT: 'Printed on the bill. Opens in a new tab.',
  QR_CODE: 'From the QR code on the bill. Opens in a new tab.',
  WEB_SEARCH: 'The brand’s registration page you confirmed. Opens in a new tab.',
  SEARCH: 'No official page was found, so this opens a Google search.',
  USER: 'Opens in a new tab.',
};
