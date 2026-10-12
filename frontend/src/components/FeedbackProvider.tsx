import { CircleAlert, CircleCheck, Info, TriangleAlert, X } from 'lucide-react';
import { useCallback, useEffect, useMemo, useRef, useState, type ReactNode } from 'react';
import { FeedbackContext, type ConfirmOptions, type ToastTone } from '../lib/feedback-context';
import { Button } from './Button';

type Toast = { id: number; text: string; tone: ToastTone };

/** Errors stay longer: there's more to read and it matters more. */
const TOAST_MS: Record<ToastTone, number> = { success: 4000, info: 4000, error: 7000 };

const TOAST_TONES = {
  success: { Icon: CircleCheck, colour: 'text-emerald-600' },
  error: { Icon: CircleAlert, colour: 'text-rose-600' },
  info: { Icon: Info, colour: 'text-brand-600' },
};

/** Shows toasts (bottom right) and the confirm dialog for the whole app. See feedback-context.ts. */
export function FeedbackProvider({ children }: { children: ReactNode }) {
  const [toasts, setToasts] = useState<Toast[]>([]);
  const nextId = useRef(0);

  const dismiss = useCallback((id: number) => setToasts((all) => all.filter((t) => t.id !== id)), []);

  const toast = useCallback(
    (text: string, tone: ToastTone = 'success') => {
      const id = ++nextId.current;
      setToasts((all) => [...all.slice(-2), { id, text, tone }]); // at most 3 on screen
      window.setTimeout(() => dismiss(id), TOAST_MS[tone]);
    },
    [dismiss],
  );

  // The open dialog's question, and how to answer the confirm() call that asked it.
  const [request, setRequest] = useState<ConfirmOptions | null>(null);
  const answer = useRef<((ok: boolean) => void) | null>(null);

  const confirm = useCallback((options: ConfirmOptions) => {
    answer.current?.(false); // a newer question replaces an unanswered one
    setRequest(options);
    return new Promise<boolean>((resolve) => {
      answer.current = resolve;
    });
  }, []);

  const value = useMemo(() => ({ toast, confirm }), [toast, confirm]);

  return (
    <FeedbackContext.Provider value={value}>
      {children}

      {request && (
        <ConfirmDialog
          options={request}
          onAnswer={(ok) => {
            answer.current?.(ok);
            answer.current = null;
            setRequest(null);
          }}
        />
      )}

      {/* Screen readers read new toasts out without moving focus. */}
      <div
        aria-live="polite"
        className="pointer-events-none fixed inset-x-4 bottom-20 z-[60] flex flex-col items-center gap-2 sm:inset-x-auto sm:right-6 sm:bottom-6 sm:items-end"
      >
        {toasts.map((t) => {
          const { Icon, colour } = TOAST_TONES[t.tone];
          return (
            <div
              key={t.id}
              role={t.tone === 'error' ? 'alert' : 'status'}
              className="pointer-events-auto flex w-full max-w-sm items-start gap-3 rounded-xl bg-slate-900 px-4 py-3 text-sm text-white shadow-xl"
            >
              <Icon className={`mt-0.5 size-4 shrink-0 ${colour}`} aria-hidden />
              <p className="min-w-0 flex-1">{t.text}</p>
              <button
                type="button"
                onClick={() => dismiss(t.id)}
                aria-label="Dismiss"
                className="-m-1 rounded p-1 text-slate-400 hover:text-white"
              >
                <X className="size-4" aria-hidden />
              </button>
            </div>
          );
        })}
      </div>
    </FeedbackContext.Provider>
  );
}

/**
 * The browser's own <dialog> in modal mode: it keeps focus inside, closes on Escape and
 * greys out the page. Focus starts on Cancel for destructive questions, so pressing
 * Enter by habit doesn't delete anything.
 */
function ConfirmDialog({ options, onAnswer }: { options: ConfirmOptions; onAnswer: (ok: boolean) => void }) {
  const dialog = useRef<HTMLDialogElement>(null);
  const answered = useRef(false);

  useEffect(() => {
    const d = dialog.current;
    if (!d) return;
    d.showModal();
    return () => d.close();
  }, []);

  function reply(ok: boolean) {
    if (answered.current) return;
    answered.current = true;
    onAnswer(ok);
  }

  return (
    <dialog
      ref={dialog}
      aria-labelledby="confirm-title"
      // Escape. (Still open = a stale close from React's development double-mount: ignore.)
      onClose={() => !dialog.current?.open && reply(false)}
      onClick={(e) => e.target === dialog.current && reply(false)} // a click on the grey backdrop
      className="m-auto w-[calc(100%-2rem)] max-w-md rounded-2xl bg-white p-0 shadow-2xl backdrop:bg-slate-900/40"
    >
      <div className="flex gap-4 p-6">
        {options.danger && (
          <span className="grid size-10 shrink-0 place-items-center rounded-full bg-rose-50 text-rose-600">
            <TriangleAlert className="size-5" aria-hidden />
          </span>
        )}
        <div className="min-w-0">
          <h2 id="confirm-title" className="text-base font-semibold text-slate-900">
            {options.title}
          </h2>
          {options.message && <p className="mt-1.5 text-sm text-slate-600">{options.message}</p>}
        </div>
      </div>
      <div className="flex flex-col-reverse gap-2 rounded-b-2xl bg-slate-50 px-6 py-4 sm:flex-row sm:justify-end">
        <Button variant="secondary" onClick={() => reply(false)} autoFocus={options.danger}>
          Cancel
        </Button>
        <Button variant={options.danger ? 'danger' : 'primary'} onClick={() => reply(true)} autoFocus={!options.danger}>
          {options.confirmLabel}
        </Button>
      </div>
    </dialog>
  );
}
