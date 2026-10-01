import { Camera } from 'lucide-react';
import { Suspense, useCallback, useEffect, useRef, useState } from 'react';
import { Outlet, useLocation } from 'react-router';
import { UploadProvider } from '@/components/documents/UploadProvider';
import { useUpload } from '@/components/documents/upload-context';
import { PageLoader } from '@/components/ui/feedback';
import { Sidebar } from './Sidebar';
import { Topbar } from './Topbar';

/** Phones: a thumb-reachable upload button (camera-first bill capture). */
function MobileUploadButton() {
  const { openUpload } = useUpload();
  const { pathname } = useLocation();
  // Pages with their own bottom action bar / composer.
  if (pathname.startsWith('/assistant') || pathname.startsWith('/documents/')) return null;
  return (
    <button
      type="button"
      onClick={() => openUpload()}
      className="fixed right-4 bottom-[max(1.25rem,env(safe-area-inset-bottom))] z-30 inline-flex h-14 items-center gap-2 rounded-full bg-brand-600 pr-5 pl-4 text-sm font-semibold text-white shadow-elevated transition-colors hover:bg-brand-700 sm:hidden"
    >
      <Camera className="size-5" aria-hidden />
      Add bill
    </button>
  );
}

function MobileNavDrawer({ onClose }: { onClose: () => void }) {
  const panelRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    panelRef.current?.querySelector<HTMLElement>('a[href], button')?.focus();
    const previousOverflow = document.body.style.overflow;
    document.body.style.overflow = 'hidden';
    function onKeyDown(event: KeyboardEvent) {
      if (event.key === 'Escape') onClose();
    }
    document.addEventListener('keydown', onKeyDown);
    return () => {
      document.body.style.overflow = previousOverflow;
      document.removeEventListener('keydown', onKeyDown);
    };
  }, [onClose]);

  return (
    <div className="fixed inset-0 z-50 lg:hidden" role="dialog" aria-modal="true" aria-label="Navigation">
      <div className="absolute inset-0 animate-fade-in bg-slate-900/40" aria-hidden onClick={onClose} />
      <div ref={panelRef} className="absolute inset-y-0 left-0 w-72 max-w-[85vw] animate-slide-in-left shadow-elevated">
        <Sidebar onNavigate={onClose} onClose={onClose} />
      </div>
    </div>
  );
}

export function AppLayout() {
  const [navOpen, setNavOpen] = useState(false);
  const closeNav = useCallback(() => setNavOpen(false), []);

  return (
    <UploadProvider>
      <a
        href="#main"
        className="sr-only focus:not-sr-only focus:fixed focus:top-3 focus:left-3 focus:z-[70] focus:rounded-lg focus:bg-white focus:px-4 focus:py-2 focus:text-sm focus:font-medium focus:shadow-elevated"
      >
        Skip to content
      </a>
      <div className="min-h-dvh">
        <aside className="fixed inset-y-0 left-0 z-40 hidden w-64 border-r border-slate-200/80 lg:block">
          <Sidebar />
        </aside>
        {navOpen && <MobileNavDrawer onClose={closeNav} />}

        <div className="lg:pl-64">
          <Topbar onOpenMenu={() => setNavOpen(true)} />
          <main id="main" tabIndex={-1} className="mx-auto w-full max-w-7xl px-4 pt-6 pb-24 focus:outline-none sm:px-6 sm:pb-10 lg:px-8 lg:pt-8">
            <Suspense fallback={<PageLoader />}>
              <Outlet />
            </Suspense>
          </main>
        </div>
        <MobileUploadButton />
      </div>
    </UploadProvider>
  );
}
