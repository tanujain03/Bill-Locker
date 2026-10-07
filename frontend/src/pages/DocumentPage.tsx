import {
  ArrowLeft,
  CircleAlert,
  CircleCheck,
  CircleDot,
  Download,
  Eye,
  LoaderCircle,
  RefreshCw,
  Save,
  Sparkles,
  Trash2,
  Undo2,
} from 'lucide-react';
import { useCallback, useEffect, useRef, useState, type FormEvent, type ReactNode } from 'react';
import { Link, useNavigate, useParams, useSearchParams } from 'react-router';
import { AppHeader } from '../components/AppHeader';
import { CopyButton } from '../components/documents/CopyButton';
import { DetailsForm } from '../components/documents/DetailsForm';
import { DocumentPreview } from '../components/documents/DocumentPreview';
import { StatusBadge } from '../components/documents/StatusBadge';
import { Alert } from '../components/FormParts';
import { ApiError, errorMessage } from '../lib/api';
import { detailsToText, fromForm, toForm, type FormValues } from '../lib/document-form';
import {
  deleteDocument,
  downloadDocument,
  extractDocument,
  getDocument,
  saveDocument,
  type DocumentDetail,
} from '../lib/documents';

type Message = { tone: 'error' | 'success' | 'info'; text: string };

/**
 * /documents/:id: the file on the left, its details on the right.
 * Flow: (upload) → read with AI → the user checks and fixes → Save (at the end of the form).
 */
