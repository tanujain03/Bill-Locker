import { ArrowRight, BellRing, Mail, ShieldAlert, ShieldCheck, ShieldX, Sparkles, type LucideIcon } from 'lucide-react';
import { Link } from 'react-router';

/** What the three short feature lines say, each with its icon. */
const FEATURES: { icon: LucideIcon; text: string }[] = [
  { icon: Sparkles, text: 'AI reads your bills' },
  { icon: Mail, text: 'Imports bills from Gmail' },
  { icon: BellRing, text: 'Warranty and return reminders' },
];

/**
 * Right side of the sign-in screens (large screens only): what Bill Locker does, with a
 * picture of it. Everything sits in one centred column of limited width, so on a wide or
 * very large screen it stays together instead of spreading out.
 */
export function PromoPanel() {
  return (
    // No background of its own: AuthLayout paints it from the split to the screen's edge.
    <aside className="relative hidden w-3/5 items-center justify-center lg:flex">
      {/* Two soft blurred shapes, only for depth (AuthLayout clips them at the screen's edge). */}
      <div className="pointer-events-none absolute -top-40 -right-40 size-[34rem] rounded-full bg-brand-200/50 blur-3xl" aria-hidden />
      <div className="pointer-events-none absolute -bottom-48 left-0 size-[30rem] rounded-full bg-violet-200/40 blur-3xl" aria-hidden />

      <div className="relative w-full max-w-[40rem] px-12 py-10">
        <h2 className="text-5xl leading-tight font-bold tracking-tight text-brand-950">
          Never lose a bill.
          <br />
          Never miss a warranty.
        </h2>
        <p className="mt-5 text-xl text-slate-700">
          Keep every invoice and warranty card in one locker, and get reminded before cover runs out.
        </p>

        <ul className="mt-6 flex flex-wrap gap-x-5 gap-y-2">
          {FEATURES.map(({ icon: Icon, text }) => (
            <li key={text} className="flex items-center gap-2 text-sm font-medium text-slate-700">
              <span className="grid size-7 place-items-center rounded-lg bg-white text-brand-600 shadow-sm ring-1 ring-brand-100">
                <Icon className="size-4" aria-hidden />
              </span>
              {text}
            </li>
          ))}
        </ul>

        <Link
          to="/register"
          className="mt-8 inline-flex items-center gap-2 rounded-full bg-brand-600 px-7 py-3.5 text-lg font-semibold text-white shadow-lg shadow-brand-600/25 transition hover:bg-brand-700"
        >
          Create your free locker
          <ArrowRight className="size-5" aria-hidden />
        </Link>

        {/* Decorative picture of the app (made of HTML, not an image file). The reminder
            note is pinned to the card, so the two always stay together. */}
        <div className="relative mt-14 mr-6 ml-2" aria-hidden>
          <div className="-rotate-3 rounded-2xl bg-linear-to-br from-brand-500 to-violet-500 p-4 shadow-2xl shadow-brand-600/30">
            <p className="px-2 pb-3 text-lg font-bold text-white">My warranties</p>
            <div className="space-y-3">
              <Row icon={ShieldCheck} name="Sony Bravia 55″ TV" meta="Covered until Mar 2028" tone="bg-emerald-100 text-emerald-700" label="Active" />
              <Row icon={ShieldAlert} name="Dell Inspiron laptop" meta="25 days left" tone="bg-amber-100 text-amber-800" label="Expiring soon" />
              <Row icon={ShieldX} name="LG washing machine" meta="Ended Aug 2026" tone="bg-rose-100 text-rose-700" label="Expired" />
            </div>
          </div>
          <div className="absolute -top-7 -right-6 flex rotate-2 items-center gap-2 rounded-xl bg-white px-4 py-3 text-sm font-medium text-slate-700 shadow-xl ring-1 ring-slate-100">
            <BellRing className="size-4 text-amber-500" />
            Reminder: laptop warranty ends in 25 days
          </div>
        </div>
      </div>
    </aside>
  );
}

function Row({ icon: Icon, name, meta, tone, label }: {
  icon: LucideIcon;
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
