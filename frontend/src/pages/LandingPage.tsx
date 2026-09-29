import {
  ArrowRight,
  BellRing,
  CheckCheck,
  FileSearch,
  LockKeyhole,
  Mail,
  MessageSquare,
  ScanText,
  ShieldAlert,
  ShieldCheck,
  Sparkles,
  Upload,
} from 'lucide-react';
import { Link } from 'react-router';
import { ButtonLink } from '@/components/ui/Button';
import { Logo } from '@/components/ui/Logo';
import { useDocumentTitle } from '@/hooks/useDocumentTitle';
import { useAuth } from '@/lib/auth-context';

const FEATURES = [
  { icon: ScanText, title: 'AI reads every bill', text: 'OCR + AI extract product, price, dates, seller and warranty from photos and PDFs.' },
  { icon: Mail, title: 'Gmail import', text: 'Connect your inbox and let AI find invoices and warranty cards — no manual uploads.' },
  { icon: ShieldCheck, title: 'Warranty tracking', text: 'Expiry dates are calculated precisely and every product shows its warranty status.' },
  { icon: BellRing, title: 'Smart reminders', text: 'Get notified before a warranty expires or a service is due.' },
  { icon: MessageSquare, title: 'Ask your documents', text: '“Is my laptop still under warranty?” — answers grounded in your own bills.' },
  { icon: LockKeyhole, title: 'Private by design', text: 'Your locker is yours alone. AI never saves anything without your confirmation.' },
];

const STEPS = [
  { icon: Upload, title: 'Upload or import', text: 'Snap a photo, drop a PDF or connect Gmail.' },
  { icon: FileSearch, title: 'Review what AI found', text: 'Every field is shown with its confidence. Missing data is flagged, never guessed.' },
  { icon: CheckCheck, title: 'Relax', text: 'Bill Locker tracks warranties and services, and reminds you in time.' },
];

export function LandingPage() {
  useDocumentTitle(null);
  const { status } = useAuth();
  const signedIn = status === 'authenticated';

  return (
    <div className="min-h-dvh bg-white">
      <header className="sticky top-0 z-30 border-b border-slate-100 bg-white/85 backdrop-blur">
        <div className="mx-auto flex h-16 max-w-6xl items-center justify-between px-4 sm:px-6">
          <Link to="/" aria-label="Bill Locker home">
            <Logo />
          </Link>
          <nav className="flex items-center gap-2" aria-label="Account">
            {signedIn ? (
              <ButtonLink to="/dashboard" rightIcon={<ArrowRight className="size-4" aria-hidden />}>
                Open my locker
              </ButtonLink>
            ) : (
              <>
                <ButtonLink to="/login" variant="ghost">
                  Sign in
                </ButtonLink>
                <ButtonLink to="/register">Get started</ButtonLink>
              </>
            )}
          </nav>
        </div>
      </header>

      <main>
        <section className="relative overflow-hidden">
          <div
            className="pointer-events-none absolute inset-x-0 top-0 h-[520px] bg-gradient-to-b from-brand-50 to-white"
            aria-hidden
          />
          <div className="relative mx-auto grid max-w-6xl items-center gap-12 px-4 pt-14 pb-20 sm:px-6 lg:grid-cols-2 lg:pt-24">
            <div>
              <span className="inline-flex items-center gap-1.5 rounded-full bg-white px-3 py-1 text-xs font-semibold text-brand-700 shadow-card ring-1 ring-brand-100">
                <Sparkles className="size-3.5" aria-hidden />
                AI-powered purchase locker
              </span>
              <h1 className="mt-5 text-4xl font-semibold tracking-tight text-balance text-slate-900 sm:text-5xl">
                Never lose a bill.
                <br />
                <span className="text-brand-600">Never miss a warranty.</span>
              </h1>
              <p className="mt-5 max-w-xl text-lg text-slate-600">
                Bill Locker keeps every invoice, warranty card and service receipt in one place. AI reads them, tracks
                your warranties and reminds you before they expire.
              </p>
              <div className="mt-8 flex flex-wrap gap-3">
                <ButtonLink to={signedIn ? '/dashboard' : '/register'} size="lg" rightIcon={<ArrowRight className="size-4" aria-hidden />}>
                  {signedIn ? 'Open my locker' : 'Create your free locker'}
                </ButtonLink>
                {!signedIn && (
                  <ButtonLink to="/login" size="lg" variant="secondary">
                    Try the demo
                  </ButtonLink>
                )}
              </div>
            </div>

            <HeroPreview />
          </div>
        </section>

        <section className="mx-auto max-w-6xl px-4 py-16 sm:px-6" aria-labelledby="features-heading">
          <h2 id="features-heading" className="text-center text-2xl font-semibold tracking-tight text-slate-900 sm:text-3xl">
            Everything about your purchases, organised
          </h2>
          <p className="mx-auto mt-3 max-w-2xl text-center text-slate-600">
            Bills scattered across email, WhatsApp, your gallery and a drawer? Bring them together once.
          </p>
          <div className="mt-12 grid gap-5 sm:grid-cols-2 lg:grid-cols-3">
            {FEATURES.map(({ icon: Icon, title, text }) => (
              <div key={title} className="rounded-2xl border border-slate-200/80 bg-white p-6 shadow-card">
                <span className="flex size-10 items-center justify-center rounded-xl bg-brand-50 text-brand-600">
                  <Icon className="size-5" aria-hidden />
                </span>
                <h3 className="mt-4 font-semibold text-slate-900">{title}</h3>
                <p className="mt-1.5 text-sm text-slate-600">{text}</p>
              </div>
            ))}
          </div>
        </section>

        <section className="bg-slate-50 py-16" aria-labelledby="how-heading">
          <div className="mx-auto max-w-6xl px-4 sm:px-6">
            <h2 id="how-heading" className="text-center text-2xl font-semibold tracking-tight text-slate-900 sm:text-3xl">
              How it works
            </h2>
            <ol className="mt-12 grid gap-6 md:grid-cols-3">
              {STEPS.map(({ icon: Icon, title, text }, index) => (
                <li key={title} className="relative rounded-2xl bg-white p-6 shadow-card">
                  <span className="text-xs font-semibold tracking-wider text-brand-600 uppercase">Step {index + 1}</span>
                  <Icon className="mt-3 size-6 text-slate-700" aria-hidden />
                  <h3 className="mt-3 font-semibold text-slate-900">{title}</h3>
                  <p className="mt-1.5 text-sm text-slate-600">{text}</p>
                </li>
              ))}
            </ol>
          </div>
        </section>

        <section className="mx-auto max-w-6xl px-4 py-16 sm:px-6">
          <div className="flex flex-col items-center gap-6 rounded-3xl bg-brand-950 px-6 py-12 text-center text-white sm:px-12">
            <h2 className="max-w-2xl text-2xl font-semibold tracking-tight text-balance sm:text-3xl">
              Start with the bill in your inbox. It takes a minute.
            </h2>
            <ButtonLink to={signedIn ? '/dashboard' : '/register'} size="lg" variant="secondary" className="border-0">
              {signedIn ? 'Open my locker' : 'Get started — it’s free'}
            </ButtonLink>
          </div>
        </section>
      </main>

      <footer className="border-t border-slate-100">
        <div className="mx-auto flex max-w-6xl flex-col items-center justify-between gap-3 px-4 py-8 text-sm text-slate-500 sm:flex-row sm:px-6">
          <Logo />
          <p>Never Lose a Bill. Never Miss a Warranty.</p>
        </div>
      </footer>
    </div>
  );
}

