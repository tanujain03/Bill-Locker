import { Link } from 'react-router';
import { useEffect, useRef, useState } from 'react';
import { BILL_CATEGORY_LABELS, DOCUMENT_TYPE_LABELS, deleteDocument, extractDocument, formatAmount, type DocumentSummary } from '../../lib/documents';
import { deleteSelectedDocuments } from '../../lib/bulk-delete';
import { runDocumentAction } from '../../lib/document-actions';
import { ShareDocumentsDialog } from './ShareDocumentsDialog';
import { errorMessage } from '../../lib/api';
import { useFeedback } from '../../lib/feedback-context';
import { SelectionActionsMenu } from './SelectionActionsMenu';
import { DuplicateBadge } from './DuplicateWarning';
import { ReturnBadge } from './ReturnWindow';
import { FileTypeIcon, SourceBadge } from './SourceBadge';
import { StatusBadge } from './StatusBadge';

/** The list of bills on the Documents and Bills & receipts pages: each row opens the bill. */
export function DocumentList({ documents, filtered, emptyText, selectionScope, onDeleted, onRead }: {
  documents: DocumentSummary[];
  filtered: boolean;
  /** What an empty, unfiltered list says (each page has its own). */
  emptyText?: string;
  /** Changing search or filters clears selection, including when old rows are still loading. */
  selectionScope: string;
  onDeleted: (ids: string[]) => void;
  onRead: () => void;
}) {
  const { confirm, toast } = useFeedback();
  const [selection, setSelection] = useState({ scope: selectionScope, ids: new Set<string>() });
  const [activeAction, setActiveAction] = useState<'deleting' | 'reading' | null>(null);
  const busy = activeAction !== null;
  const [sharing, setSharing] = useState<DocumentSummary[] | null>(null);
  const locked = useRef(false);
  const [failure, setFailure] = useState<{ scope: string; text: string } | null>(null);
  const selected = new Set(documents.filter((d) => selection.scope === selectionScope && selection.ids.has(d.id)).map((d) => d.id));
  useEffect(() => {
    setSelection({ scope: selectionScope, ids: new Set() });
    setFailure(null);
  }, [selectionScope]);
  const allSelected = documents.length > 0 && selected.size === documents.length;

  function toggle(id: string) {
    setFailure(null);
    const next = new Set(selected);
    if (next.has(id)) next.delete(id);
    else next.add(id);
    setSelection({ scope: selectionScope, ids: next });
  }

  async function removeSelected() {
    if (locked.current || selected.size === 0) return;
    locked.current = true;
    const ids = [...selected];
    const count = ids.length;
    try {
      if (!(await confirm({
        title: `Delete ${count} document${count === 1 ? '' : 's'}?`,
        message: 'The selected files and their product and warranty details will be permanently deleted. This cannot be undone.',
        confirmLabel: `Delete ${count} document${count === 1 ? '' : 's'}`,
        danger: true,
      }))) return;
      setActiveAction('deleting');
      setFailure(null);
      const result = await deleteSelectedDocuments(ids, deleteDocument);
      if (result.deleted.length) {
        onDeleted(result.deleted);
        toast(`Deleted ${result.deleted.length} document${result.deleted.length === 1 ? '' : 's'}.`);
      }
      setSelection((current) => ({ ...current, ids: new Set([...current.ids].filter((id) => !result.deleted.includes(id))) }));
      if (result.failed.length) {
        setFailure({ scope: selectionScope, text: `${result.failed.length} document${result.failed.length === 1 ? '' : 's'} could not be deleted. ${errorMessage(result.failed[0].error)} Failed documents remain selected so you can retry.` });
      }
    } finally {
      locked.current = false;
      setActiveAction(null);
    }
  }

  async function readSelected() {
    if (locked.current || selected.size === 0) return;
    locked.current = true;
    const ids = [...selected];
    try {
      if (!(await confirm({
        title: `Read ${ids.length} document${ids.length === 1 ? '' : 's'} again?`,
        message: 'A new AI reading will replace the saved details, including any corrections. Review the new details afterward.',
        confirmLabel: 'Read again',
      }))) return;
      setActiveAction('reading');
      setFailure(null);
      // AI reads are slow: process one at a time to avoid overwhelming the service.
      const result = await runDocumentAction(ids, extractDocument, 1);
      if (result.completed.length) {
        onRead();
        toast(`Read ${result.completed.length} document${result.completed.length === 1 ? '' : 's'}. Open them to review the details.`);
      }
      const completed = new Set(result.completed.map((r) => r.id));
      setSelection((current) => ({ ...current, ids: new Set([...current.ids].filter((id) => !completed.has(id))) }));
      if (result.failed.length) {
        setFailure({ scope: selectionScope, text: `${result.failed.length} document${result.failed.length === 1 ? '' : 's'} could not be read. ${errorMessage(result.failed[0].error)} Failed documents remain selected so you can retry.` });
      }
    } finally {
      locked.current = false;
      setActiveAction(null);
    }
  }

  if (documents.length === 0) {
    return (
      <p className="mt-6 rounded-xl border border-slate-200 bg-white p-6 text-center text-sm text-slate-600">
        {filtered ? 'No documents match your search.' : (emptyText ?? 'No documents yet. Upload your first bill above.')}
      </p>
    );
  }
  return (
    <div className="mt-4">
      {sharing && <ShareDocumentsDialog documents={sharing} onClose={() => setSharing(null)} />}
      <div className="flex flex-wrap items-center justify-between gap-3 rounded-t-xl border border-slate-200 bg-white px-4 py-3">
        <label className="flex items-center gap-3 text-sm text-slate-700">
          <input
            type="checkbox"
            checked={allSelected}
            ref={(input) => { if (input) input.indeterminate = selected.size > 0 && !allSelected; }}
            disabled={busy}
            onChange={() => setSelection({ scope: selectionScope, ids: allSelected ? new Set() : new Set(documents.map((d) => d.id)) })}
            className="size-4 accent-brand-600"
          />
          Select all shown ({documents.length})
        </label>
        {selected.size > 0 && (
          <div role="group" aria-label="Actions for selected documents" className="flex flex-wrap items-center gap-2">
            <span role="status" className="text-sm font-medium text-slate-700">{selected.size} selected</span>
            {busy && <span role="status" className="text-sm text-slate-500">{activeAction === 'reading' ? 'Reading...' : 'Deleting...'}</span>}
            <SelectionActionsMenu
              busy={busy}
              onShare={() => setSharing(documents.filter((d) => selected.has(d.id)))}
              onRead={readSelected}
              onDelete={removeSelected}
              onClear={() => { setSelection({ scope: selectionScope, ids: new Set() }); setFailure(null); }}
            />
          </div>
        )}
      </div>
      {failure?.scope === selectionScope && <p role="alert" className="border-x border-slate-200 bg-rose-50 px-4 py-3 text-sm text-rose-800">{failure.text}</p>}
      <ul aria-label="Documents" aria-busy={busy} className="divide-y divide-slate-200 overflow-hidden rounded-b-xl border border-t-0 border-slate-200 bg-white">
        {documents.map((d) => (
          <li key={d.id} className={`flex items-center ${selected.has(d.id) ? 'bg-brand-50' : ''}`}>
            <label className="flex shrink-0 items-center self-stretch px-4">
              <input type="checkbox" checked={selected.has(d.id)} disabled={busy} onChange={() => toggle(d.id)} aria-label={`Select ${d.fileName}`} className="size-4 accent-brand-600" />
            </label>
            <Link to={`/documents/${d.id}`} className="flex min-w-0 flex-1 items-center gap-4 py-3 pr-4 hover:bg-slate-50">
              <FileTypeIcon contentType={d.contentType} />
              <div className="min-w-0 flex-1">
                <div className="flex min-w-0 items-center gap-2">
                  <p className="truncate font-medium">{d.sellerName ?? d.fileName}</p>
                  <SourceBadge sourceGmail={d.sourceGmail} sample={d.sample} />
                  {d.possibleDuplicate && <DuplicateBadge />}
                  <ReturnBadge returnByDate={d.returnByDate} />
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
    </div>
  );
}
