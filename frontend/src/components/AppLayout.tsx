import {
  FileText,
  LayoutDashboard,
  Mail,
  Menu,
  PanelLeftClose,
  PanelLeftOpen,
  Search,
  Settings,
  ShieldCheck,
  X,
  type LucideIcon,
} from 'lucide-react';
import { useEffect, useRef, useState, type FocusEvent, type FormEvent } from 'react';
import { Link, NavLink, Outlet, useLocation, useNavigate } from 'react-router';
import { useAttention, type Attention } from '../lib/useAttention';
import { DropAnywhere } from './DropAnywhere';
import { Logo } from './Logo';
import { NotificationBell } from './NotificationBell';
import { UploadButton } from './UploadButton';
import { UserMenu } from './UserMenu';

type NavItem = { to: string; label: string; Icon: LucideIcon; badge?: (a: Attention) => number };

const LINKS: NavItem[] = [
  { to: '/home', label: 'Dashboard', Icon: LayoutDashboard },
  // The badges say how much waits there, without opening the dashboard.
  { to: '/documents', label: 'Documents', Icon: FileText, badge: (a) => a.toReview + a.readFailed },
  { to: '/warranties', label: 'Warranties', Icon: ShieldCheck, badge: (a) => a.expiring.length },
  { to: '/gmail', label: 'Gmail', Icon: Mail, badge: (a) => a.gmailFiles },
];

const SETTINGS: NavItem = { to: '/settings', label: 'Settings', Icon: Settings };

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
 * The frame around every signed-in page: the sidebar on the left, a top bar (search,
 * Upload bill, 🔔, the account menu), and the page itself below the top bar.
 *
 * Desktop (lg+), like Chrome's vertical tabs: the sidebar is a thin rail of icons.
 * Hovering it (or tabbing into it) slides it open over the page; the button at its
 * top left pins it open, and then the page moves over to make room.
 * Tablets: the sidebar is a drawer opened with ☰. Phones: a tab bar at the bottom.
 * A file dragged onto any page uploads it (DropAnywhere).
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
  const attention = useAttention();

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
        <Sidebar pinned={pinned} expanded={expanded} onTogglePinned={togglePinned} attention={attention} />
      </aside>

      {/* Tablets: the same sidebar as a drawer. */}
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
            <Sidebar expanded attention={attention} />
          </div>
        </div>
      )}

      {/* Only a pinned sidebar takes room; a hovered one slides over the page.
          Phones: room at the bottom for the tab bar. */}
      <div className={`pb-16 transition-[padding] duration-200 sm:pb-0 ${pinned ? 'lg:pl-64' : 'lg:pl-16'}`}>
        <header className="sticky top-0 z-20 flex h-16 items-center gap-2 border-b border-slate-200 bg-white/95 px-4 backdrop-blur sm:gap-3 sm:px-6">
          <button
            type="button"
            onClick={() => setOpen(true)}
            aria-label="Open menu"
            aria-expanded={open}
            className="hidden rounded-lg p-2 text-slate-700 hover:bg-slate-100 sm:block lg:hidden"
          >
            <Menu className="size-5" aria-hidden />
          </button>
          {/* The rail has no room for the name, so the top bar shows it instead. */}
          <Link to="/home" aria-label="Bill Locker home" className={`shrink-0 ${pinned ? 'lg:hidden' : ''}`}>
            <Logo />
          </Link>
          <GlobalSearch />
          <div className="ml-auto flex items-center gap-1 sm:gap-2">
            {/* Phones have Upload in the tab bar at the bottom. */}
            <UploadButton className="hidden sm:inline-flex" />
            <NotificationBell attention={attention} />
            <UserMenu />
          </div>
        </header>
        <Outlet />
      </div>

      <PhoneTabBar attention={attention} />
      <DropAnywhere />
    </div>
  );
}

/**
 * Search bills and products from any page: Enter opens Documents with the search filled
 * in (it looks at seller, product, bill number and file name). Ctrl K / ⌘K jumps here.
 */
function GlobalSearch() {
  const navigate = useNavigate();
  const [q, setQ] = useState('');
  const input = useRef<HTMLInputElement>(null);

  useEffect(() => {
    const onKey = (e: KeyboardEvent) => {
      if ((e.ctrlKey || e.metaKey) && e.key.toLowerCase() === 'k') {
        e.preventDefault();
        input.current?.focus();
      }
    };
    window.addEventListener('keydown', onKey);
    return () => window.removeEventListener('keydown', onKey);
  }, []);

  function submit(e: FormEvent) {
    e.preventDefault();
    const text = q.trim();
    navigate(text ? `/documents?q=${encodeURIComponent(text)}` : '/documents');
    setQ('');
    input.current?.blur();
  }

  return (
    <form role="search" onSubmit={submit} className="relative ml-2 hidden max-w-md flex-1 md:block">
      <Search className="pointer-events-none absolute top-1/2 left-3 size-4 -translate-y-1/2 text-slate-400" aria-hidden />
      <input
        ref={input}
        type="search"
        value={q}
        onChange={(e) => setQ(e.target.value)}
        aria-label="Search bills and products"
        placeholder="Search bills and products…"
        className="block w-full rounded-lg border border-slate-200 bg-slate-50 py-2 pr-14 pl-9 text-sm outline-none placeholder:text-slate-400 focus:border-brand-500 focus:bg-white focus:ring-2 focus:ring-brand-100"
      />
      <kbd className="pointer-events-none absolute top-1/2 right-2.5 -translate-y-1/2 rounded border border-slate-300 bg-white px-1.5 font-sans text-[10px] font-medium text-slate-500">
        Ctrl K
      </kbd>
    </form>
  );
}

