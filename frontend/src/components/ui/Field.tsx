import { ChevronDown, CircleAlert } from 'lucide-react';
import { createContext, useContext, useId, type ComponentProps, type ReactNode } from 'react';
import { cn } from '@/lib/cn';

interface FieldContextValue {
  id: string;
  describedBy?: string;
  invalid: boolean;
}

const FieldContext = createContext<FieldContextValue | null>(null);

interface FieldProps {
  label?: ReactNode;
  hint?: ReactNode;
  error?: string;
  required?: boolean;
  /** Extra element rendered at the end of the label row (e.g. a confidence badge). */
  labelAddon?: ReactNode;
  className?: string;
  children: ReactNode;
}

/** Label + control + hint/error, wired together with ids and aria attributes. */
export function Field({ label, hint, error, required, labelAddon, className, children }: FieldProps) {
  const id = useId();
  const hintId = hint ? `${id}-hint` : undefined;
  const errorId = error ? `${id}-error` : undefined;
  const describedBy = [errorId, hintId].filter(Boolean).join(' ') || undefined;

  return (
    <FieldContext.Provider value={{ id, describedBy, invalid: Boolean(error) }}>
      <div className={cn('space-y-1.5', className)}>
        {(label || labelAddon) && (
          <div className="flex min-h-5 flex-wrap items-center justify-between gap-x-2 gap-y-1">
            {label && (
              <label htmlFor={id} className="text-sm font-medium text-slate-700">
                {label}
                {required && (
                  <span className="text-rose-600" aria-hidden>
                    {' '}
                    *
                  </span>
                )}
              </label>
            )}
            {labelAddon}
          </div>
        )}
        {children}
        {error ? (
          <p id={errorId} role="alert" className="flex items-center gap-1 text-xs font-medium text-rose-600">
            <CircleAlert className="size-3.5 shrink-0" aria-hidden />
            {error}
          </p>
        ) : (
          hint && (
            <p id={hintId} className="text-xs text-slate-500">
              {hint}
            </p>
          )
        )}
      </div>
    </FieldContext.Provider>
  );
}

function useFieldAria(props: { id?: string; 'aria-describedby'?: string; 'aria-invalid'?: ComponentProps<'input'>['aria-invalid'] }) {
  const field = useContext(FieldContext);
  return {
    id: props.id ?? field?.id,
    'aria-describedby': props['aria-describedby'] ?? field?.describedBy,
    'aria-invalid': props['aria-invalid'] ?? (field?.invalid ? true : undefined),
  };
}

const CONTROL =
  'w-full rounded-lg border border-slate-300 bg-white text-sm text-slate-900 shadow-xs transition-colors ' +
  'placeholder:text-slate-400 focus:border-brand-500 focus:outline-none focus:ring-4 focus:ring-brand-500/15 ' +
  'disabled:cursor-not-allowed disabled:bg-slate-50 disabled:text-slate-500 read-only:bg-slate-50 ' +
  'aria-[invalid=true]:border-rose-400 aria-[invalid=true]:focus:ring-rose-500/15';

export function Input({ className, ...props }: ComponentProps<'input'>) {
  const aria = useFieldAria(props);
  return <input className={cn(CONTROL, 'h-10 px-3', className)} {...props} {...aria} />;
}

export function Textarea({ className, ...props }: ComponentProps<'textarea'>) {
  const aria = useFieldAria(props);
  return <textarea className={cn(CONTROL, 'min-h-22 px-3 py-2 leading-relaxed', className)} {...props} {...aria} />;
}

export function Select({ className, children, ...props }: ComponentProps<'select'>) {
  const aria = useFieldAria(props);
  return (
    <div className="relative">
      <select className={cn(CONTROL, 'h-10 appearance-none pl-3 pr-9', className)} {...props} {...aria}>
        {children}
      </select>
      <ChevronDown
        className="pointer-events-none absolute right-3 top-1/2 size-4 -translate-y-1/2 text-slate-400"
        aria-hidden
      />
    </div>
  );
}