export function DocumentPage() {
  const { id = '' } = useParams();
  const navigate = useNavigate();
  const [searchParams, setSearchParams] = useSearchParams();

  const [document, setDocument] = useState<DocumentDetail | null>(null);
  const [form, setForm] = useState<FormValues | null>(null);
  const [busy, setBusy] = useState<'reading' | 'saving' | 'deleting' | null>(null);
  /** Page-level news: AI reading, download/delete problems. Shown at the top. */
  const [message, setMessage] = useState<Message | null>(null);
  /** Why the last save failed. Shown next to the Save button, where the user is looking. */
  const [saveError, setSaveError] = useState<string | null>(null);
  const [justSaved, setJustSaved] = useState(false);
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({});

  /** Edits on screen that aren't saved yet. */
  const dirty = Boolean(document && form && JSON.stringify(form) !== JSON.stringify(toForm(document)));

  /** Show a document from the backend and reset the form to its values. */
  const show = useCallback((d: DocumentDetail) => {
    setDocument(d);
    setForm(toForm(d));
    setFieldErrors({});
    setSaveError(null);
  }, []);

  const read = useCallback(async () => {
    setBusy('reading');
    setMessage(null);
    setJustSaved(false);
    try {
      show(await extractDocument(id));
      setMessage({ tone: 'info', text: 'Details read by AI. Please check them, fix anything wrong, then save.' });
    } catch (error) {
      // No key → just a hint; the user can type the details in.
      const tone = error instanceof ApiError && error.code === 'AI_NOT_CONFIGURED' ? 'info' : 'error';
      setMessage({ tone, text: errorMessage(error) });
    } finally {
      setBusy(null);
    }
  }, [id, show]);

  // Load the document. Right after an upload (?read=1) also read it with AI, once.
  // (The ref stops React's development double-run from calling the AI twice.)
  const autoReadStarted = useRef(false);
  useEffect(() => {
    getDocument(id)
      .then((d) => {
        show(d);
        if (searchParams.get('read') === '1' && d.status === 'UPLOADED' && !autoReadStarted.current) {
          autoReadStarted.current = true;
          setSearchParams({}, { replace: true }); // a page reload must not read it again
          read();
        }
      })
      .catch((error) => setMessage({ tone: 'error', text: errorMessage(error) }));
    // Runs again only when the id changes (read and searchParams are left out on purpose).
  }, [id]);

  /**
   * A new AI reading is stored straight away, so ask first when it would replace
   * something the user made: details they saved, or edits on screen not saved yet.
   */
  function readAgain() {
    if (!document || !form) return;
    const question =
      document.status === 'SAVED'
        ? 'This replaces your saved details with a new AI reading. Continue?'
        : dirty
          ? 'This replaces the details you typed (not saved yet) with a new AI reading. Continue?'
          : null;
    if (question && !confirm(question)) return;
    read();
  }

  async function save(event?: FormEvent) {
    event?.preventDefault();
    if (!form || busy) return;
    setBusy('saving');
    setSaveError(null);
    try {
      show(await saveDocument(id, fromForm(form)));
      setJustSaved(true);
      setMessage(null); // "AI read it, please check" is done once it's saved
    } catch (error) {
      setFieldErrors(error instanceof ApiError ? error.fieldErrors : {});
      setSaveError(errorMessage(error));
      // Take the user to the first field that needs fixing.
      requestAnimationFrame(() => window.document.querySelector<HTMLElement>('[aria-invalid="true"]')?.focus());
    } finally {
      setBusy(null);
    }
  }

  function discard() {
    if (document && confirm('Undo all changes since the last save?')) show(document);
  }

  function editForm(next: FormValues) {
    setForm(next);
    setJustSaved(false);
  }

  // Ctrl+S / ⌘S saves instead of the browser's "Save page as".
  const saveRef = useRef(save);
  saveRef.current = save;
  useEffect(() => {
    const onKey = (e: KeyboardEvent) => {
      if ((e.ctrlKey || e.metaKey) && e.key.toLowerCase() === 's') {
        e.preventDefault();
        saveRef.current();
      }
    };
    window.addEventListener('keydown', onKey);
    return () => window.removeEventListener('keydown', onKey);
  }, []);

  // Closing the tab or reloading with unsaved edits: the browser asks first.
  useEffect(() => {
    if (!dirty) return;
    const warn = (e: BeforeUnloadEvent) => e.preventDefault();
    window.addEventListener('beforeunload', warn);
    return () => window.removeEventListener('beforeunload', warn);
  }, [dirty]);

  async function download() {
    if (!document) return;
    try {
      const url = URL.createObjectURL(await downloadDocument(id));
      const link = Object.assign(window.document.createElement('a'), { href: url, download: document.fileName });
      link.click();
      URL.revokeObjectURL(url);
    } catch (error) {
      setMessage({ tone: 'error', text: errorMessage(error) });
    }
  }

  async function remove() {
    if (!confirm('Delete this document and its details? This cannot be undone.')) return;
    setBusy('deleting');
    try {
      await deleteDocument(id);
      navigate('/documents');
    } catch (error) {
      setMessage({ tone: 'error', text: errorMessage(error) });
      setBusy(null);
    }
  }

  return (
    <div className="min-h-dvh">
      <AppHeader />
      <main className="mx-auto max-w-7xl px-4 py-6">
        <Link
          to="/documents"
          onClick={(e) => {
            if (dirty && !confirm('You have unsaved changes. Leave without saving?')) e.preventDefault();
          }}
          className="inline-flex items-center gap-1 text-sm text-slate-600 hover:text-slate-900"
        >
          <ArrowLeft className="size-4" aria-hidden />
          All documents
        </Link>

        {!document ? (
          <div className="mt-4">
            {message ? <Alert tone={message.tone}>{message.text}</Alert> : <PageSkeleton />}
          </div>
        ) : (
          <>
            <div className="mt-3 flex flex-wrap items-end justify-between gap-4">
              <div className="min-w-0">
                <h1 className="truncate text-2xl font-semibold tracking-tight">
                  {document.sellerName ?? document.fileName}
                </h1>
                <div className="mt-1 flex flex-wrap items-center gap-2 text-sm text-slate-500">
                  <StatusBadge status={document.status} />
                  {document.sellerName && <span className="truncate">{document.fileName}</span>}
                </div>
              </div>

              {/* Things you do to the whole document. Save lives at the end of the form. */}
              <div className="flex flex-wrap items-center gap-2">
                <ToolbarButton onClick={readAgain} disabled={busy !== null}>
                  {document.status === 'UPLOADED' ? (
                    <Sparkles className="size-4" aria-hidden />
                  ) : (
                    <RefreshCw className="size-4" aria-hidden />
                  )}
                  {document.status === 'UPLOADED' ? 'Read with AI' : 'Read again'}
                </ToolbarButton>
                {form && (
                  <CopyButton text={detailsToText(form)} label="Copy all details">
                    Copy all
                  </CopyButton>
                )}
                <ToolbarButton onClick={download} disabled={busy !== null}>
                  <Download className="size-4" aria-hidden />
                  Download
                </ToolbarButton>
                <span className="mx-1 hidden h-6 w-px bg-slate-200 sm:block" aria-hidden />
                <ToolbarButton onClick={remove} disabled={busy !== null} danger>
                  <Trash2 className="size-4" aria-hidden />
                  {busy === 'deleting' ? 'Deleting…' : 'Delete'}
                </ToolbarButton>
              </div>
            </div>

            {message && (
              <div className="mt-4">
                <Alert tone={message.tone}>{message.text}</Alert>
              </div>
            )}

            <div className="mt-6 grid gap-6 lg:grid-cols-[minmax(0,5fr)_minmax(0,6fr)]">
              {/* The file stays in view while you scroll the details, so you can compare. */}
              <div className="lg:sticky lg:top-4 lg:self-start">
                <DocumentPreview id={id} contentType={document.contentType} fileName={document.fileName} />
              </div>

              <form noValidate onSubmit={save} className="relative">
                {busy === 'reading' && (
                  <div className="absolute inset-0 z-20 flex items-start justify-center rounded-xl bg-white/80 pt-24 backdrop-blur-[1px]">
                    <p
                      role="status"
                      className="sticky top-24 inline-flex items-center gap-2 rounded-full bg-white px-4 py-2 font-medium text-slate-700 shadow-md ring-1 ring-slate-200"
                    >
                      <LoaderCircle className="size-5 animate-spin text-brand-600" aria-hidden />
                      Reading your document with AI…
                    </p>
                  </div>
                )}
                {form && <DetailsForm value={form} onChange={editForm} fieldErrors={fieldErrors} />}

                <SaveBar
                  state={
                    busy === 'saving'
                      ? 'saving'
                      : saveError
                        ? 'error'
                        : dirty
                          ? 'dirty'
                          : justSaved
                            ? 'saved'
                            : document.status === 'SAVED'
                              ? 'clean'
                              : 'review'
                  }
                  error={saveError}
                  disabled={busy !== null || !form}
                  onDiscard={discard}
                  canDiscard={dirty}
                />
              </form>
            </div>
          </>
        )}
      </main>
    </div>
  );
}

