import { ChevronDown, LogOut, Mail, Menu as MenuIcon, Search, Settings, Sparkles, Upload } from 'lucide-react';
import { useEffect, useRef, useState, type FormEvent } from 'react';
import { Link, useNavigate } from 'react-router';
import { useUpload } from '@/components/documents/upload-context';
import { NotificationBell } from '@/components/notifications/NotificationBell';
import { Button } from '@/components/ui/Button';
import { LogoMark } from '@/components/ui/Logo';
import { Menu } from '@/components/ui/Menu';
import { Avatar } from '@/components/ui/misc';
import { useAuth } from '@/lib/auth-context';
import { homePath, isFeatureEnabled } from '@/lib/features';

/** Natural-language search: sends the query to the AI search on the products page. */
function GlobalSearch() {
  const [query, setQuery] = useState('');
  const inputRef = useRef<HTMLInputElement>(null);
  const navigate = useNavigate();

  // "/" focuses the search from anywhere (except while typing in a field).
  useEffect(() => {
    function onKeyDown(event: KeyboardEvent) {
      const target = event.target as HTMLElement | null;
      const typing = target && (target.isContentEditable || ['INPUT', 'TEXTAREA', 'SELECT'].includes(target.tagName));
      if (event.key === '/' && !typing) {
        event.preventDefault();
        inputRef.current?.focus();
      }
    }
    document.addEventListener('keydown', onKeyDown);
    return () => document.removeEventListener('keydown', onKeyDown);
  }, []);

  function submit(event: FormEvent) {
    event.preventDefault();
    const value = query.trim();
    if (!value) return;
    navigate(`/products?ai=${encodeURIComponent(value)}`);
    setQuery('');
    inputRef.current?.blur();
  }

  return (
    <form onSubmit={submit} role="search" className="relative hidden w-full max-w-xl md:block">
      <label htmlFor="global-search" className="sr-only">
        Search your purchases with AI
      </label>
      <Sparkles className="pointer-events-none absolute top-1/2 left-3 size-4 -translate-y-1/2 text-brand-500" aria-hidden />
      <input
        id="global-search"
        ref={inputRef}
        value={query}
        onChange={(event) => setQuery(event.target.value)}
        placeholder="Ask or search… e.g. “warranties expiring in 90 days”"
        className="h-10 w-full rounded-lg border border-slate-200 bg-slate-50 pr-10 pl-9 text-sm text-slate-900 placeholder:text-slate-400 focus:border-brand-500 focus:bg-white focus:ring-4 focus:ring-brand-500/15 focus:outline-none"
      />
      <kbd className="pointer-events-none absolute top-1/2 right-3 hidden -translate-y-1/2 rounded border border-slate-200 bg-white px-1.5 text-[11px] font-medium text-slate-400 lg:block">
        /
      </kbd>
    </form>
  );
}

function UserMenu() {
  const { user, logout } = useAuth();
  const navigate = useNavigate();

  return (
    <Menu
      label="Account menu"
      triggerClassName="flex items-center gap-2 rounded-lg p-1 pr-1.5 transition-colors hover:bg-slate-100"
      trigger={
        <>
          <Avatar name={user?.name} className="size-8 text-xs" />
          <span className="hidden max-w-32 truncate text-sm font-medium text-slate-700 xl:block">{user?.name}</span>
          <ChevronDown className="hidden size-4 text-slate-400 xl:block" aria-hidden />
        </>
      }
      header={
        user && (
          <div className="border-b border-slate-100 px-3 pt-2 pb-2.5">
            <p className="truncate text-sm font-semibold text-slate-900">{user.name}</p>
            <p className="truncate text-xs text-slate-500">{user.email}</p>
          </div>
        )
      }
      items={[
        { label: 'Profile & settings', icon: <Settings className="size-4" aria-hidden />, onSelect: () => navigate('/profile') },
        ...(isFeatureEnabled('gmail')
          ? [{ label: 'Gmail import', icon: <Mail className="size-4" aria-hidden />, onSelect: () => navigate('/gmail') }]
          : []),
        {
          label: 'Sign out',
          icon: <LogOut className="size-4" aria-hidden />,
          tone: 'danger',
          onSelect: () => {
            navigate('/login', { replace: true });
            logout();
          },
        },
      ]}
    />
  );
}

export function Topbar({ onOpenMenu }: { onOpenMenu: () => void }) {
  const { openUpload } = useUpload();

  return (
    <header className="sticky top-0 z-30 flex h-16 items-center gap-3 border-b border-slate-200/80 bg-white/85 px-4 backdrop-blur sm:px-6 lg:px-8">
      <Button variant="ghost" size="icon" className="-ml-2 lg:hidden" onClick={onOpenMenu} aria-label="Open navigation">
        <MenuIcon className="size-5" aria-hidden />
      </Button>
      <Link to={homePath()} className="lg:hidden" aria-label="Bill Locker home">
        <LogoMark />
      </Link>

      {isFeatureEnabled('search') && <GlobalSearch />}

      <div className="ml-auto flex items-center gap-1 sm:gap-2">
        {isFeatureEnabled('products') && (
          <Link
            to="/products?focus=search"
            aria-label="Search products"
            className="inline-flex size-10 items-center justify-center rounded-lg text-slate-600 hover:bg-slate-100 md:hidden"
          >
            <Search className="size-5" aria-hidden />
          </Link>
        )}
        <Button className="hidden sm:inline-flex" onClick={() => openUpload()} leftIcon={<Upload className="size-4" aria-hidden />}>
          Upload bill
        </Button>
        {isFeatureEnabled('notifications') && <NotificationBell />}
        <UserMenu />
      </div>
    </header>
  );
}
