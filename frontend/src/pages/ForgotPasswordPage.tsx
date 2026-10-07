import { useState, type FormEvent } from 'react';
import { Link, Navigate, useLocation } from 'react-router';
import { AuthLayout } from '../components/AuthLayout';
import { Alert, SubmitButton, TextField } from '../components/FormParts';
import { api, ApiError, errorMessage } from '../lib/api';

/**
 * Step 1 of resetting a password: ask for a reset link.
 * The email comes from the sign-in page ("Forgot password?" passes it in the
 * router state) and can't be edited here: the user only clicks the button.
 */
export function ForgotPasswordPage() {
  const email = (useLocation().state as { email?: string } | null)?.email ?? '';
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [emailError, setEmailError] = useState<string | undefined>();
  const [sentMessage, setSentMessage] = useState<string | null>(null);

  // Opened directly (typed URL, bookmark): there's no email to use, so go and get one.
  if (!email) {
    return (
      <Navigate to="/" replace state={{ info: 'Enter your email, then click “Forgot password?”.' }} />
    );
  }

  async function handleSubmit(event: FormEvent) {
    event.preventDefault();
    setBusy(true);
    setError(null);
    setEmailError(undefined);
    try {
      // The answer is the same whether or not the email has an account.
      const response = await api<{ message: string }>('/auth/forgot-password', { body: { email } });
      setSentMessage(response.message);
    } catch (err) {
      setError(errorMessage(err));
      if (err instanceof ApiError) setEmailError(err.fieldErrors.email);
    } finally {
      setBusy(false);
    }
  }

  const backToLogin = <Link to="/" className="font-medium text-brand-700 hover:underline">Back to sign in</Link>;

  if (sentMessage) {
    return (
      <AuthLayout title="Check your email" footer={backToLogin}>
        <div className="space-y-3">
          <Alert tone="success">{sentMessage}</Alert>
          <p className="text-sm text-slate-600">
            The link works once and expires in 30 minutes. Can’t find it? Look in your Spam folder.
          </p>
        </div>
      </AuthLayout>
    );
  }

  return (
    <AuthLayout
      title="Forgot your password?"
      subtitle="We’ll send a link to choose a new password to this email."
      footer={backToLogin}
    >
      <form onSubmit={handleSubmit} noValidate className="space-y-4">
        {error && <Alert tone="error">{error}</Alert>}
        <TextField
          label="Email"
          type="email"
          value={email}
          readOnly // filled in from the sign-in page; to change it, go back to sign in
          error={emailError}
          hint="Wrong email? Go back to sign in and change it there."
        />
        <SubmitButton busy={busy}>Send reset link</SubmitButton>
      </form>
    </AuthLayout>
  );
}
