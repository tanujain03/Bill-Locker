import { useEffect } from 'react';

/**
 * The browser tab's title: "Documents · Bill Locker". Tabs, history and bookmarks can
 * then tell the pages apart (they all said "Bill Locker" before).
 */
export function usePageTitle(title: string | null | undefined) {
  useEffect(() => {
    document.title = title ? `${title} · Bill Locker` : 'Bill Locker';
  }, [title]);
}
