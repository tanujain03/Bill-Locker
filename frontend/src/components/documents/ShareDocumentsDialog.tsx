import { Download, LoaderCircle, Share2, X } from 'lucide-react';
import { useEffect, useRef, useState } from 'react';
import { errorMessage } from '../../lib/api';
import { downloadDocument, type DocumentSummary } from '../../lib/documents';
import { runDocumentAction } from '../../lib/document-actions';
import { Button } from '../Button';

/** Prepare private files first; a second click keeps the native share sheet within a user gesture. */
export function ShareDocumentsDialog({ documents, onClose }: { documents: DocumentSummary[]; onClose: () => void }) {
  const dialog = useRef<HTMLDialogElement>(null);
  const [files, setFiles] = useState<File[]>([]);
  const [loading, setLoading] = useState(true);
  const [sharing, setSharing] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [retry, setRetry] = useState(0);
  const [unsupported, setUnsupported] = useState(false);

  useEffect(() => {
    dialog.current?.showModal();
  }, []);

  useEffect(() => {
    let current = true;
    setLoading(true);
    setError(null);
    setFiles([]);
    runDocumentAction(documents.map((d) => d.id), async (id) => {
      const d = documents.find((d) => d.id === id)!;
      return new File([await downloadDocument(id)], d.fileName, { type: d.contentType });
    }).then((result) => {
      if (!current) return;
      if (result.failed.length) {
        setError(`Could not prepare ${result.failed.length} document(s). ${errorMessage(result.failed[0].error)}`);
      } else {
        // Preserve the user's list order even when downloads finish out of order.
        const prepared = documents.map((d) => result.completed.find((r) => r.id === d.id)!.value);
        setFiles(prepared);
        setUnsupported(!navigator.canShare?.({ files: prepared }));
      }
      setLoading(false);
    });
    return () => { current = false; };
  }, [documents, retry]);

  async function share() {
    setSharing(true);
    setError(null);
    try {
      await navigator.share({ files, title: 'Documents from Bill Locker' });
      onClose();
    } catch (e) {
      if (!(e instanceof DOMException && e.name === 'AbortError')) {
        setError(errorMessage(e));
        setUnsupported(true);
      }
    } finally {
      setSharing(false);
    }
  }

  function download(file: File) {
    const url = URL.createObjectURL(file);
    Object.assign(document.createElement('a'), { href: url, download: file.name }).click();
    setTimeout(() => URL.revokeObjectURL(url), 1000);
  }

  return (
    <dialog ref={dialog} onCancel={(e) => { e.preventDefault(); onClose(); }} aria-labelledby="share-documents-title" className="fixed inset-0 m-auto max-h-[85dvh] w-[calc(100%_-_2rem)] max-w-lg overflow-y-auto rounded-2xl border border-slate-200 bg-white p-6 shadow-xl backdrop:bg-slate-900/40">
      <div className="flex items-center justify-between gap-3">
        <h2 id="share-documents-title" className="text-lg font-semibold">Share {documents.length} document{documents.length === 1 ? '' : 's'}</h2>
        <Button variant="ghost" size="sm" icon={X} onClick={onClose} aria-label="Close sharing" />
      </div>
      {loading ? (
        <p role="status" className="mt-4 flex items-center gap-2 text-sm text-slate-600"><LoaderCircle className="size-4 animate-spin" aria-hidden />Preparing files…</p>
      ) : (
        <>
          {error && <p role="alert" className="mt-4 text-sm text-rose-700">{error}</p>}
          {files.length > 0 ? (
            <>
              <p className="mt-3 text-sm text-slate-600">{unsupported ? 'Your browser cannot share these files directly. Download each file below, then attach them in your preferred app.' : 'Choose an app and recipient in your device’s share window.'}</p>
              <ul className="mt-4 divide-y divide-slate-100">
                {files.map((file, i) => <li key={i} className="flex items-center justify-between gap-3 py-2"><span className="min-w-0 truncate text-sm">{file.name}</span>{unsupported && <Button size="sm" icon={Download} onClick={() => download(file)} aria-label={`Download ${file.name}`}>Download</Button>}</li>)}
              </ul>
              {!unsupported && <Button className="mt-4" icon={Share2} busy={sharing} onClick={share}>Share files</Button>}
            </>
          ) : <Button className="mt-4" onClick={() => setRetry((n) => n + 1)}>Retry preparing files</Button>}
        </>
      )}
    </dialog>
  );
}