type SaveState = 'saving' | 'error' | 'dirty' | 'saved' | 'clean' | 'review';

const SAVE_STATES: Record<Exclude<SaveState, 'error'>, { Icon: typeof Save; text: string; colour: string }> = {
  saving: { Icon: LoaderCircle, text: 'Saving…', colour: 'text-slate-600' },
  dirty: { Icon: CircleDot, text: 'Unsaved changes', colour: 'text-amber-700' },
  saved: { Icon: CircleCheck, text: 'Saved', colour: 'text-emerald-700' },
  clean: { Icon: CircleCheck, text: 'All changes saved', colour: 'text-slate-500' },
  review: { Icon: Eye, text: 'Check the details, then save', colour: 'text-slate-600' },
};

/**
 * At the end of the form: you save where you finish checking. It sticks to the
 * bottom of the window while you scroll, so Save is never more than a glance away,
 * and it says whether there is anything to save.
 */
function SaveBar(props: {
  state: SaveState;
  error: string | null;
  disabled: boolean;
  canDiscard: boolean;
  onDiscard: () => void;
}) {
  const { Icon, text, colour } =
    props.state === 'error'
      ? { Icon: CircleAlert, text: props.error ?? 'Could not save.', colour: 'text-rose-700' }
      : SAVE_STATES[props.state];
  const quiet = props.state === 'clean' || props.state === 'saved';

  return (
    <div className="sticky bottom-0 z-10 -mx-1 mt-6 px-1 pb-4">
      <div
        className={`flex flex-wrap items-center justify-between gap-3 rounded-xl border bg-white/95 px-4 py-3 shadow-lg backdrop-blur ${
          props.state === 'error' ? 'border-rose-200' : 'border-slate-200'
        }`}
      >
        <p role="status" className={`flex min-w-0 items-center gap-2 text-sm font-medium ${colour}`}>
          <Icon className={`size-4 shrink-0 ${props.state === 'saving' ? 'animate-spin' : ''}`} aria-hidden />
          <span>{text}</span>
        </p>
        <div className="flex items-center gap-2">
          {props.canDiscard && (
            <button
              type="button"
              onClick={props.onDiscard}
              disabled={props.disabled}
              className="inline-flex items-center gap-1.5 rounded-lg px-3 py-2 text-sm font-medium text-slate-600 hover:bg-slate-100 disabled:opacity-60"
            >
              <Undo2 className="size-4" aria-hidden />
              Discard
            </button>
          )}
          <button
            type="submit"
            disabled={props.disabled}
            title="Save (Ctrl+S)"
            className={`inline-flex items-center gap-2 rounded-lg px-5 py-2.5 text-sm font-semibold disabled:opacity-60 ${
              quiet
                ? 'border border-slate-300 bg-white text-slate-700 hover:bg-slate-50'
                : 'bg-brand-600 text-white shadow-sm hover:bg-brand-700'
            }`}
          >
            <Save className="size-4" aria-hidden />
            {props.state === 'saving' ? 'Saving…' : 'Save details'}
            <kbd className="hidden rounded border border-current/30 px-1 font-sans text-[10px] font-medium opacity-70 md:inline">
              Ctrl S
            </kbd>
          </button>
        </div>
      </div>
    </div>
  );
}

/** Grey blocks in the page's shape while it loads, so nothing jumps when it arrives. */
function PageSkeleton() {
  return (
    <div className="animate-pulse" aria-label="Loading document">
      <div className="h-8 w-64 rounded-lg bg-slate-200" />
      <div className="mt-2 h-5 w-32 rounded-full bg-slate-200" />
      <div className="mt-6 grid gap-6 lg:grid-cols-[minmax(0,5fr)_minmax(0,6fr)]">
        <div className="h-[70vh] rounded-xl bg-slate-200" />
        <div className="space-y-4">
          {[1, 2, 3].map((n) => (
            <div key={n} className="h-40 rounded-xl bg-slate-200" />
          ))}
        </div>
      </div>
    </div>
  );
}

function ToolbarButton(props: { onClick: () => void; disabled?: boolean; danger?: boolean; children: ReactNode }) {
  const colours = props.danger
    ? 'border-transparent bg-transparent text-rose-700 hover:bg-rose-50'
    : 'border-slate-300 bg-white text-slate-700 hover:bg-slate-50';
  return (
    <button
      type="button"
      onClick={props.onClick}
      disabled={props.disabled}
      className={`inline-flex items-center gap-1.5 rounded-lg border px-3 py-2 text-sm font-medium disabled:opacity-60 ${colours}`}
    >
      {props.children}
    </button>
  );
}
