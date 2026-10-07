import { ChevronDown, LogOut, Mail } from 'lucide-react';
import { useEffect, useRef, useState } from 'react';
import { Link } from 'react-router';
import { useAuth } from '../lib/auth-context';

/** "Asha Verma" → "AV" for the round avatar. */
export function initials(name: string): string {
  const parts = name.trim().split(/\s+/);
  return ((parts[0]?.[0] ?? '') + (parts.length > 1 ? parts[parts.length - 1][0] : '')).toUpperCase();
}

export function Avatar({ name, size = 'md' }: { name: string; size?: 'sm' | 'md' }) {
  return (
    <span
      aria-hidden
      className={`grid shrink-0 place-items-center rounded-full bg-brand-100 font-semibold text-brand-700 ${
        size === 'sm' ? 'size-8 text-xs' : 'size-9 text-sm'
      }`}
    >
      {initials(name)}
    </span>
  );
}

/**
 * The avatar button in the top bar and its little menu: who you are, Gmail import,
 * Sign out. Closes on a click outside, on Escape, and after choosing an item.
 */
export function UserMenu() {
  const { user, logout } = useAuth();
  const [open, setOpen] = useState(false);
  const box = useRef<HTMLDivElement>(null);
  const button = useRef<HTMLButtonElement>(null);

  useEffect(() => {
    if (!open) return;
    const onClick = (e: MouseEvent) => {
      if (!box.current?.contains(e.target as Node)) setOpen(false);
    };
    const onKey = (e: KeyboardEvent) => {
      if (e.key === 'Escape') {
        setOpen(false);
        button.current?.focus(); // back to where the user was
      }
    };
    document.addEventListener('mousedown', onClick);
    document.addEventListener('keydown', onKey);
    return () => {
      document.removeEventListener('mousedown', onClick);
      document.removeEventListener('keydown', onKey);
    };
  }, [open]);

  if (!user) return null;
  const itemClass = 'flex w-full items-center gap-3 rounded-lg px-3 py-2 text-sm font-medium hover:bg-slate-100';

  return (
    <div ref={box} className="relative">
      <button
        ref={button}
        type="button"
        onClick={() => setOpen((o) => !o)}
        aria-haspopup="true"
        aria-expanded={open}
        aria-label={`Account menu for ${user.name}`}
        className="flex items-center gap-2 rounded-xl px-1.5 py-1 hover:bg-slate-100 sm:pr-2.5"
      >
        <Avatar name={user.name} />
        <span className="hidden max-w-32 truncate text-sm font-medium sm:block">{user.name}</span>
        <ChevronDown className={`hidden size-4 text-slate-500 transition sm:block ${open ? 'rotate-180' : ''}`} aria-hidden />
      </button>

      {open && (
        <div className="absolute top-full right-0 z-40 mt-2 w-64 rounded-xl border border-slate-200 bg-white p-1.5 shadow-lg">
          <div className="border-b border-slate-100 px-3 pt-1.5 pb-3">
            <p className="truncate text-sm font-semibold">{user.name}</p>
            <p className="truncate text-xs text-slate-500">{user.email}</p>
          </div>
          <div className="pt-1.5">
            <Link to="/gmail" onClick={() => setOpen(false)} className={`${itemClass} text-slate-700`}>
              <Mail className="size-4" aria-hidden />
              Gmail import
            </Link>
            <button type="button" onClick={logout} className={`${itemClass} text-rose-600 hover:bg-rose-50`}>
              <LogOut className="size-4" aria-hidden />
              Sign out
            </button>
          </div>
        </div>
      )}
    </div>
  );
}
