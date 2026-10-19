import type { ReactNode } from 'react';
import { usePageTitle } from '../lib/usePageTitle';
import { useScreenScale } from '../lib/useScreenScale';
import { Logo } from './Logo';
import { PromoPanel } from './PromoPanel';

/** The widest the layout gets, as a multiple of the screen height: 1440 × 900 = 1.6 (16:10). */
const FRAME_RATIO = 1.6;

/**
 * The frame shared by sign in, sign up and the password pages:
 * left 40% = the form in a card, right 60% = what Bill Locker is (hidden on small screens).
 * Exactly one screen high: the page itself never scrolls (h-dvh + overflow-hidden),
 * and useScreenScale() sizes everything to the real screen so it always fits.
 *
 * Proportions: the two halves sit in a centred frame at most 1.6 × the screen height wide
 * (the 1440 × 900 design is 16:10). On a 16:10 or 16:9 screen that is the whole width; on a
 * wider screen (21:9) the layout keeps its shape in the middle instead of stretching apart.
 * The backgrounds still reach both edges of the screen and meet exactly at the frame's split.
 */
export function AuthLayout({ title, subtitle, children, footer }: {
  title: string;
  subtitle?: string;
  children: ReactNode;
  footer?: ReactNode;
}) {
  useScreenScale();
  usePageTitle(title);

  return (
    <div className="relative h-dvh overflow-hidden bg-slate-100">
      {/* The right side's background, from the split (40% into the frame) to the screen's right edge. */}
      <div
        className="absolute inset-y-0 right-0 hidden bg-linear-to-br from-brand-50 via-white to-violet-50 lg:block"
        style={{ left: `calc(50% - min(100%, ${FRAME_RATIO * 100}dvh) * 0.1)` }}
        aria-hidden
      />
      <div className="relative mx-auto flex h-full w-full" style={{ maxWidth: `${FRAME_RATIO * 100}dvh` }}>
      <div className="flex w-full flex-col items-center overflow-y-auto px-4 py-6 lg:w-2/5">
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
    </div>
  );
}
