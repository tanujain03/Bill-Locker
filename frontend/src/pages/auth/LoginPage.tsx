import { zodResolver } from '@hookform/resolvers/zod';
import { Sparkles } from 'lucide-react';
import { useState } from 'react';
import { useForm } from 'react-hook-form';
import { Link } from 'react-router';
import { z } from 'zod';
import { Alert } from '@/components/ui/Alert';
import { Button } from '@/components/ui/Button';
import { Field, Input } from '@/components/ui/Field';
import { PasswordInput } from '@/components/ui/PasswordInput';
import { useDocumentTitle } from '@/hooks/useDocumentTitle';
import { getErrorMessage } from '@/lib/api-client';
import { useAuth } from '@/lib/auth-context';
import { config, DEMO_CREDENTIALS } from '@/lib/config';

const loginSchema = z.object({
  email: z.string().trim().min(1, 'Email is required').email('Enter a valid email address'),
  password: z.string().min(1, 'Password is required'),
});

type LoginValues = z.infer<typeof loginSchema>;

export function LoginPage() {
  useDocumentTitle('Sign in');
  const { login, sessionExpired } = useAuth();
  const [serverError, setServerError] = useState<string | null>(null);

  const {
    register,
    handleSubmit,
    setValue,
    formState: { errors, isSubmitting },
  } = useForm<LoginValues>({
    resolver: zodResolver(loginSchema),
    defaultValues: { email: '', password: '' },
  });

  // On success the auth state flips and <PublicOnlyRoute> redirects to where the user was heading.
  const onSubmit = handleSubmit(async (values) => {
    setServerError(null);
    try {
      await login(values);
    } catch (error) {
      setServerError(getErrorMessage(error, 'Could not sign you in. Please try again.'));
    }
  });

  function signInWithDemo() {
    setValue('email', DEMO_CREDENTIALS.email, { shouldValidate: true });
    setValue('password', DEMO_CREDENTIALS.password, { shouldValidate: true });
    void onSubmit();
  }

  return (
    <div>
      <h1 className="text-2xl font-semibold tracking-tight text-slate-900">Welcome back</h1>
      <p className="mt-1 text-sm text-slate-500">Sign in to open your locker.</p>

      <div className="mt-6 space-y-3">
        {sessionExpired && <Alert tone="info">Your session expired. Please sign in again.</Alert>}
        {serverError && <Alert tone="danger">{serverError}</Alert>}
      </div>

      <form onSubmit={onSubmit} noValidate className="mt-6 space-y-5">
        <Field label="Email" error={errors.email?.message}>
          <Input type="email" autoComplete="email" placeholder="you@example.com" {...register('email')} />
        </Field>
        <Field label="Password" error={errors.password?.message}>
          <PasswordInput autoComplete="current-password" placeholder="Your password" {...register('password')} />
        </Field>
        <Button type="submit" size="lg" className="w-full" loading={isSubmitting}>
          Sign in
        </Button>
      </form>

      {config.showDemoLogin && (
        <div className="mt-6">
          <div className="relative text-center text-xs text-slate-400">
            <span className="absolute inset-x-0 top-1/2 h-px bg-slate-200" aria-hidden />
            <span className="relative bg-white px-3">or</span>
          </div>
          <Button
            variant="subtle"
            size="lg"
            className="mt-6 w-full"
            onClick={signInWithDemo}
            disabled={isSubmitting}
            leftIcon={<Sparkles className="size-4" aria-hidden />}
          >
            Explore with the demo account
          </Button>
        </div>
      )}

      <p className="mt-8 text-center text-sm text-slate-500">
        New to Bill Locker?{' '}
        <Link to="/register" className="font-semibold text-brand-700 hover:text-brand-800">
          Create an account
        </Link>
      </p>
    </div>
  );
}
