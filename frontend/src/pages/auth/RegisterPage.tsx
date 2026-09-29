import { zodResolver } from '@hookform/resolvers/zod';
import { useState } from 'react';
import { useForm } from 'react-hook-form';
import { Link } from 'react-router';
import { z } from 'zod';
import { Alert } from '@/components/ui/Alert';
import { Button } from '@/components/ui/Button';
import { Field, Input } from '@/components/ui/Field';
import { PasswordInput } from '@/components/ui/PasswordInput';
import { useToast } from '@/components/ui/toast-context';
import { useDocumentTitle } from '@/hooks/useDocumentTitle';
import { getErrorMessage } from '@/lib/api-client';
import { useAuth } from '@/lib/auth-context';
import { applyServerFieldErrors } from '@/lib/form-errors';

const registerSchema = z
  .object({
    name: z.string().trim().min(2, 'Enter your name').max(80, 'Keep it under 80 characters'),
    email: z.string().trim().min(1, 'Email is required').email('Enter a valid email address').max(254),
    password: z
      .string()
      .min(8, 'Use at least 8 characters')
      .max(128, 'Use at most 128 characters')
      .regex(/[A-Za-z]/, 'Include at least one letter')
      .regex(/\d/, 'Include at least one number'),
    confirmPassword: z.string().min(1, 'Confirm your password'),
  })
  .refine((values) => values.password === values.confirmPassword, {
    path: ['confirmPassword'],
    message: 'Passwords do not match',
  });

type RegisterValues = z.infer<typeof registerSchema>;

export function RegisterPage() {
  useDocumentTitle('Create account');
  const { register: registerAccount } = useAuth();
  const toast = useToast();
  const [serverError, setServerError] = useState<string | null>(null);

  const {
    register,
    handleSubmit,
    setError,
    formState: { errors, isSubmitting },
  } = useForm<RegisterValues>({
    resolver: zodResolver(registerSchema),
    defaultValues: { name: '', email: '', password: '', confirmPassword: '' },
  });

  const onSubmit = handleSubmit(async ({ name, email, password }) => {
    setServerError(null);
    try {
      const user = await registerAccount({ name, email, password });
      toast.success(`Welcome to Bill Locker, ${user.name.split(' ')[0]}!`, 'Upload your first bill to get started.');
    } catch (error) {
      if (!applyServerFieldErrors(error, setError, ['name', 'email', 'password'])) {
        setServerError(getErrorMessage(error, 'Could not create your account. Please try again.'));
      }
    }
  });

  return (
    <div>
      <h1 className="text-2xl font-semibold tracking-tight text-slate-900">Create your locker</h1>
      <p className="mt-1 text-sm text-slate-500">Free, private, and ready in under a minute.</p>

      {serverError && (
        <Alert tone="danger" className="mt-6">
          {serverError}
        </Alert>
      )}

      <form onSubmit={onSubmit} noValidate className="mt-6 space-y-5">
        <Field label="Full name" error={errors.name?.message}>
          <Input autoComplete="name" placeholder="e.g. Asha Verma" {...register('name')} />
        </Field>
        <Field label="Email" error={errors.email?.message}>
          <Input type="email" autoComplete="email" placeholder="you@example.com" {...register('email')} />
        </Field>
        <Field label="Password" error={errors.password?.message} hint="At least 8 characters, with a letter and a number.">
          <PasswordInput autoComplete="new-password" {...register('password')} />
        </Field>
        <Field label="Confirm password" error={errors.confirmPassword?.message}>
          <PasswordInput autoComplete="new-password" {...register('confirmPassword')} />
        </Field>
        <Button type="submit" size="lg" className="w-full" loading={isSubmitting}>
          Create account
        </Button>
      </form>

      <p className="mt-8 text-center text-sm text-slate-500">
        Already have an account?{' '}
        <Link to="/login" className="font-semibold text-brand-700 hover:text-brand-800">
          Sign in
        </Link>
      </p>
    </div>
  );
}
