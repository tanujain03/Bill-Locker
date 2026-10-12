import { Bell, CircleAlert, Clock, Eye, Mail, type LucideIcon } from 'lucide-react';
import { useEffect, useRef, useState } from 'react';
import { Link } from 'react-router';
import type { Attention } from '../lib/useAttention';

/**
 * 🔔 in the top bar: everything that's waiting, on every page — bills to review, bills
 * that couldn't be read, files found in Gmail, warranties ending soon. Each line is a link.
 */
export function NotificationBell({ attention }: { attention: Attention | null }) {
  const [open, setOpen] = useState(false);
  const box = useRef<HTMLDivElement>(null);
  const count = attention?.total ?? 0;

  useEffect(() => {
    if (!open) return;
    const onClick = (e: MouseEvent) => !box.current?.contains(e.target as Node) && setOpen(false);
    const onKey = (e: KeyboardEvent) => e.key === 'Escape' && setOpen(false);
    document.addEventListener('mousedown', onClick);
    document.addEventListener('keydown', onKey);
    return () => {
      document.removeEventListener('mousedown', onClick);
      document.removeEventListener('keydown', onKey);
    };
  }, [open]);

  const items: { to: string; Icon: LucideIcon; tone: string; text: string }[] = [];
  if (attention) {
    const bills = (n: number) => (n === 1 ? '1 bill' : `${n} bills`);
    if (attention.toReview) items.push({ to: '/documents?status=EXTRACTED', Icon: Eye, tone: 'text-amber-600', text: `${bills(attention.toReview)} to review and save` });
    if (attention.readFailed) items.push({ to: '/documents?status=UPLOADED', Icon: CircleAlert, tone: 'text-rose-600', text: `${bills(attention.readFailed)} couldn’t be read` });
    if (attention.gmailFiles) items.push({ to: '/gmail', Icon: Mail, tone: 'text-brand-600', text: `${attention.gmailFiles === 1 ? '1 file' : `${attention.gmailFiles} files`} found in Gmail` });
    for (const w of attention.expiring) {
      items.push({
        to: `/documents/${w.documentId}`,
        Icon: Clock,
        tone: 'text-amber-600',
        text: `${w.productName ?? 'A product'}: warranty ends ${w.daysLeft === 0 ? 'today' : `in ${w.daysLeft} day${w.daysLeft === 1 ? '' : 's'}`}`,
      });
    }
  }

  return (
    <div ref={box} className="relative">
      <button
        type="button"
        onClick={() => setOpen((o) => !o)}
        aria-expanded={open}
        aria-label={count ? `Notifications: ${count} waiting` : 'Notifications'}
        className="relative rounded-lg p-2.5 text-slate-600 hover:bg-slate-100 hover:text-slate-900"
      >
        <Bell className="size-5" aria-hidden />
        {count > 0 && (
          <span className="absolute top-1 right-1 grid min-w-4.5 place-items-center rounded-full bg-rose-600 px-1 text-[10px] leading-4.5 font-semibold text-white">
            {count > 9 ? '9+' : count}
          </span>
        )}
      </button>

      {open && (
        <div className="absolute top-full right-0 z-40 mt-2 w-80 max-w-[calc(100vw-2rem)] rounded-xl border border-slate-200 bg-white p-1.5 shadow-lg">
          <p className="px-3 pt-1.5 pb-2 text-sm font-semibold">Notifications</p>
          {items.length === 0 ? (
            <p className="px-3 pb-3 text-sm text-slate-500">You’re all caught up.</p>
          ) : (
            <ul>
              {items.map(({ to, Icon, tone, text }) => (
                <li key={to + text}>
                  <Link
                    to={to}
                    onClick={() => setOpen(false)}
                    className="flex items-start gap-3 rounded-lg px-3 py-2 text-sm text-slate-700 hover:bg-slate-100"
                  >
                    <Icon className={`mt-0.5 size-4 shrink-0 ${tone}`} aria-hidden />
                    <span className="min-w-0">{text}</span>
                  </Link>
                </li>
              ))}
            </ul>
          )}
        </div>
      )}
    </div>
  );
}
