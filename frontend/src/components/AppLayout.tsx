import { FileText, LayoutDashboard, LogOut, Mail, Menu, ShieldCheck, X, type LucideIcon } from 'lucide-react';
import { useEffect, useRef, useState } from 'react';
import { Link, NavLink, Outlet, useLocation } from 'react-router';
import { useAuth } from '../lib/auth-context';
import { Logo } from './Logo';

const LINKS: { to: string; label: string; Icon: LucideIcon }[] = [
  { to: '/home', label: 'Dashboard', Icon: LayoutDashboard },
  { to: '/documents', label: 'Documents', Icon: FileText },
  { to: '/warranties', label: 'Warranties', Icon: ShieldCheck },
  { to: '/gmail', label: 'Gmail', Icon: Mail },
];

/**
 * The frame around every signed-in page: navigation on the left, the page on the right.
 * Desktop (lg and up): the sidebar is always there. Smaller screens: a slim top bar
 * whose ☰ button opens the same sidebar as a drawer over the page.
 */
export function AppLayout() {
  const [open, setOpen] = useState(false);
  const location = useLocation();
  const drawer = useRef<HTMLDivElement>(null);

  // Following a link closes the drawer.
  useEffect(() => setOpen(false), [location.pathname]);

  // While the drawer is open: Escape closes it, and focus moves into it.
  useEffect(() => {
    if (!open) return;
    drawer.current?.querySelector<HTMLElement>('a, button')?.focus();
    const onKey = (e: KeyboardEvent) => e.key === 'Escape' && setOpen(false);
    window.addEventListener('keydown', onKey);
    return () => window.removeEventListener('keydown', onKey);
  }, [open]);

  return (
    <div className="min-h-dvh">
      {/* Desktop: fixed sidebar. */}
      <aside className="fixed inset-y-0 left-0 z-30 hidden w-60 border-r border-slate-200 bg-white lg:block">
        <Sidebar />
      </aside>

      {/* Phones and tablets: top bar + drawer. */}
      <header className="sticky top-0 z-30 flex h-14 items-center justify-between border-b border-slate-200 bg-white/95 px-4 backdrop-blur lg:hidden">
        <Link to="/home" aria-label="Bill Locker home">
          <Logo />
        </Link>
        <button
          type="button"
          onClick={() => setOpen(true)}
          aria-label="Open menu"
          aria-expanded={open}
          className="rounded-lg p-2 text-slate-700 hover:bg-slate-100"
        >
          <Menu className="size-5" aria-hidden />
        </button>
      </header>
      {open && (
        <div className="fixed inset-0 z-40 lg:hidden" role="dialog" aria-modal="true" aria-label="Menu">
          <button
            type="button"
            aria-label="Close menu"
            onClick={() => setOpen(false)}
            className="absolute inset-0 bg-slate-900/40"
          />
          <div ref={drawer} className="absolute inset-y-0 left-0 w-72 max-w-[85vw] bg-white shadow-xl">
            <button
              type="button"
              onClick={() => setOpen(false)}
              aria-label="Close menu"
              className="absolute top-3 right-3 rounded-lg p-2 text-slate-500 hover:bg-slate-100"
            >
              <X className="size-5" aria-hidden />
            </button>
            <Sidebar />
          </div>
        </div>
      )}

      {/* The page itself, to the right of the sidebar on desktop. */}
      <div className="lg:pl-60">
        <Outlet />
      </div>
    </div>
  );
}

/** Logo, links and the signed-in user with Sign out — shared by the sidebar and the drawer. */
function Sidebar() {
  const { user, logout } = useAuth();
  const linkClass = ({ isActive }: { isActive: boolean }) =>
    `flex items-center gap-3 rounded-lg px-3 py-2.5 text-sm font-medium transition ${
      isActive ? 'bg-brand-50 text-brand-700' : 'text-slate-600 hover:bg-slate-100 hover:text-slate-900'
    }`;

  return (
    <div className="flex h-full flex-col">
      <div className="flex h-16 shrink-0 items-center px-5">
        <Link to="/home" aria-label="Bill Locker home">
          <Logo />
        </Link>
      </div>

      <nav aria-label="Main" className="flex-1 space-y-1 overflow-y-auto px-3 py-2">
        {LINKS.map(({ to, label, Icon }) => (
          <NavLink key={to} to={to} className={linkClass}>
            <Icon className="size-5 shrink-0" aria-hidden />
            {label}
          </NavLink>
        ))}
      </nav>

      <div className="shrink-0 border-t border-slate-200 p-3">
        {user && (
          <div className="flex items-center gap-3 px-2 py-2">
            <span className="grid size-9 shrink-0 place-items-center rounded-full bg-brand-100 text-sm font-semibold text-brand-700">
              {initials(user.name)}
            </span>
            <div className="min-w-0">
              <p className="truncate text-sm font-medium">{user.name}</p>
              <p className="truncate text-xs text-slate-500">{user.email}</p>
            </div>
          </div>
        )}
        <button
          type="button"
          onClick={logout}
          className="mt-1 flex w-full items-center gap-3 rounded-lg px-3 py-2.5 text-sm font-medium text-slate-600 hover:bg-slate-100 hover:text-slate-900"
        >
          <LogOut className="size-5" aria-hidden />
          Sign out
        </button>
      </div>
    </div>
  );
}

/** "Asha Verma" → "AV" for the round avatar. */
function initials(name: string): string {
  const parts = name.trim().split(/\s+/);
  return ((parts[0]?.[0] ?? '') + (parts.length > 1 ? parts[parts.length - 1][0] : '')).toUpperCase();
}
