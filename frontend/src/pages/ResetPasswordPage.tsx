import { useState, type FormEvent } from 'react';
import { Link, useNavigate, useSearchParams } from 'react-router';
import { AuthLayout } from '../components/AuthLayout';
import { Alert, PasswordField, PasswordRules, SubmitButton } from '../components/FormParts';
import { api, ApiError, errorMessage } from '../lib/api';

/** Step 2: the page the emailed link opens, /reset-password?token=… */
export function ResetPasswordPage() {
  const token = useSearchParams()[0].get('token');
  const navigate = useNavigate();

  const [password, setPassword] = useState('');
  const [confirm, setConfirm] = useState('');
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [linkExpired, setLinkExpired] = useState(false);
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({});

  const askAgain = <Link to="/forgot-password" className="font-medium text-brand-700 hover:underline">Ask for a new link</Link>;

  if (!token) {
    return (
      <AuthLayout title="Reset link incomplete" footer={askAgain}>
        <Alert tone="error">This link is missing its code. Open the full link from the email, or ask for a new one.</Alert>
      </AuthLayout>
    );
  }

  async function handleSubmit(event: FormEvent) {
    event.preventDefault();
    setError(null);
    setFieldErrors({});
    // "Confirm password" is only a typing check, so the browser does it; the backend never sees it.
    if (password !== confirm) {
      setFieldErrors({ confirm: 'The passwords don’t match' });
      return;
    }
    setBusy(true);
    try {
      const response = await api<{ message: string }>('/auth/reset-password', { body: { token, password } });
      navigate('/login', { replace: true, state: { message: response.message } });
    } catch (err) {
      setError(errorMessage(err));
      if (err instanceof ApiError) {
        setFieldErrors(err.fieldErrors);
        setLinkExpired(err.code === 'INVALID_RESET_TOKEN');
      }
      setBusy(false);
    }
  }

  return (
    <AuthLayout title="Choose a new password" footer={askAgain}>
      <form onSubmit={handleSubmit} noValidate className="space-y-4">
        {error && <Alert tone="error">{error}</Alert>}
        {!linkExpired && (
          <>
            <PasswordField
              label="New password"
              autoComplete="new-password"
              value={password}
              onChange={(e) => setPassword(e.target.value)}
              error={fieldErrors.password}
            />
            <PasswordRules password={password} />
            <PasswordField
              label="Confirm new password"
              autoComplete="new-password"
              value={confirm}
              onChange={(e) => setConfirm(e.target.value)}
              error={fieldErrors.confirm}
            />
            <SubmitButton busy={busy} busyText="Changing password…">Change password</SubmitButton>
          </>
        )}
      </form>
    </AuthLayout>
  );
}
