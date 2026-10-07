import {
  FileText,
  LayoutDashboard,
  Mail,
  Menu,
  PanelLeftClose,
  PanelLeftOpen,
  ShieldCheck,
  Upload,
  X,
  type LucideIcon,
} from 'lucide-react';
import { useEffect, useRef, useState, type FocusEvent } from 'react';
import { Link, NavLink, Outlet, useLocation } from 'react-router';
import { Logo } from './Logo';
import { UserMenu } from './UserMenu';

const LINKS: { to: string; label: string; Icon: LucideIcon }[] = [
  { to: '/home', label: 'Dashboard', Icon: LayoutDashboard },
  { to: '/documents', label: 'Documents', Icon: FileText },
  { to: '/warranties', label: 'Warranties', Icon: ShieldCheck },
  { to: '/gmail', label: 'Gmail Import', Icon: Mail },
];

/** Remembers "keep the sidebar open" in this browser (a per-viewer convenience). */
const PINNED_KEY = 'billLocker.sidebarPinned';

function readPinned(): boolean {
  try {
    return localStorage.getItem(PINNED_KEY) === 'true';
  } catch {
    return false; // storage blocked (e.g. private window): start collapsed
  }
}

/**
 * The frame around every signed-in page: the sidebar on the left, a top bar with
 * Upload bill and the account menu, and the page itself below the top bar.
 *
 * Desktop (lg+), like Chrome's vertical tabs: the sidebar is a thin rail of icons.
 * Hovering it (or tabbing into it) slides it open over the page; the button at its
 * top left pins it open, and then the page moves over to make room.
 * Below lg the sidebar becomes a drawer opened with the ☰ button.
 */
export function AppLayout() {
  const [open, setOpen] = useState(false);
  const [pinned, setPinned] = useState(readPinned);
  /** Open for a moment because the mouse or keyboard focus is on it. */
  const [peek, setPeek] = useState(false);
  const peekTimer = useRef<number | undefined>(undefined);
  const location = useLocation();
  const drawer = useRef<HTMLDivElement>(null);
  const expanded = pinned || peek;

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

  useEffect(() => () => window.clearTimeout(peekTimer.current), []);

  function togglePinned() {
    const next = !pinned;
    setPinned(next);
    setPeek(false); // unpinning closes it straight away, even with the mouse on it
    try {
      localStorage.setItem(PINNED_KEY, String(next));
    } catch {
      // not saved; it still works until the page is reloaded
    }
  }

  return (
    <div className="min-h-dvh">
      {/* Desktop: a rail of icons that opens on hover, or stays open when pinned. */}
      <aside
        onMouseEnter={() => {
          // A short wait, so just passing the mouse over the rail doesn't open it.
          peekTimer.current = window.setTimeout(() => setPeek(true), 150);
        }}
        onMouseLeave={() => {
          window.clearTimeout(peekTimer.current);
          setPeek(false);
        }}
        onFocus={() => setPeek(true)}
        onBlur={(e: FocusEvent) => {
          if (!e.currentTarget.contains(e.relatedTarget)) setPeek(false);
        }}
        className={`fixed inset-y-0 left-0 z-30 hidden overflow-hidden border-r border-slate-200 bg-white transition-[width,box-shadow] duration-200 lg:block ${
          expanded ? 'w-64' : 'w-16'
        } ${peek && !pinned ? 'shadow-xl' : ''}`}
      >
        <Sidebar pinned={pinned} expanded={expanded} onTogglePinned={togglePinned} />
      </aside>

      {/* Phones and tablets: the same sidebar as a drawer. */}
      {open && (
        <div className="fixed inset-0 z-50 lg:hidden" role="dialog" aria-modal="true" aria-label="Menu">
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
              className="absolute top-4 right-3 rounded-lg p-2 text-slate-500 hover:bg-slate-100"
            >
              <X className="size-5" aria-hidden />
            </button>
            <Sidebar expanded />
          </div>
        </div>
      )}

      {/* Only a pinned sidebar takes room; a hovered one slides over the page. */}
      <div className={`transition-[padding] duration-200 ${pinned ? 'lg:pl-64' : 'lg:pl-16'}`}>
        <header className="sticky top-0 z-20 flex h-16 items-center gap-3 border-b border-slate-200 bg-white/95 px-4 backdrop-blur sm:px-6">
          <button
            type="button"
            onClick={() => setOpen(true)}
            aria-label="Open menu"
            aria-expanded={open}
            className="rounded-lg p-2 text-slate-700 hover:bg-slate-100 lg:hidden"
          >
            <Menu className="size-5" aria-hidden />
          </button>
          {/* The rail has no room for the name, so the top bar shows it instead. */}
          <Link to="/home" aria-label="Bill Locker home" className={pinned ? 'lg:hidden' : ''}>
            <Logo />
          </Link>
          <div className="ml-auto flex items-center gap-2 sm:gap-3">
            {/* The only Upload bill button (goes to Documents, whose drop zone uploads).
                On phones only the icon shows. */}
            <Link
              to="/documents"
              aria-label="Upload bill"
              className="inline-flex items-center justify-center gap-2 rounded-lg bg-brand-600 px-3 py-2.5 text-sm font-semibold text-white shadow-sm hover:bg-brand-700 sm:px-4"
            >
              <Upload className="size-4" aria-hidden />
              <span className="hidden sm:inline">Upload bill</span>
            </Link>
            <UserMenu />
          </div>
        </header>
        <Outlet />
      </div>
    </div>
  );
}

