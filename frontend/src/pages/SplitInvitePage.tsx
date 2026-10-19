import { useEffect, useRef, useState } from 'react';
import { Link, useLocation, useNavigate } from 'react-router';
import { Users } from 'lucide-react';
import { Button, buttonClass } from '../components/Button';
import { Alert } from '../components/FormParts';
import { useAuth } from '../lib/auth-context';
import { errorMessage } from '../lib/api';
import { splitApi, type InvitePreview } from '../lib/bill-splits';
import { usePageTitle } from '../lib/usePageTitle';
export function SplitInvitePage() {
  usePageTitle('Join a bill group');
  const { user, loading } = useAuth(), location = useLocation(), navigate = useNavigate();
  const token = new URLSearchParams(location.hash.slice(1)).get('token') ?? '';
  const [preview, setPreview] = useState<InvitePreview | null>(null), [error, setError] = useState(''), [busy, setBusy] = useState(false);
  const running = useRef(false);
  useEffect(() => {
    let active = true;
    setPreview(null);
    setError('');
    if (user && token)
      splitApi.preview(token).then(p => {
        if (active)
          setPreview(p);
      }).catch(e => {
        if (active)
          setError(errorMessage(e));
      });
    return () => {
      active = false;
    };
  }, [user, token]);
  async function join() {
    if (running.current)
      return;
    running.current = true;
    setBusy(true);
    setError('');
    try {
      const g = await splitApi.accept(token);
      navigate(`/splits/${g.id}`, {
        replace: true
      });
    }
    catch (e) {
      setError(errorMessage(e));
    }
    finally {
      running.current = false;
      setBusy(false);
    }
  }
  return <main className="grid min-h-dvh place-items-center bg-slate-50 p-4">
    <div className="w-full max-w-md space-y-5 rounded-2xl border border-slate-200 bg-white p-7 shadow-sm">
      <Users className="size-10 text-brand-600" aria-hidden />
      <h1 className="text-2xl font-semibold">Join a bill group</h1>

      {loading ? <p role="status">Loading…</p> : !token ? <Alert tone="error">This invitation is incomplete. Ask the group owner for a new link.</Alert> : !user ? <><p>Sign in or create an account to review this invitation. Joining shares the group ledger with you.</p><div className="flex flex-wrap gap-3">
        <Link to="/login" state={{
          from: location.pathname + location.hash
        }} className={buttonClass({
          variant: 'primary'
        })}>Sign in</Link>
        <Link to="/register" state={{
          from: location.pathname + location.hash
        }} className={buttonClass()}>Create account</Link>
      </div></> : <>
        <p className="text-sm text-slate-500">Signed in as {user.name} ({user.email})</p>
        {preview ? <><p>Join <strong>
          {preview.groupName}
        </strong> as <strong>
            {preview.memberName}
          </strong>?</p><p className="text-sm text-slate-500">Only accept if this is your name. The group owner controls expenses. Your private bills and files remain private.</p><p className="text-xs text-slate-500">Expires {new Date(preview.expiresAt).toLocaleString()}</p><Button variant="primary" onClick={join} busy={busy}>Accept invitation</Button></> : !error && <p role="status">Checking invitation…</p>}
      </>}
      {error && <Alert tone="error">
        {error}
      </Alert>}
      <Link to={user ? '/splits' : '/login'} className="block text-sm text-brand-700 hover:underline">Back to Bill Locker</Link>

    </div>
  </main>;
}
