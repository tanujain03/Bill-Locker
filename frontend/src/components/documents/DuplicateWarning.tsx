import { Copy, Trash2 } from 'lucide-react';
import { Link } from 'react-router';
import { Button } from '../Button';
import { formatAmount, type DuplicateMatch } from '../../lib/documents';

/**
 * "This looks like a copy": another document has the same date and the same bill number,
 * or the same shop and total (rules in the backend's DuplicateRules). The user decides:
 * delete this one, or keep both (then the warning never comes back for this bill).
 */
export function DuplicateWarning({ matches, busy, onDelete, onKeepBoth }: {
  matches: DuplicateMatch[];
  busy: boolean;
  onDelete: () => void;
  onKeepBoth: () => void;
}) {
  return (
    <div role="status" className="flex gap-2 rounded-lg bg-amber-50 p-3 text-sm text-amber-900 ring-1 ring-amber-200">
      <Copy className="mt-0.5 size-4 shrink-0" aria-hidden />
      <div className="min-w-0 flex-1">
        <p className="font-medium">This looks like a copy of a bill you already have</p>
        <ul className="mt-1 space-y-0.5">
          {matches.map((m) => (
            <li key={m.id}>
              <Link to={`/documents/${m.id}`} className="font-medium underline underline-offset-2 hover:text-amber-950">
                {m.sellerName ?? m.fileName}
              </Link>
              <span className="text-amber-800">
                {' · '}
                {[m.documentNumber, m.purchaseDate, m.totalAmount != null && formatAmount(m.totalAmount), m.fileName]
                  .filter(Boolean)
                  .join(' · ')}
              </span>
            </li>
          ))}
        </ul>
        <p className="mt-1 text-amber-800">Counting it twice would also double it on the Expenses page.</p>
        <div className="mt-2 flex flex-wrap gap-2">
          <Button size="sm" variant="secondary" icon={Trash2} onClick={onDelete} disabled={busy}>
            Delete this copy
          </Button>
          <Button size="sm" variant="ghost" onClick={onKeepBoth} disabled={busy}>
            Keep both, they’re different
          </Button>
        </div>
      </div>
    </div>
  );
}

/** The list's marker for a document that looks like a copy of another one. */
export function DuplicateBadge() {
  return (
    <span
      title="Looks like a copy of another bill (same date and number, or same shop and total). Open it to check."
      className="inline-flex shrink-0 items-center gap-1 rounded-full bg-amber-50 px-2 py-0.5 text-xs font-medium text-amber-800 ring-1 ring-amber-200"
    >
      <Copy className="size-3.5" aria-hidden />
      Possible copy
    </span>
  );
}
