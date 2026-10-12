import { KeyRound, LogOut, Mail, UserRound, type LucideIcon } from 'lucide-react';
import { useEffect, useState, type FormEvent, type ReactNode } from 'react';
import { Link } from 'react-router';
import { Button, buttonClass } from '../components/Button';
import { PasswordField, PasswordRules, TextField } from '../components/FormParts';
import { Avatar } from '../components/UserMenu';
import { changePassword, updateProfile } from '../lib/account';
import { ApiError, errorMessage } from '../lib/api';
import { useAuth } from '../lib/auth-context';
import { useFeedback } from '../lib/feedback-context';
import { getGmail, timeAgo, type GmailOverview } from '../lib/gmail';
import { usePageTitle } from '../lib/usePageTitle';

/** /settings: your profile, your password, connected Gmail addresses, sign out. */
export function SettingsPage() {
  usePageTitle('Settings');
  const { user, logout } = useAuth();
  if (!user) return null;

  return (
    <main className="mx-auto max-w-3xl px-4 py-8">
      <h1 className="text-2xl font-semibold tracking-tight">Settings</h1>
      <p className="mt-1 text-slate-600">Your account and the inboxes Bill Locker looks in.</p>

      <div className="mt-6 space-y-6">
        <ProfileSection />
        <PasswordSection />
        <GmailSection />
        <Section icon={LogOut} title="Sign out" description="Signs you out in this browser.">
          <Button icon={LogOut} variant="dangerGhost" onClick={logout} className="-ml-3">
            Sign out
          </Button>
        </Section>
      </div>
    </main>
  );
}

function ProfileSection() {
  const { user, updateUser } = useAuth();
  const { toast } = useFeedback();
  const [name, setName] = useState(user?.name ?? '');
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | undefined>();
  if (!user) return null;
  const changed = name.trim() !== user.name;

  async function save(event: FormEvent) {
    event.preventDefault();
    setBusy(true);
    setError(undefined);
    try {
      const updated = await updateProfile(name);
      updateUser(updated); // the top bar and the greeting show the new name straight away
      setName(updated.name);
      toast('Your name has been saved.');
    } catch (e) {
      setError(e instanceof ApiError ? (e.fieldErrors.name ?? e.message) : errorMessage(e));
    } finally {
      setBusy(false);
    }
  }

  return (
    <Section icon={UserRound} title="Profile" description="How Bill Locker greets you.">
      <div className="mb-4 flex items-center gap-3">
        <Avatar name={name.trim() || user.name} />
        <div className="min-w-0 text-sm">
          <p className="truncate font-medium">{name.trim() || user.name}</p>
          <p className="truncate text-slate-500">{user.email}</p>
        </div>
      </div>
      <form onSubmit={save} noValidate className="space-y-4">
        <TextField label="Your name" autoComplete="name" value={name} onChange={(e) => setName(e.target.value)} error={error} />
        <TextField label="Email" value={user.email} readOnly hint="Your sign-in email can’t be changed yet." />
        <Button type="submit" variant="primary" busy={busy} disabled={!changed}>
          {busy ? 'Saving…' : 'Save name'}
        </Button>
      </form>
    </Section>
  );
}

function PasswordSection() {
  const { toast } = useFeedback();
  const [current, setCurrent] = useState('');
  const [next, setNext] = useState('');
  const [busy, setBusy] = useState(false);
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({});

  async function save(event: FormEvent) {
    event.preventDefault();
    setBusy(true);
    setFieldErrors({});
    try {
      const { message } = await changePassword(current, next);
      setCurrent('');
      setNext('');
      toast(message);
    } catch (e) {
      if (e instanceof ApiError && Object.keys(e.fieldErrors).length > 0) setFieldErrors(e.fieldErrors);
      else toast(errorMessage(e), 'error');
    } finally {
      setBusy(false);
    }
  }

  return (
    <Section icon={KeyRound} title="Password" description="You’ll need your current password to choose a new one.">
      <form onSubmit={save} noValidate className="space-y-4">
        <PasswordField
          label="Current password"
          autoComplete="current-password"
          value={current}
          onChange={(e) => setCurrent(e.target.value)}
          error={fieldErrors.currentPassword}
        />
        <PasswordField
          label="New password"
          autoComplete="new-password"
          value={next}
          onChange={(e) => setNext(e.target.value)}
          error={fieldErrors.newPassword}
        />
        <PasswordRules password={next} />
        <Button type="submit" variant="primary" busy={busy} disabled={!current || !next}>
          {busy ? 'Changing…' : 'Change password'}
        </Button>
      </form>
    </Section>
  );
}

/** Which inboxes are connected; adding and removing them stays on the Gmail page. */
function GmailSection() {
  const [gmail, setGmail] = useState<GmailOverview | null | 'error'>(null);
  useEffect(() => {
    getGmail().then(setGmail, () => setGmail('error'));
  }, []);

  return (
    <Section icon={Mail} title="Connected Gmail" description="Inboxes Bill Locker looks in for bills (read-only).">
      {gmail === null ? (
        <p className="text-sm text-slate-500">Loading…</p>
      ) : gmail === 'error' ? (
        <p className="text-sm text-slate-500">Couldn’t load your Gmail accounts right now.</p>
      ) : !gmail.configured ? (
        <p className="text-sm text-slate-500">Gmail import isn’t available right now.</p>
      ) : (
        <>
          {gmail.accounts.length === 0 ? (
            <p className="text-sm text-slate-600">No Gmail address connected yet.</p>
          ) : (
            <ul className="divide-y divide-slate-100 rounded-lg ring-1 ring-slate-200">
              {gmail.accounts.map((a) => (
                <li key={a.id} className="flex items-center justify-between gap-3 px-3 py-2.5 text-sm">
                  <span className="truncate font-medium">{a.email}</span>
                  <span className="shrink-0 text-slate-500">
                    {a.lastScannedAt ? `Scanned ${timeAgo(a.lastScannedAt)}` : 'Not scanned yet'}
                  </span>
                </li>
              ))}
            </ul>
          )}
          <Link to="/gmail" className={`${buttonClass()} mt-4`}>
            <Mail className="size-4" aria-hidden />
            {gmail.accounts.length === 0 ? 'Connect Gmail' : 'Manage on the Gmail page'}
          </Link>
        </>
      )}
    </Section>
  );
}

function Section(props: { icon: LucideIcon; title: string; description: string; children: ReactNode }) {
  const Icon = props.icon;
  return (
    <section className="rounded-2xl border border-slate-200 bg-white shadow-xs">
      <header className="flex items-start gap-3 border-b border-slate-100 px-5 py-4">
        <span className="grid size-9 shrink-0 place-items-center rounded-lg bg-brand-50 text-brand-600">
          <Icon className="size-4.5" aria-hidden />
        </span>
        <div>
          <h2 className="font-semibold">{props.title}</h2>
          <p className="text-sm text-slate-500">{props.description}</p>
        </div>
      </header>
      <div className="p-5">{props.children}</div>
    </section>
  );
}
