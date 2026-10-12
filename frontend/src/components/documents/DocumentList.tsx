import { Link } from 'react-router';
import { BILL_CATEGORY_LABELS, DOCUMENT_TYPE_LABELS, formatAmount, type DocumentSummary } from '../../lib/documents';
import { FileTypeIcon, SourceBadge } from './SourceBadge';
import { StatusBadge } from './StatusBadge';

/** The list of bills on the Documents and Bills & receipts pages: each row opens the bill. */
export function DocumentList({ documents, filtered, emptyText }: {
  documents: DocumentSummary[];
  filtered: boolean;
  /** What an empty, unfiltered list says (each page has its own). */
  emptyText?: string;
}) {
  if (documents.length === 0) {
    return (
      <p className="mt-6 rounded-xl border border-slate-200 bg-white p-6 text-center text-sm text-slate-600">
        {filtered ? 'No documents match your search.' : (emptyText ?? 'No documents yet. Upload your first bill above.')}
      </p>
    );
  }
  return (
    <ul className="mt-4 divide-y divide-slate-200 overflow-hidden rounded-xl border border-slate-200 bg-white">
      {documents.map((d) => (
        <li key={d.id}>
          <Link to={`/documents/${d.id}`} className="flex items-center gap-4 px-4 py-3 hover:bg-slate-50">
            <FileTypeIcon contentType={d.contentType} />
            <div className="min-w-0 flex-1">
              <div className="flex min-w-0 items-center gap-2">
                <p className="truncate font-medium">{d.sellerName ?? d.fileName}</p>
                <SourceBadge sourceGmail={d.sourceGmail} />
              </div>
              <p className="mt-0.5 truncate text-sm text-slate-500">
                {[
                  // A bill or receipt says what it was for (Travel, Food…); others their type.
                  d.documentType === 'RECEIPT' && d.category
                    ? BILL_CATEGORY_LABELS[d.category]
                    : d.documentType && DOCUMENT_TYPE_LABELS[d.documentType],
                  d.firstProductName && (d.itemCount > 1 ? `${d.firstProductName} +${d.itemCount - 1} more` : d.firstProductName),
                  d.purchaseDate,
                  d.sellerName && d.fileName,
                ]
                  .filter(Boolean)
                  .join(' · ')}
              </p>
            </div>
            {d.totalAmount != null && (
              <span className="shrink-0 text-sm font-medium tabular-nums">{formatAmount(d.totalAmount)}</span>
            )}
            <StatusBadge status={d.status} reading={d.readQueued} />
          </Link>
        </li>
      ))}
    </ul>
  );
}
