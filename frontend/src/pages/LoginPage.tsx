import { UserPlus } from 'lucide-react';
import { useState, type FormEvent, type MouseEvent } from 'react';
import { Link, useLocation } from 'react-router';
import { AuthLayout } from '../components/AuthLayout';
import { Alert, PasswordField, SubmitButton, TextField } from '../components/FormParts';
import { ApiError, errorMessage } from '../lib/api';
import { useAuth } from '../lib/auth-context';

/** The first page (/ and /login). */
export function LoginPage() {
  const { login } = useAuth();
  // Other pages send us here with a message: { message: "Your password has been changed…" }
  // (success) or { info: "Enter your email…" } (from /forgot-password opened without one).
  const state = useLocation().state as { message?: string; info?: string } | null;

  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [remember, setRemember] = useState(true);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({});

  /**
   * "Forgot password?" takes the email typed above to the next page (in the router's
   * state, not the URL, so the address never shows up in browser history or logs).
   */
  function handleForgotPassword(event: MouseEvent) {
    if (!email.trim()) {
      event.preventDefault(); // stay here: there's no email to send a link to yet
      setFieldErrors({ email: 'Enter your email here first, then click “Forgot password?”' });
    }
  }

  async function handleSubmit(event: FormEvent) {
    event.preventDefault(); // stop the browser's own full-page form submit
    setBusy(true);
    setError(null);
    setFieldErrors({});
    try {
      await login(email, password, remember);
      // Nothing else to do: GuestOnly sees the signed-in user and moves on to /home.
    } catch (err) {
      setError(errorMessage(err));
      if (err instanceof ApiError) setFieldErrors(err.fieldErrors);
      setBusy(false);
    }
  }

  return (
    <AuthLayout title="Sign in to your locker">
      <form onSubmit={handleSubmit} noValidate className="space-y-4">
        {state?.message && !error && <Alert tone="success">{state.message}</Alert>}
        {state?.info && !error && <Alert tone="info">{state.info}</Alert>}
        {error && <Alert tone="error">{error}</Alert>}
        <TextField
          label="Email"
          type="email"
          autoComplete="email"
          placeholder="Enter your email address…"
          value={email}
          onChange={(e) => {
            setEmail(e.target.value);
            setFieldErrors(({ email: _old, ...rest }) => rest); // typing clears the old email message
          }}
          error={fieldErrors.email}
        />
        <PasswordField
          label="Password"
          autoComplete="current-password"
          placeholder="Enter your password…"
          value={password}
          onChange={(e) => setPassword(e.target.value)}
          error={fieldErrors.password}
        />
        <div className="flex items-center justify-between">
          <label className="inline-flex items-center gap-2 text-sm text-slate-700">
            <input
              type="checkbox"
              checked={remember}
              onChange={(e) => setRemember(e.target.checked)}
              className="size-4 rounded border-slate-300 accent-brand-600"
            />
            Remember me
          </label>
          <Link
            to="/forgot-password"
            state={{ email: email.trim() }}
            onClick={handleForgotPassword}
            className="text-sm font-medium text-brand-700 hover:underline"
          >
            Forgot password?
          </Link>
        </div>
        <SubmitButton busy={busy}>Sign in</SubmitButton>
      </form>

      <div className="my-6 flex items-center gap-3 text-sm text-slate-500" role="separator">
        <span className="h-px flex-1 bg-slate-300" />
        or
        <span className="h-px flex-1 bg-slate-300" />
      </div>

      <Link
        to="/register"
        className="flex w-full items-center justify-center gap-2 rounded-lg border border-brand-600 px-4 py-2.5 text-sm font-semibold text-brand-700 hover:bg-brand-50"
      >
        <UserPlus className="size-4" aria-hidden />
        Create a new account
      </Link>
    </AuthLayout>
  );
}
