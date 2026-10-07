import type { ReactNode } from 'react';
import { useScreenScale } from '../lib/useScreenScale';
import { Logo } from './Logo';
import { PromoPanel } from './PromoPanel';

/**
 * The frame shared by sign in, sign up and the password pages:
 * left 40% = the form in a card, right 60% = what Bill Locker is (hidden on small screens).
 * Exactly one screen high: the page itself never scrolls (h-dvh + overflow-hidden),
 * and useScreenScale() sizes everything to the real screen so it always fits.
 */
export function AuthLayout({ title, subtitle, children, footer }: {
  title: string;
  subtitle?: string;
  children: ReactNode;
  footer?: ReactNode;
}) {
  useScreenScale();

  return (
    <div className="flex h-dvh overflow-hidden">
      <div className="flex w-full flex-col items-center overflow-y-auto bg-slate-100 px-4 py-6 lg:w-2/5">
        <main className="my-auto w-full max-w-md shrink-0 overflow-hidden rounded-xl border border-slate-200 bg-white shadow-sm">
          <div className="px-6 pt-8 pb-7 sm:px-12">
            <div className="flex justify-center">
              <Logo size="lg" />
            </div>
            <h1 className="mt-6 text-center text-2xl font-light tracking-tight text-slate-800">{title}</h1>
            {subtitle && <p className="mt-2 text-center text-sm text-slate-600">{subtitle}</p>}
            <div className="mt-6">{children}</div>
          </div>
          <div className="border-t border-slate-200 bg-slate-50 px-6 py-4 text-center text-sm text-slate-600">
            {footer ?? <>© {new Date().getFullYear()} Bill Locker</>}
          </div>
        </main>
      </div>
      <PromoPanel />
    </div>
  );
}