/** A small count on a link; nothing when zero. */
function Badge({ count, className = '' }: { count: number; className?: string }) {
  if (!count) return null;
  return (
    <span
      className={`grid min-w-5 place-items-center rounded-full bg-rose-600 px-1.5 text-[11px] leading-5 font-semibold text-white tabular-nums ${className}`}
    >
      {count > 99 ? '99+' : count}
    </span>
  );
}

/**
 * The pin button, the logo, the links and Settings — the desktop sidebar and the drawer.
 * The icons sit at the same place whether it's open or not; when it's a rail, the
 * labels are simply cut off by the narrow width (so screen readers still read them).
 */
function Sidebar({ pinned, expanded, onTogglePinned, attention }: {
  pinned?: boolean;
  expanded: boolean;
  /** Missing in the drawer, which has its own ✕ button. */
  onTogglePinned?: () => void;
  attention: Attention | null;
}) {
  const linkClass = ({ isActive }: { isActive: boolean }) =>
    `relative flex items-center gap-3 rounded-lg px-2.5 py-2.5 text-sm font-medium whitespace-nowrap transition ${
      isActive ? 'bg-brand-50 text-brand-700' : 'text-slate-600 hover:bg-slate-100 hover:text-slate-900'
    }`;
  const PinIcon = pinned ? PanelLeftClose : PanelLeftOpen;
  const fade = `transition-opacity ${expanded ? 'opacity-100' : 'opacity-0'}`;

  const link = ({ to, label, Icon, badge }: NavItem) => {
    const count = badge && attention ? badge(attention) : 0;
    return (
      <NavLink key={to} to={to} className={linkClass}>
        <Icon className="size-5 shrink-0" aria-hidden />
        <span className={fade}>{label}</span>
        {/* Open: a pill at the end of the row. Rail: a dot on the icon. */}
        {count > 0 &&
          (expanded ? (
            <Badge count={count} className="ml-auto" />
          ) : (
            <span className="absolute top-2 left-7 size-2 rounded-full bg-rose-600 ring-2 ring-white" aria-hidden />
          ))}
        {count > 0 && <span className="sr-only">({count} waiting)</span>}
      </NavLink>
    );
  };

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
          className={`${fade} ${onTogglePinned ? '' : 'px-2'}`}
        >
          <Logo />
        </Link>
      </div>

      <nav aria-label="Main" className="flex-1 space-y-1 overflow-y-auto px-3 pt-2">
        {LINKS.map(link)}
      </nav>

      {/* Settings sits at the bottom, where people look for it. */}
      <div className="shrink-0 border-t border-slate-200 px-3 py-3">{link(SETTINGS)}</div>
    </div>
  );
}

/** Phones: the main places plus a big Upload in the middle, within thumb reach. */
function PhoneTabBar({ attention }: { attention: Attention | null }) {
  const tab = ({ to, label, Icon, badge }: NavItem) => {
    const count = badge && attention ? badge(attention) : 0;
    return (
      <NavLink
        key={to}
        to={to}
        className={({ isActive }) =>
          `relative flex flex-1 flex-col items-center gap-0.5 py-2 text-[11px] font-medium ${isActive ? 'text-brand-700' : 'text-slate-500'}`
        }
      >
        <Icon className="size-5" aria-hidden />
        {label}
        {count > 0 && <Badge count={count} className="absolute top-1 left-1/2 ml-1.5 scale-90" />}
      </NavLink>
    );
  };
  const [home, documents, warranties, gmail] = LINKS;

  return (
    <nav
      aria-label="Main"
      className="fixed inset-x-0 bottom-0 z-30 flex h-16 items-center border-t border-slate-200 bg-white/95 px-1 backdrop-blur sm:hidden"
    >
      {tab(home)}
      {tab(documents)}
      <div className="flex flex-1 justify-center">
        <UploadButton iconOnlyOnPhones className="rounded-full shadow-lg shadow-brand-600/30" />
      </div>
      {tab(warranties)}
      {tab(gmail)}
    </nav>
  );
}
