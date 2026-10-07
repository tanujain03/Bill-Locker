import { useEffect, useRef } from 'react';

/** Calls `refresh` every `ms` while `active` (skipping ticks while the tab is hidden). */
export function usePolling(active: boolean, refresh: () => void, ms = 3000) {
  // Keep the latest refresh in a ref, so a new function each render doesn't restart the timer.
  const latest = useRef(refresh);
  latest.current = refresh;

  useEffect(() => {
    if (!active) return;
    const timer = setInterval(() => {
      if (!document.hidden) latest.current();
    }, ms);
    return () => clearInterval(timer);
  }, [active, ms]);
}
