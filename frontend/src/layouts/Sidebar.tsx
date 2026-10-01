import {
  Bell,
  FileText,
  LayoutDashboard,
  LogOut,
  Mail,
  Package,
  Settings,
  ShieldCheck,
  Sparkles,
  Upload,
  Wrench,
  X,
  type LucideIcon,
} from 'lucide-react';
import { Link, NavLink, useNavigate } from 'react-router';
import { useUpload } from '@/components/documents/upload-context';
import { Button } from '@/components/ui/Button';
import { Logo } from '@/components/ui/Logo';
import { Avatar } from '@/components/ui/misc';
import { useDocumentActivity } from '@/hooks/useDocuments';
import { useUnreadCount } from '@/hooks/useNotifications';
import { useAuth } from '@/lib/auth-context';
import { cn } from '@/lib/cn';
import { homePath, isFeatureEnabled, type Feature } from '@/lib/features';

interface NavItem {
  to: string;
  label: string;
  icon: LucideIcon;
  badge?: number;
  /** Shown only when this feature is available (see lib/features.ts). */
  feature?: Feature;
}

function NavItemLink({ item, onNavigate }: { item: NavItem; onNavigate?: () => void }) {
  const Icon = item.icon;
  return (
    <NavLink
      to={item.to}
      onClick={onNavigate}
      className={({ isActive }) =>
        cn(
          'group flex items-center gap-3 rounded-lg px-3 py-2 text-sm font-medium transition-colors',
          isActive ? 'bg-brand-50 text-brand-700' : 'text-slate-600 hover:bg-slate-100 hover:text-slate-900',
        )
      }
    >
      {({ isActive }) => (
        <>
          <Icon
            className={cn('size-[18px] shrink-0', isActive ? 'text-brand-600' : 'text-slate-400 group-hover:text-slate-600')}
            aria-hidden
          />
          <span className="flex-1">{item.label}</span>
          {item.badge !== undefined && item.badge > 0 && (
            <span
              className={cn(
                'tabular min-w-5 rounded-full px-1.5 text-center text-xs font-semibold',
                isActive ? 'bg-brand-600 text-white' : 'bg-slate-200/80 text-slate-700',
              )}
            >
              {item.badge > 99 ? '99+' : item.badge}
              <span className="sr-only"> pending</span>
            </span>
          )}
        </>
      )}
    </NavLink>
  );
}

interface SidebarProps {
  /** Called after a link is chosen (closes the mobile drawer). */
  onNavigate?: () => void;
  onClose?: () => void;
}

export function Sidebar({ onNavigate, onClose }: SidebarProps) {
  const { user, logout } = useAuth();
  const navigate = useNavigate();
  const { openUpload } = useUpload();
  const { data: unread = 0 } = useUnreadCount(isFeatureEnabled('notifications'));
  const { toReview } = useDocumentActivity();

  const allItems: NavItem[] = [
    { to: '/dashboard', label: 'Dashboard', icon: LayoutDashboard, feature: 'dashboard' },
    { to: '/products', label: 'My Products', icon: Package, feature: 'products' },
    { to: '/documents', label: 'Documents', icon: FileText, badge: toReview },
    { to: '/warranties', label: 'Warranties', icon: ShieldCheck, feature: 'warranties' },
    { to: '/services', label: 'Services', icon: Wrench, feature: 'services' },
    { to: '/assistant', label: 'AI Assistant', icon: Sparkles, feature: 'assistant' },
    { to: '/gmail', label: 'Gmail Import', icon: Mail, feature: 'gmail' },
    { to: '/notifications', label: 'Notifications', icon: Bell, badge: unread, feature: 'notifications' },
  ];
  const items = allItems.filter((item) => !item.feature || isFeatureEnabled(item.feature));

  function signOut() {
    onNavigate?.();
    navigate('/login', { replace: true });
    logout();
  }

  return (
    <div className="flex h-full flex-col bg-white">
      <div className="flex h-16 shrink-0 items-center justify-between px-5">
        <Link to={homePath()} onClick={onNavigate} aria-label="Bill Locker home">
          <Logo />
        </Link>
        {onClose && (
          <Button variant="ghost" size="icon-sm" onClick={onClose} aria-label="Close navigation">
            <X className="size-4" aria-hidden />
          </Button>
        )}
      </div>

      <div className="px-3 pt-2">
        <Button
          className="w-full"
          onClick={() => {
            onNavigate?.();
            openUpload();
          }}
          leftIcon={<Upload className="size-4" aria-hidden />}
        >
          Upload bill
        </Button>
      </div>

      <nav aria-label="Main" className="scrollbar-thin flex-1 overflow-y-auto px-3 py-4">
        <ul className="space-y-0.5">
          {items.map((item) => (
            <li key={item.to}>
              <NavItemLink item={item} onNavigate={onNavigate} />
            </li>
          ))}
        </ul>
      </nav>

      <div className="border-t border-slate-100 px-3 py-3">
        <ul className="space-y-0.5">
          <li>
            <NavItemLink item={{ to: '/profile', label: 'Settings', icon: Settings }} onNavigate={onNavigate} />
          </li>
          <li>
            <button
              type="button"
              onClick={signOut}
              className="flex w-full items-center gap-3 rounded-lg px-3 py-2 text-sm font-medium text-slate-600 transition-colors hover:bg-rose-50 hover:text-rose-700"
            >
              <LogOut className="size-[18px] text-slate-400" aria-hidden />
              Logout
            </button>
          </li>
        </ul>
        {user && (
          <div className="mt-3 flex items-center gap-3 rounded-xl bg-slate-50 px-3 py-2.5">
            <Avatar name={user.name} className="size-8 text-xs" />
            <div className="min-w-0">
              <p className="truncate text-sm font-medium text-slate-900">{user.name}</p>
              <p className="truncate text-xs text-slate-500">{user.email}</p>
            </div>
          </div>
        )}
      </div>
    </div>
  );
}