/**
 * The pin button, the logo and the links — the desktop sidebar and the drawer.
 * The icons sit at the same place whether it's open or not; when it's a rail, the
 * labels are simply cut off by the narrow width (so screen readers still read them).
 * Upload bill, who is signed in and Sign out live only in the top bar.
 */
function Sidebar({ pinned, expanded, onTogglePinned }: {
  pinned?: boolean;
  expanded: boolean;
  /** Missing in the drawer, which has its own ✕ button. */
  onTogglePinned?: () => void;
}) {
  const linkClass = ({ isActive }: { isActive: boolean }) =>
    `flex items-center gap-3 rounded-lg px-2.5 py-2.5 text-sm font-medium whitespace-nowrap transition ${
      isActive ? 'bg-brand-50 text-brand-700' : 'text-slate-600 hover:bg-slate-100 hover:text-slate-900'
    }`;
  const PinIcon = pinned ? PanelLeftClose : PanelLeftOpen;

  return (
    <div className="flex h-full w-64 flex-col">
      <div className="flex h-16 shrink-0 items-center gap-2 px-3">
        {onTogglePinned && (
          <button
            type="button"
            onClick={onTogglePinned}
            aria-label={pinned ? 'Collapse sidebar' : 'Keep sidebar open'}
            aria-pressed={pinned}
            title={pinned ? 'Collapse sidebar' : 'Keep sidebar open'}
            className="shrink-0 rounded-lg p-2.5 text-slate-600 hover:bg-slate-100 hover:text-slate-900"
          >
            <PinIcon className="size-5" aria-hidden />
          </button>
        )}
        <Link
          to="/home"
          aria-label="Bill Locker home"
          tabIndex={expanded ? undefined : -1}
          className={`transition-opacity ${onTogglePinned ? '' : 'px-2'} ${expanded ? 'opacity-100' : 'opacity-0'}`}
        >
          <Logo />
        </Link>
      </div>

      <nav aria-label="Main" className="flex-1 space-y-1 overflow-y-auto px-3 pt-2">
        {LINKS.map(({ to, label, Icon }) => (
          <NavLink key={to} to={to} className={linkClass}>
            <Icon className="size-5 shrink-0" aria-hidden />
            <span className={`transition-opacity ${expanded ? 'opacity-100' : 'opacity-0'}`}>{label}</span>
          </NavLink>
        ))}
      </nav>
    </div>
  );
}
