import { ChevronDown, LogOut, Settings } from 'lucide-react';
import { useEffect, useRef, useState, type KeyboardEvent } from 'react';
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
 * The avatar button in the top bar and its menu: who you are, Settings and Sign out —
 * the only place for the account (the sidebar is just navigation).
 * A proper menu for the keyboard too: it opens with focus on the first item, ↑ ↓ Home
 * End move between items, Escape closes it and returns to the button. A click outside
 * or choosing an item also closes it.
 */
export function UserMenu() {
  const { user, logout } = useAuth();
  const [open, setOpen] = useState(false);
  const box = useRef<HTMLDivElement>(null);
  const button = useRef<HTMLButtonElement>(null);
  const menu = useRef<HTMLDivElement>(null);

  useEffect(() => {
    if (!open) return;
    menu.current?.querySelector<HTMLElement>('[role="menuitem"]')?.focus();
    const onClick = (e: MouseEvent) => {
      if (!box.current?.contains(e.target as Node)) setOpen(false);
    };
    document.addEventListener('mousedown', onClick);
    return () => document.removeEventListener('mousedown', onClick);
  }, [open]);

  function onMenuKey(e: KeyboardEvent) {
    const items = [...(menu.current?.querySelectorAll<HTMLElement>('[role="menuitem"]') ?? [])];
    const at = items.indexOf(document.activeElement as HTMLElement);
    const go = (i: number) => {
      e.preventDefault();
      items[(i + items.length) % items.length]?.focus(); // wraps around at both ends
    };
    if (e.key === 'ArrowDown') go(at + 1);
    else if (e.key === 'ArrowUp') go(at - 1);
    else if (e.key === 'Home') go(0);
    else if (e.key === 'End') go(items.length - 1);
    else if (e.key === 'Escape' || e.key === 'Tab') {
      setOpen(false);
      if (e.key === 'Escape') button.current?.focus(); // back to where the user was
    }
  }

  if (!user) return null;

  return (
    <div ref={box} className="relative">
      <button
        ref={button}
        type="button"
        onClick={() => setOpen((o) => !o)}
        aria-haspopup="menu"
        aria-expanded={open}
        aria-label={`Account menu for ${user.name}`}
        className="flex items-center gap-2 rounded-xl px-1.5 py-1 hover:bg-slate-100 sm:pr-2.5"
      >
        <Avatar name={user.name} />
        <span className="hidden max-w-32 truncate text-sm font-medium sm:block">{user.name}</span>
        <ChevronDown className={`hidden size-4 text-slate-500 transition sm:block ${open ? 'rotate-180' : ''}`} aria-hidden />
      </button>

      {open && (
        <div
          ref={menu}
          role="menu"
          aria-label="Account"
          onKeyDown={onMenuKey}
          className="absolute top-full right-0 z-40 mt-2 w-64 rounded-xl border border-slate-200 bg-white p-1.5 shadow-lg"
        >
          <div className="flex items-center gap-3 border-b border-slate-100 px-3 pt-1.5 pb-3">
            <Avatar name={user.name} />
            <div className="min-w-0">
              <p className="truncate text-sm font-semibold">{user.name}</p>
              <p className="truncate text-xs text-slate-600">{user.email}</p>
            </div>
          </div>
          <div className="space-y-0.5 pt-1.5">
            <Link
              to="/settings"
              role="menuitem"
              onClick={() => setOpen(false)}
              className="flex w-full items-center gap-3 rounded-lg px-3 py-2 text-sm font-medium text-slate-700 outline-none hover:bg-slate-100 focus-visible:bg-slate-100"
            >
              <Settings className="size-4" aria-hidden />
              Settings
            </Link>
            <button
              type="button"
              role="menuitem"
              onClick={logout}
              className="flex w-full items-center gap-3 rounded-lg px-3 py-2 text-sm font-medium text-rose-600 outline-none hover:bg-rose-50 focus-visible:bg-rose-50"
            >
              <LogOut className="size-4" aria-hidden />
              Sign out
            </button>
          </div>
        </div>
      )}
    </div>
  );
}
