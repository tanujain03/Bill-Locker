import { zodResolver } from '@hookform/resolvers/zod';
import { useMutation, useQueryClient } from '@tanstack/react-query';
import {
  BrainCircuit,
  EyeOff,
  LockKeyhole,
  LogOut,
  Mail,
  RotateCcw,
  ShieldCheck,
  UserRound,
  type LucideIcon,
} from 'lucide-react';
import { useEffect } from 'react';
import { useForm } from 'react-hook-form';
import { useNavigate } from 'react-router';
import { z } from 'zod';
import { Badge } from '@/components/ui/Badge';
import { Button, ButtonLink } from '@/components/ui/Button';
import { Card, CardBody, CardHeader } from '@/components/ui/Card';
import { Field, Input } from '@/components/ui/Field';
import { Avatar } from '@/components/ui/misc';
import { PageHeader } from '@/components/ui/PageHeader';
import { useToast } from '@/components/ui/toast-context';
import { useDocumentTitle } from '@/hooks/useDocumentTitle';
import { useGmailConnection } from '@/hooks/useGmail';
import { apiClient, getErrorMessage } from '@/lib/api-client';
import { useAuth } from '@/lib/auth-context';
import { config } from '@/lib/config';
import { isFeatureEnabled, type Feature } from '@/lib/features';
import { authService } from '@/services/auth.service';
import { formatDate } from '@/utils/format';

const profileSchema = z.object({
  name: z.string().trim().min(2, 'Enter your name').max(80, 'Keep it under 80 characters'),
});

type ProfileValues = z.infer<typeof profileSchema>;

const PRIVACY_POINTS: { icon: LucideIcon; text: string; feature?: Feature }[] = [
  { icon: LockKeyhole, text: 'Your documents are private to your account — every request is checked against your identity.' },
  { icon: EyeOff, text: 'Gmail access is read-only and can be revoked at any time.', feature: 'gmail' },
  { icon: BrainCircuit, text: 'AI suggestions are never saved without your confirmation, and your data is not used to train models.' },
];

export function ProfilePage() {
  useDocumentTitle('Settings');
  const { user, setUser, logout } = useAuth();
  const navigate = useNavigate();
  const toast = useToast();

  const updateProfile = useMutation({ mutationFn: authService.updateProfile, onSuccess: setUser });

  const {
    register,
    handleSubmit,
    reset,
    formState: { errors, isSubmitting, isDirty },
  } = useForm<ProfileValues>({ resolver: zodResolver(profileSchema), defaultValues: { name: user?.name ?? '' } });

  useEffect(() => {
    if (user) reset({ name: user.name });
  }, [user, reset]);

  const onSubmit = handleSubmit(async (values) => {
    try {
      await updateProfile.mutateAsync(values);
      toast.success('Profile updated');
    } catch (error) {
      toast.error('Could not update your profile', getErrorMessage(error));
    }
  });

  function signOut() {
    navigate('/login', { replace: true });
    logout();
  }

  return (
    <div className="mx-auto max-w-3xl">
      <PageHeader title="Settings" description="Your profile, connected accounts and privacy." />

      <div className="space-y-6">
        <Card>
          <CardHeader icon={<UserRound className="size-4 text-slate-400" aria-hidden />} title="Profile" />
          <CardBody>
            <div className="mb-6 flex items-center gap-4">
              <Avatar name={user?.name} className="size-14 text-lg" />
              <div className="min-w-0">
                <p className="truncate font-semibold text-slate-900">{user?.name}</p>
                <p className="truncate text-sm text-slate-500">{user?.email}</p>
                {user?.createdAt && <p className="text-xs text-slate-400">Member since {formatDate(user.createdAt)}</p>}
              </div>
            </div>
            <form onSubmit={onSubmit} noValidate className="grid gap-4 sm:grid-cols-2">
              <Field label="Full name" error={errors.name?.message}>
                <Input autoComplete="name" {...register('name')} />
              </Field>
              <Field label="Email" hint="Email changes are not supported yet.">
                <Input value={user?.email ?? ''} readOnly />
              </Field>
              <div className="sm:col-span-2">
                <Button type="submit" loading={isSubmitting} disabled={!isDirty}>
                  Save changes
                </Button>
              </div>
            </form>
          </CardBody>
        </Card>

        {isFeatureEnabled('gmail') && <ConnectedAccountsCard />}

        <Card>
          <CardHeader icon={<ShieldCheck className="size-4 text-slate-400" aria-hidden />} title="Privacy & security" />
          <CardBody>
            <ul className="space-y-3">
              {PRIVACY_POINTS.filter((point) => !point.feature || isFeatureEnabled(point.feature)).map(({ icon: Icon, text }) => (
                <li key={text} className="flex gap-3 text-sm text-slate-600">
                  <Icon className="mt-0.5 size-4 shrink-0 text-emerald-600" aria-hidden />
                  {text}
                </li>
              ))}
            </ul>
          </CardBody>
        </Card>

        {config.apiMocking && <DemoDataCard />}

        <Card className="flex flex-col items-start justify-between gap-4 p-5 sm:flex-row sm:items-center sm:p-6">
          <div>
            <p className="font-semibold text-slate-900">Sign out</p>
            <p className="text-sm text-slate-500">End your session on this device.</p>
          </div>
          <Button variant="secondary" onClick={signOut} leftIcon={<LogOut className="size-4" aria-hidden />}>
            Sign out
          </Button>
        </Card>
      </div>
    </div>
  );
}

