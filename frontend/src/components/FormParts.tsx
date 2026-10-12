import { Circle, CircleAlert, CircleCheck, Eye, EyeOff, Info } from 'lucide-react';
import { useId, useState, type InputHTMLAttributes, type ReactNode } from 'react';
import { Button } from './Button';

type FieldProps = InputHTMLAttributes<HTMLInputElement> & {
  label: string;
  error?: string;
  hint?: string;
  /** Something shown inside the input on the right, e.g. the eye button. */
  trailing?: ReactNode;
};

/** A labelled input with an optional hint and an error message under it. */
export function TextField({ label, error, hint, trailing, ...input }: FieldProps) {
  const id = useId();
  const noteId = `${id}-note`;
  return (
    <div>
      <label htmlFor={id} className="block text-sm font-medium text-slate-700">
        {label}
      </label>
      <div className="relative mt-1.5">
        <input
          id={id}
          aria-invalid={error ? true : undefined}
          aria-describedby={error || hint ? noteId : undefined}
          className={`block w-full rounded-lg border px-3 py-2 text-sm shadow-xs outline-none placeholder:text-slate-400 focus:ring-2 read-only:bg-slate-100 read-only:text-slate-600 ${
            trailing ? 'pr-10' : ''
          } ${error ? 'border-rose-400 focus:ring-rose-200' : 'border-slate-300 bg-white focus:border-brand-500 focus:ring-brand-100'}`}
          {...input}
        />
        {trailing && <div className="absolute inset-y-0 right-0 flex items-center pr-1.5">{trailing}</div>}
      </div>
      {(error || hint) && (
        <p id={noteId} className={`mt-1 text-xs ${error ? 'text-rose-600' : 'text-slate-500'}`}>
          {error ?? hint}
        </p>
      )}
    </div>
  );
}

/** A password input with an eye button that shows/hides what was typed. */
export function PasswordField(props: Omit<FieldProps, 'type' | 'trailing'>) {
  const [visible, setVisible] = useState(false);
  const Icon = visible ? EyeOff : Eye;
  return (
    <TextField
      {...props}
      // type="text" shows the characters, type="password" shows dots.
      type={visible ? 'text' : 'password'}
      trailing={
        <button
          type="button" // not "submit": clicking the eye must not send the form
          onClick={() => setVisible((v) => !v)}
          aria-label={visible ? 'Hide password' : 'Show password'}
          aria-pressed={visible}
          className="rounded-md p-1.5 text-slate-500 hover:bg-slate-100 hover:text-slate-700"
        >
          <Icon className="size-4" aria-hidden />
        </button>
      }
    />
  );
}

/** The form's full-width main button. While busy: a spinner and what's happening ("Signing in…"). */
export function SubmitButton({ busy, busyText, children }: { busy: boolean; busyText: string; children: ReactNode }) {
  return (
    <Button type="submit" variant="primary" full busy={busy}>
      {busy ? busyText : children}
    </Button>
  );
}

/** A coloured message box above a form; colour always comes with an icon. */
export function Alert({ tone, children }: { tone: 'error' | 'success' | 'info'; children: ReactNode }) {
  const { Icon, colours } = ALERT_TONES[tone];
  return (
    <div role={tone === 'error' ? 'alert' : 'status'} className={`flex gap-2 rounded-lg p-3 text-sm ring-1 ${colours}`}>
      <Icon className="mt-0.5 size-4 shrink-0" aria-hidden />
      <div>{children}</div>
    </div>
  );
}

const ALERT_TONES = {
  error: { Icon: CircleAlert, colours: 'bg-rose-50 text-rose-800 ring-rose-200' },
  success: { Icon: CircleCheck, colours: 'bg-emerald-50 text-emerald-800 ring-emerald-200' },
  info: { Icon: Info, colours: 'bg-brand-50 text-brand-700 ring-brand-100' },
};

/** The backend's password rules (ValidPassword.java), ticked off live while typing. */
const PASSWORD_RULES: [string, (password: string) => boolean][] = [
  ['8+ characters', (p) => p.length >= 8],
  ['A letter', (p) => /[A-Za-z]/.test(p)],
  ['A number', (p) => /\d/.test(p)],
];

/** Shown under a new-password field instead of a hint line. The backend still checks for real. */
export function PasswordRules({ password }: { password: string }) {
  return (
    <ul className="-mt-2 flex flex-wrap gap-x-4 gap-y-1 text-xs" aria-label="Password rules">
      {PASSWORD_RULES.map(([label, test]) => {
        const ok = test(password);
        return (
          <li key={label} className={`inline-flex items-center gap-1 ${ok ? 'text-emerald-700' : 'text-slate-500'}`}>
            {ok ? <CircleCheck className="size-3.5" aria-hidden /> : <Circle className="size-3.5" aria-hidden />}
            {label}
            <span className="sr-only">{ok ? '(done)' : '(not yet)'}</span>
          </li>
        );
      })}
    </ul>
  );
}
