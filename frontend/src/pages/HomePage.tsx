import { ReceiptText } from 'lucide-react';
import { Link } from 'react-router';
import { AppHeader } from '../components/AppHeader';
import { useAuth } from '../lib/auth-context';

/** The signed-in start page. The dashboard arrives in a later task. */
export function HomePage() {
  const { user } = useAuth();

  return (
    <div className="min-h-dvh">
      <AppHeader />

      <main className="mx-auto max-w-6xl px-4 py-10">
        <h1 className="text-2xl font-semibold tracking-tight">Hi, {user?.name} 👋</h1>
        <p className="mt-1 text-slate-600">You’re signed in as {user?.email}.</p>

        <div className="mt-8 flex flex-col items-center rounded-2xl border border-dashed border-slate-300 bg-white px-6 py-14 text-center">
          <span className="grid size-12 place-items-center rounded-xl bg-brand-50 text-brand-600">
            <ReceiptText className="size-6" aria-hidden />
          </span>
          <h2 className="mt-4 font-semibold">Keep your bills in one place</h2>
          <p className="mt-1 max-w-sm text-sm text-slate-600">
            Upload an invoice or warranty card and we’ll read the details for you.
          </p>
          <Link
            to="/documents"
            className="mt-4 rounded-lg bg-brand-600 px-4 py-2 text-sm font-semibold text-white hover:bg-brand-700"
          >
            Go to documents
          </Link>
        </div>
      </main>
    </div>
  );
}