function ConnectedAccountsCard() {
  const gmail = useGmailConnection();

  return (
    <Card>
      <CardHeader
        icon={<Mail className="size-4 text-slate-400" aria-hidden />}
        title="Connected accounts"
        description="Import bills automatically from your inbox."
      />
      <CardBody>
        <div className="flex flex-col gap-4 rounded-xl border border-slate-200 p-4 sm:flex-row sm:items-center">
          <span className="flex size-10 shrink-0 items-center justify-center rounded-xl bg-rose-50 text-rose-600">
            <Mail className="size-5" aria-hidden />
          </span>
          <div className="min-w-0 flex-1">
            <p className="flex items-center gap-2 text-sm font-semibold text-slate-900">
              Gmail
              {gmail.data?.connected ? <Badge tone="success" size="sm">Connected</Badge> : <Badge size="sm">Not connected</Badge>}
            </p>
            <p className="truncate text-sm text-slate-500">
              {gmail.data?.connected ? gmail.data.email : 'Let AI find invoices and warranty cards in your inbox.'}
            </p>
          </div>
          <ButtonLink to="/gmail" variant="secondary" size="sm">
            {gmail.data?.connected ? 'Manage' : 'Connect'}
          </ButtonLink>
        </div>
      </CardBody>
    </Card>
  );
}

/** Mock-API only: restores the seeded demo data (handy between demo rehearsals). */
function DemoDataCard() {
  const queryClient = useQueryClient();
  const toast = useToast();
  const reset = useMutation({
    mutationFn: () => apiClient.post('/__mock/reset'),
    onSuccess: async () => {
      await queryClient.invalidateQueries();
      toast.success('Demo data restored');
    },
    onError: (error) => toast.error('Could not reset demo data', getErrorMessage(error)),
  });

  return (
    <Card className="flex flex-col items-start justify-between gap-4 border-dashed p-5 sm:flex-row sm:items-center sm:p-6">
      <div>
        <p className="font-semibold text-slate-900">Demo data</p>
        <p className="text-sm text-slate-500">
          The app is running on the in-browser mock API. Restore the seeded demo products, documents and emails.
        </p>
      </div>
      <Button variant="secondary" onClick={() => reset.mutate()} loading={reset.isPending} leftIcon={<RotateCcw className="size-4" aria-hidden />}>
        Reset demo data
      </Button>
    </Card>
  );
}
