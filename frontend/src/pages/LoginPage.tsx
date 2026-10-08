import { useState, type FormEvent } from 'react';
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
    <AuthLayout
      title="Sign in to your locker"
      // One way to sign up on this screen (the promo panel's button is the big one).
      footer={<>New here? <Link to="/register" className="font-medium text-brand-700 hover:underline">Create an account</Link></>}
    >
      <form onSubmit={handleSubmit} noValidate className="space-y-4">
        {state?.message && !error && <Alert tone="success">{state.message}</Alert>}
        {state?.info && !error && <Alert tone="info">{state.info}</Alert>}
        {error && <Alert tone="error">{error}</Alert>}
        <TextField
          label="Email"
          type="email"
          autoComplete="email"
          placeholder="you@example.com"
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
          {/* Takes the email typed above along (in the router's state, not the URL, so the
              address never shows up in browser history or logs). It can be changed there. */}
          <Link
            to="/forgot-password"
            state={{ email: email.trim() }}
            className="text-sm font-medium text-brand-700 hover:underline"
          >
            Forgot password?
          </Link>
        </div>
        <SubmitButton busy={busy} busyText="Signing in…">Sign in</SubmitButton>
      </form>

    </AuthLayout>
  );
}
