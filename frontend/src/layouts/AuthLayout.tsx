import { BellRing, Mail, ScanText, ShieldAlert } from 'lucide-react';
import { Link, Outlet } from 'react-router';
import { Logo } from '@/components/ui/Logo';

const HIGHLIGHTS = [
  { icon: ScanText, text: 'AI reads your bills and fills in the details for you' },
  { icon: Mail, text: 'Import invoices straight from Gmail — no manual uploads' },
  { icon: BellRing, text: 'Reminders before warranties expire or services are due' },
];

/** Split screen: brand story on the left (desktop), the auth form on the right. */
export function AuthLayout() {
  return (
    <div className="grid min-h-dvh bg-white lg:grid-cols-2">
      <div className="relative hidden flex-col justify-between overflow-hidden bg-brand-950 p-10 text-white lg:flex xl:p-14">
        <div
          className="pointer-events-none absolute inset-0 opacity-[0.07]"
          aria-hidden
          style={{
            backgroundImage:
              'linear-gradient(to right, #fff 1px, transparent 1px), linear-gradient(to bottom, #fff 1px, transparent 1px)',
            backgroundSize: '44px 44px',
          }}
        />
        <Link to="/" className="relative w-fit">
          <Logo inverted />
        </Link>

        <div className="relative">
          <h2 className="max-w-md text-3xl font-semibold tracking-tight text-balance xl:text-4xl">
            Never lose a bill. Never miss a warranty.
          </h2>
          <ul className="mt-8 space-y-4">
            {HIGHLIGHTS.map(({ icon: Icon, text }) => (
              <li key={text} className="flex items-center gap-3 text-brand-100">
                <span className="flex size-9 shrink-0 items-center justify-center rounded-xl bg-white/10">
                  <Icon className="size-4" aria-hidden />
                </span>
                {text}
              </li>
            ))}
          </ul>

          <div className="mt-12 max-w-sm rounded-2xl bg-white p-4 text-slate-900 shadow-elevated" aria-hidden>
            <div className="flex items-center gap-3">
              <span className="flex size-10 items-center justify-center rounded-xl bg-amber-50 text-amber-600">
                <ShieldAlert className="size-5" />
              </span>
              <div className="min-w-0">
                <p className="text-sm font-semibold">Dell Inspiron 15 laptop</p>
                <p className="text-xs text-slate-500">Warranty expires in 25 days</p>
              </div>
              <span className="ml-auto rounded-full bg-amber-50 px-2 py-0.5 text-[11px] font-medium text-amber-800 ring-1 ring-amber-600/25">
                Expiring soon
              </span>
            </div>
            <div className="mt-3 h-1.5 rounded-full bg-amber-100">
              <div className="h-full w-[93%] rounded-full bg-amber-500" />
            </div>
          </div>
        </div>

        <p className="relative text-xs text-brand-300">© {new Date().getFullYear()} Bill Locker</p>
      </div>

      <div className="flex flex-col justify-center px-4 py-10 sm:px-10">
        <div className="mx-auto w-full max-w-sm">
          <Link to="/" className="mb-10 inline-block lg:hidden">
            <Logo />
          </Link>
          <Outlet />
        </div>
      </div>
    </div>
  );
}
