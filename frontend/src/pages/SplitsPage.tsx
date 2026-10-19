import { useEffect, useRef, useState, type FormEvent } from 'react';
import { Link, useNavigate, useSearchParams } from 'react-router';
import { Users, Plus, ArrowRight } from 'lucide-react';
import { Button, buttonClass } from '../components/Button';
import { Alert, TextField } from '../components/FormParts';
import { ApiError, errorMessage } from '../lib/api';
import { splitApi, type GroupSummary } from '../lib/bill-splits';
import { usePageTitle } from '../lib/usePageTitle';
export function SplitsPage() {
  usePageTitle('Split bills');
  const [groups, setGroups] = useState<GroupSummary[]>([]), [name, setName] = useState(''), [error, setError] = useState('');
  const [fields, setFields] = useState<Record<string, string>>({}), [busy, setBusy] = useState(false), [loading, setLoading] = useState(true);
  const running = useRef(false), navigate = useNavigate();
  const [params] = useSearchParams();
  const source = params.get('document');
  const suffix = source ? `?document=${encodeURIComponent(source)}` : '';
  useEffect(() => {
    let active = true;
    splitApi.list().then(g => {
      if (active)
        setGroups(g);
    }).catch(e => {
      if (active)
        setError(errorMessage(e));
    }).finally(() => {
      if (active)
        setLoading(false);
    });
    return () => {
      active = false;
    };
  }, []);
  async function create(e: FormEvent) {
    e.preventDefault();
    if (running.current)
      return;
    running.current = true;
    setBusy(true);
    setError('');
    setFields({});
    try {
      const g = await splitApi.create(name);
      navigate(`/splits/${g.id}${suffix}`);
    }
    catch (e) {
      setError(errorMessage(e));
      if (e instanceof ApiError)
        setFields(e.fieldErrors);
    }
    finally {
      running.current = false;
      setBusy(false);
    }
  }
  return <div className="mx-auto max-w-5xl space-y-6 p-4 sm:p-6">

    <header>
      <h1 className="flex items-center gap-3 text-2xl font-semibold"><Users aria-hidden className="size-7 text-brand-600" />Split bills</h1>
      <p className="mt-2 text-slate-500">Trips, dinners and shared purchases. Keep track of who paid and who owes.</p>
    </header>

    {source && <Alert tone="info">Choose a group or create one to split your saved bill.</Alert>}

    {error && <Alert tone="error">
      {error}
    </Alert>}

    <form noValidate onSubmit={create} className="rounded-xl border border-slate-200 bg-white p-5">

      <h2 className="mb-4 text-lg font-semibold">Create a group</h2>
      <div className="flex flex-col items-stretch gap-3 sm:flex-row sm:items-end">
        <div className="flex-1">
          <TextField label="Group name" placeholder="e.g. Goa weekend" maxLength={100} value={name} onChange={e => setName(e.target.value)} error={fields.name} />
        </div>
        <Button type="submit" variant="primary" icon={Plus} busy={busy}>Create group</Button>
      </div>

    </form>

    <section aria-label="Your groups" className="grid gap-4 sm:grid-cols-2">

      {loading ? <p role="status">Loading groups…</p> : groups.map(g => <Link key={g.id} to={`/splits/${g.id}${suffix}`} className="flex items-center justify-between gap-4 rounded-xl border border-slate-200 bg-white p-5 hover:border-brand-400">
        <div>
          <h2 className="text-lg font-semibold">
            {g.name}
          </h2>
          <p className="mt-1 text-sm text-slate-500">{g.members} people · {g.owner ? 'You manage this group' : 'Member'}</p>
        </div>
        <ArrowRight className="size-5 shrink-0" aria-hidden />
      </Link>)}

      {!loading && !groups.length && !error && <p className="text-slate-500">No groups yet. Create one, add friends and share their invite links.</p>}

    </section>

    <p className="text-sm text-slate-500">INR only. Payments are recorded manually and confirmed by the recipient. Your private locker stays private.</p>

    {error && !loading && <Link to="/splits" reloadDocument className={buttonClass()}>Reload groups</Link>}

  </div>;
}
