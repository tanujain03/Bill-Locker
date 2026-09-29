import { useEffect } from 'react';
import { config } from '@/lib/config';

/** Sets `<title>` to "Page · Bill Locker" while the page is mounted. */
export function useDocumentTitle(title: string | null | undefined) {
  useEffect(() => {
    if (!title) return;
    const previous = document.title;
    document.title = `${title} · ${config.appName}`;
    return () => {
      document.title = previous;
    };
  }, [title]);
}
