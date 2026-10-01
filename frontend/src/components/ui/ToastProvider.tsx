import { CircleAlert, CircleCheck, Info, X } from 'lucide-react';
import { useCallback, useEffect, useMemo, useRef, useState, type ReactNode } from 'react';
import { createPortal } from 'react-dom';
import { cn } from '@/lib/cn';
import { ToastContext, type ToastApi, type ToastOptions, type ToastTone } from './toast-context';

interface ToastItem extends ToastOptions {
  id: number;
  tone: ToastTone;
}

const ICONS: Record<ToastTone, ReactNode> = {
  success: <CircleCheck className="size-5 text-emerald-600" aria-hidden />,
  error: <CircleAlert className="size-5 text-rose-600" aria-hidden />,
  info: <Info className="size-5 text-brand-600" aria-hidden />,
};

export function ToastProvider({ children }: { children: ReactNode }) {
  const [toasts, setToasts] = useState<ToastItem[]>([]);
  const nextId = useRef(1);

  const dismiss = useCallback((id: number) => {
    setToasts((current) => current.filter((toast) => toast.id !== id));
  }, []);

  const show = useCallback((options: ToastOptions) => {
    const id = nextId.current++;
    setToasts((current) => [...current.slice(-3), { ...options, id, tone: options.tone ?? 'info' }]);
  }, []);

  const api = useMemo<ToastApi>(
    () => ({
      show,
      success: (title, description) => show({ title, description, tone: 'success' }),
      error: (title, description) => show({ title, description, tone: 'error', durationMs: 7000 }),
      info: (title, description) => show({ title, description, tone: 'info' }),
    }),
    [show],
  );

  return (
    <ToastContext.Provider value={api}>
      {children}
      {createPortal(
        <div
          aria-live="polite"
          aria-relevant="additions"
          className="pointer-events-none fixed inset-x-0 top-0 z-[60] flex flex-col items-center gap-2 p-4 sm:inset-x-auto sm:bottom-0 sm:right-0 sm:top-auto sm:items-end"
        >
          {toasts.map((toast) => (
            <Toast key={toast.id} toast={toast} onDismiss={dismiss} />
          ))}
        </div>,
        document.body,
      )}
    </ToastContext.Provider>
  );
}

function Toast({ toast, onDismiss: dismissById }: { toast: ToastItem; onDismiss: (id: number) => void }) {
  const { id, durationMs } = toast;
  const onDismiss = useCallback(() => dismissById(id), [dismissById, id]);

  useEffect(() => {
    const timer = window.setTimeout(onDismiss, durationMs ?? 4500);
    return () => window.clearTimeout(timer);
  }, [onDismiss, durationMs]);

  return (
    <div
      role={toast.tone === 'error' ? 'alert' : 'status'}
      className={cn(
        'pointer-events-auto flex w-full max-w-sm animate-slide-up items-start gap-3 rounded-xl border bg-white p-4 shadow-elevated',
        toast.tone === 'error' ? 'border-rose-200' : 'border-slate-200',
      )}
    >
      <span className="mt-0.5 shrink-0">{ICONS[toast.tone]}</span>
      <div className="min-w-0 flex-1">
        <p className="text-sm font-semibold text-slate-900">{toast.title}</p>
        {toast.description && <p className="mt-0.5 text-sm text-slate-600">{toast.description}</p>}
        {toast.action && (
          <button
            type="button"
            onClick={() => {
              toast.action?.onClick();
              onDismiss();
            }}
            className="mt-2 text-sm font-semibold text-brand-700 hover:text-brand-800"
          >
            {toast.action.label}
          </button>
        )}
      </div>
      <button
        type="button"
        onClick={onDismiss}
        className="-m-1 rounded-md p-1 text-slate-400 hover:bg-slate-100 hover:text-slate-600"
        aria-label="Dismiss notification"
      >
        <X className="size-4" aria-hidden />
      </button>
    </div>
  );
}
