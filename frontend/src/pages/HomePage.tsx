import { LogOut, ReceiptText } from 'lucide-react';
import { Link } from 'react-router';
import { Logo } from '../components/Logo';
import { useAuth } from '../lib/auth-context';

/** The signed-in start page. Lockers, bills and warranties arrive in later tasks. */
export function HomePage() {
  const { user, logout } = useAuth();

  return (
    <div className="min-h-dvh">
      <header className="border-b border-slate-200 bg-white">
        <div className="mx-auto flex h-16 max-w-5xl items-center justify-between px-4">
          <Link to="/" aria-label="Bill Locker home">
            <Logo />
          </Link>
          <button
            type="button"
            onClick={logout}
            className="inline-flex items-center gap-1.5 rounded-lg px-3 py-2 text-sm font-medium text-slate-700 hover:bg-slate-100"
          >
            <LogOut className="size-4" aria-hidden />
            Sign out
          </button>
        </div>
      </header>

      <main className="mx-auto max-w-5xl px-4 py-10">
        <h1 className="text-2xl font-semibold tracking-tight">Hi, {user?.name} 👋</h1>
        <p className="mt-1 text-slate-600">You’re signed in as {user?.email}.</p>

        <div className="mt-8 flex flex-col items-center rounded-2xl border border-dashed border-slate-300 bg-white px-6 py-14 text-center">
          <span className="grid size-12 place-items-center rounded-xl bg-brand-50 text-brand-600">
            <ReceiptText className="size-6" aria-hidden />
          </span>
          <h2 className="mt-4 font-semibold">Your locker is empty</h2>
          <p className="mt-1 max-w-sm text-sm text-slate-600">Uploading bills and warranty cards is the next step we build.</p>
        </div>
      </main>
    </div>
  );
}
