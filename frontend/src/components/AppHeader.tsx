import { LogOut } from 'lucide-react';
import { Link, NavLink } from 'react-router';
import { useAuth } from '../lib/auth-context';
import { Logo } from './Logo';

/** The top bar on every signed-in page: logo, navigation, sign out. */
export function AppHeader() {
  const { logout } = useAuth();
  const linkClass = ({ isActive }: { isActive: boolean }) =>
    `rounded-lg px-3 py-2 text-sm font-medium ${isActive ? 'bg-brand-50 text-brand-700' : 'text-slate-700 hover:bg-slate-100'}`;

  return (
    <header className="border-b border-slate-200 bg-white">
      <div className="mx-auto flex h-16 max-w-6xl items-center justify-between gap-4 px-4">
        <div className="flex items-center gap-6">
          <Link to="/home" aria-label="Bill Locker home">
            <Logo />
          </Link>
          <nav className="flex gap-1">
            <NavLink to="/home" className={linkClass}>
              Home
            </NavLink>
            <NavLink to="/documents" className={linkClass}>
              Documents
            </NavLink>
            <NavLink to="/gmail" className={linkClass}>
              Gmail
            </NavLink>
          </nav>
        </div>
        <button
          type="button"
          onClick={logout}
          className="inline-flex items-center gap-1.5 rounded-lg px-3 py-2 text-sm font-medium text-slate-700 hover:bg-slate-100"
        >
          <LogOut className="size-4" aria-hidden />
          <span className="hidden sm:inline">Sign out</span>
        </button>
      </div>
    </header>
  );
}
