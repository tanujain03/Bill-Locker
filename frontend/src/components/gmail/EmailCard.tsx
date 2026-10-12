import { CircleAlert, LoaderCircle, Mail } from 'lucide-react';
import { FileTypeIcon } from '../documents/SourceBadge';
import { Link } from 'react-router';
import { EMAIL_KIND_LABELS, formatSize, type GmailEmail, type GmailFile } from '../../lib/gmail';
import { StatusBadge } from '../documents/StatusBadge';
import { buttonClass } from '../Button';

type Actions = {
  selected: Set<string>;
  busy: boolean;
  onToggle: (fileId: string) => void;
  onImport: (fileIds: string[]) => void;
  onIgnore: (fileIds: string[]) => void;
  onRestore: (fileIds: string[]) => void;
};

/** One email with its attachments. Ticking boxes and the row buttons call back into the page. */
export function EmailCard({ email, ...actions }: { email: GmailEmail } & Actions) {
  const sender = email.fromName || email.fromEmail || 'Unknown sender';
  return (
    <li className="rounded-xl border border-slate-200 bg-white p-4">
      <div className="flex flex-wrap items-center gap-x-3 gap-y-1">
        <span className="inline-flex items-center gap-1 rounded-full bg-brand-50 px-2 py-0.5 text-xs font-medium text-brand-700">
          <Mail className="size-3.5" aria-hidden />
          {EMAIL_KIND_LABELS[email.kind]}
        </span>
        <span className="min-w-0 truncate text-sm font-medium">{sender}</span>
        {email.receivedAt && (
          <span className="text-sm text-slate-500">{new Date(email.receivedAt).toLocaleDateString()}</span>
        )}
      </div>
      {/* React escapes text, so API strings are never rendered as HTML. */}
      <p className="mt-1 font-medium break-words">{email.subject || '(no subject)'}</p>
      <p className="text-xs text-slate-600">via {email.accountEmail}</p>
      {email.snippet && <p className="mt-1 truncate text-sm text-slate-600">{email.snippet}</p>}

      <ul className="mt-3 divide-y divide-slate-100 border-t border-slate-100">
        {email.files.map((file) => (
          <FileRow key={file.id} file={file} {...actions} />
        ))}
      </ul>
    </li>
  );
}

/** Small row buttons: the step forward (Import, Open) has a border, the others are quiet. */
const PRIMARY = buttonClass({ variant: 'secondary', size: 'sm' });
const QUIET = buttonClass({ variant: 'ghost', size: 'sm' });

function FileRow({ file, selected, busy, onToggle, onImport, onIgnore, onRestore }: { file: GmailFile } & Actions) {
  // Only files you can act on in bulk get a checkbox.
  const selectable = file.status === 'NEW' || file.status === 'FAILED' || file.status === 'IGNORED';

  return (
    <li className="flex flex-wrap items-center gap-x-3 gap-y-2 py-2.5">
      {selectable ? (
        <input
          type="checkbox"
          checked={selected.has(file.id)}
          onChange={() => onToggle(file.id)}
          aria-label={`Select ${file.fileName}`}
          className="size-4 accent-brand-600"
        />
      ) : (
        <span className="size-4" aria-hidden />
      )}
      <FileTypeIcon contentType={file.contentType} size="sm" />
      <span className="min-w-0 flex-1 basis-40 truncate text-sm">{file.fileName}</span>
      <span className="text-xs text-slate-600">{formatSize(file.sizeBytes)}</span>

      {(file.status === 'NEW' || file.status === 'IGNORED') && (
        <>
          <button type="button" disabled={busy} onClick={() => onImport([file.id])} className={PRIMARY}>
            Import
          </button>
          <button
            type="button"
            disabled={busy}
            onClick={() => (file.status === 'NEW' ? onIgnore : onRestore)([file.id])}
            className={QUIET}
          >
            {file.status === 'NEW' ? 'Ignore' : 'Restore'}
          </button>
        </>
      )}
      {file.status === 'IMPORTING' && (
        <span role="status" className="inline-flex items-center gap-1.5 text-sm text-slate-600">
          <LoaderCircle className="size-4 animate-spin text-brand-600" aria-hidden />
          Importing…
        </span>
      )}
      {file.status === 'FAILED' && (
        <>
          <span className="inline-flex items-center gap-1.5 text-sm text-rose-700">
            <CircleAlert className="size-4 shrink-0" aria-hidden />
            {file.error ?? 'Import failed.'}
          </span>
          <button type="button" disabled={busy} onClick={() => onImport([file.id])} className={PRIMARY}>
            Retry
          </button>
          <button type="button" disabled={busy} onClick={() => onIgnore([file.id])} className={QUIET}>
            Ignore
          </button>
        </>
      )}
      {file.status === 'IMPORTED' &&
        (file.document ? (
          <>
            <StatusBadge status={file.document.status} reading={file.document.readQueued} />
            <Link to={`/documents/${file.document.id}`} className={PRIMARY}>
              Open document
            </Link>
          </>
        ) : (
          <>
            <span className="text-sm text-slate-500">Deleted from Documents</span>
            <button type="button" disabled={busy} onClick={() => onImport([file.id])} className={PRIMARY}>
              Import again
            </button>
          </>
        ))}
    </li>
  );
}