/** Static product illustration built from real UI pieces (decorative). */
function HeroPreview() {
  const rows = [
    { name: 'Sony Bravia 55" 4K TV', meta: 'Expires in 1 yr 6 mo', tone: 'bg-emerald-50 text-emerald-700 ring-emerald-600/20', label: 'Active', fill: 'w-[25%] bg-emerald-500', track: 'bg-emerald-100' },
    { name: 'Dell Inspiron 15 laptop', meta: 'Expires in 25 days', tone: 'bg-amber-50 text-amber-800 ring-amber-600/25', label: 'Expiring soon', fill: 'w-[93%] bg-amber-500', track: 'bg-amber-100' },
    { name: 'LG front-load washer', meta: 'Expired 50 days ago', tone: 'bg-rose-50 text-rose-700 ring-rose-600/20', label: 'Expired', fill: 'w-full bg-rose-500', track: 'bg-rose-100' },
  ];

  return (
    <div className="relative mx-auto w-full max-w-md lg:max-w-none" aria-hidden>
      <div className="rounded-3xl border border-slate-200/80 bg-white p-5 shadow-elevated sm:p-6">
        <div className="flex items-center justify-between">
          <p className="text-sm font-semibold text-slate-900">Warranties</p>
          <span className="text-xs text-slate-400">8 products tracked</span>
        </div>
        <ul className="mt-4 space-y-3">
          {rows.map((row) => (
            <li key={row.name} className="rounded-2xl border border-slate-100 p-4">
              <div className="flex items-center justify-between gap-3">
                <p className="truncate text-sm font-medium text-slate-900">{row.name}</p>
                <span className={`rounded-full px-2 py-0.5 text-[11px] font-medium ring-1 ring-inset ${row.tone}`}>{row.label}</span>
              </div>
              <p className="mt-1 text-xs text-slate-500">{row.meta}</p>
              <div className={`mt-3 h-1.5 rounded-full ${row.track}`}>
                <div className={`h-full rounded-full ${row.fill}`} />
              </div>
            </li>
          ))}
        </ul>
      </div>
      <div className="absolute -bottom-20 -left-6 hidden w-64 rounded-2xl border border-slate-200/80 bg-white p-4 shadow-elevated sm:block">
        <div className="flex items-center gap-2 text-xs font-semibold text-brand-700">
          <Sparkles className="size-3.5" />
          AI Assistant
        </div>
        <p className="mt-2 text-sm text-slate-700">
          Your Dell laptop warranty is active until <strong className="font-semibold">23 Oct 2026</strong> — 25 days left.
        </p>
      </div>
      <div className="absolute -top-5 -right-3 hidden items-center gap-2 rounded-xl bg-white px-3 py-2 text-xs font-medium text-slate-700 shadow-elevated sm:flex">
        <ShieldAlert className="size-4 text-amber-500" />
        Reminder: 25 days left
      </div>
    </div>
  );
}
