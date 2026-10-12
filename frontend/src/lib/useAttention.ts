import { useEffect, useState } from 'react';
import { useLocation } from 'react-router';
import { getDashboard } from './dashboard';
import { getGmail } from './gmail';
import type { WarrantyView } from './warranties';

/** What's waiting for the user, for the 🔔 menu and the count badges on the links. */
export type Attention = {
  toReview: number;
  readFailed: number;
  gmailFiles: number;
  /** Warranties ending in the next 30 days (the 5 nearest). */
  expiring: WarrantyView[];
  total: number;
};

/**
 * Asks again on every page change, so the numbers follow what the user just did
 * (saved a bill, imported a file). Errors just leave the last numbers in place.
 */
export function useAttention(): Attention | null {
  const { pathname } = useLocation();
  const [attention, setAttention] = useState<Attention | null>(null);

  useEffect(() => {
    let current = true;
    Promise.all([getDashboard(), getGmail().catch(() => null)])
      .then(([d, g]) => {
        if (!current) return;
        const gmailFiles = g?.configured ? g.counts.toReview : 0;
        const { toReview, readFailed } = d.attention;
        setAttention({
          toReview,
          readFailed,
          gmailFiles,
          expiring: d.expiringSoon,
          total: toReview + readFailed + gmailFiles + d.expiringSoon.length,
        });
      })
      .catch(() => {});
    return () => {
      current = false;
    };
  }, [pathname]);

  return attention;
}
