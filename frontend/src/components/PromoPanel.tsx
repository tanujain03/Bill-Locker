import { ArrowRight, BellRing, ShieldCheck, ShieldAlert, ShieldX } from 'lucide-react';
import { Link } from 'react-router';

/** Right half of the sign-in screens: what Bill Locker does, with a picture of it. */
export function PromoPanel() {
  return (
    <aside className="relative hidden w-3/5 flex-col overflow-hidden bg-white px-14 pt-12 lg:flex">
      <h2 className="text-5xl leading-tight font-bold tracking-tight text-brand-950">
        Never lose a bill.
        <br />
        Never miss a warranty.
      </h2>
      <p className="mt-5 text-xl text-slate-700">
        Keep every invoice and warranty card in one locker — and get reminded before cover runs out.
      </p>
      <Link
        to="/register"
        className="mt-6 inline-flex shrink-0 items-center justify-center gap-2 rounded-full bg-brand-600 py-3.5 text-lg font-semibold text-white shadow-lg shadow-brand-600/20 hover:bg-brand-700"
      >
        Create your free locker
        <ArrowRight className="size-5" aria-hidden />
      </Link>

      {/* Decorative picture of the app (made of HTML, not an image file). It fills the
          space that's left and is cut off at the bottom of the screen. */}
      <div className="relative mt-12 min-h-0 flex-1" aria-hidden>
        <div className="absolute inset-x-0 bottom-0 h-3/4 bg-gradient-to-t from-brand-100 via-brand-50 to-transparent" />
        <div className="relative mx-auto w-[85%] max-w-lg -rotate-3 rounded-2xl bg-gradient-to-br from-brand-500 to-violet-500 p-4 shadow-2xl">
          <p className="px-2 pb-3 text-lg font-bold text-white">My warranties</p>
          <div className="space-y-3">
            <Row icon={ShieldCheck} name="Sony Bravia 55″ TV" meta="Covered until Mar 2028" tone="bg-emerald-100 text-emerald-700" label="Active" />
            <Row icon={ShieldAlert} name="Dell Inspiron laptop" meta="25 days left" tone="bg-amber-100 text-amber-800" label="Expiring soon" />
            <Row icon={ShieldX} name="LG washing machine" meta="Ended Aug 2026" tone="bg-rose-100 text-rose-700" label="Expired" />
          </div>
        </div>
        <div className="absolute top-[-1.5rem] right-6 flex rotate-2 items-center gap-2 rounded-xl bg-white px-4 py-3 text-sm font-medium text-slate-700 shadow-xl">
          <BellRing className="size-4 text-amber-500" />
          Reminder: laptop warranty ends in 25 days
        </div>
      </div>
    </aside>
  );
}

function Row({ icon: Icon, name, meta, tone, label }: {
  icon: typeof ShieldCheck;
  name: string;
  meta: string;
  tone: string;
  label: string;
}) {
  return (
    <div className="flex items-center justify-between gap-3 rounded-xl bg-white p-4 shadow-sm">
      <div className="min-w-0">
        <p className="truncate font-medium text-slate-900">{name}</p>
        <p className="text-sm text-slate-500">{meta}</p>
      </div>
      <span className={`inline-flex shrink-0 items-center gap-1 rounded-full px-2.5 py-1 text-xs font-medium ${tone}`}>
        <Icon className="size-3.5" />
        {label}
      </span>
    </div>
  );
}
