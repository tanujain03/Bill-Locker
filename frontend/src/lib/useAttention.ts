import { useEffect, useState } from 'react';
import { useLocation } from 'react-router';
import { getDashboard, type OpenReturn } from './dashboard';
import { CLOSING_DAYS } from './returns';
import { listServiceRecords } from './service-records';
import { getGmail } from './gmail';
import type { WarrantyView } from './warranties';

/** What's waiting for the user, for the 🔔 menu and the count badges on the links. */
export type Attention = {
  toReview: number;
  readFailed: number;
  gmailFiles: number;
  /** Warranties ending in the next 30 days (the 5 nearest). */
  expiring: WarrantyView[];
  /** Purchases whose return window closes within 3 days (today counts). */
  closingReturns: OpenReturn[];
  servicesDue: import('./service-records').ServiceRecord[];
  total: number;
};

/**
 * Asks again on every page change, so the numbers follow what the user just did
 * (saved a bill, imported a file). Errors just leave the last numbers in place.
 */
export function useAttention(): Attention | null {
  const { pathname } = useLocation();
  const [revision, setRevision] = useState(0);
  useEffect(() => { const refresh = () => setRevision((r) => r + 1); window.addEventListener('services-changed',refresh); return () => window.removeEventListener('services-changed',refresh); }, []);
  const [attention, setAttention] = useState<Attention | null>(null);

  useEffect(() => {
    let current = true;
    Promise.all([getDashboard(), getGmail().catch(() => null), listServiceRecords().catch(() => null)])
      .then(([d, g, services]) => {
        if (!current) return;
        const gmailFiles = g?.configured ? g.counts.toReview : 0;
        const { toReview, readFailed } = d.attention;
        const closingReturns = d.openReturns.filter((r) => r.daysLeft <= CLOSING_DAYS);
        const servicesDue = services?.filter((r) => r.reminderActive && (r.dueStatus === 'DUE_SOON' || r.dueStatus === 'OVERDUE')) ?? d.servicesDue;
        setAttention({
          toReview,
          readFailed,
          gmailFiles,
          expiring: d.expiringSoon,
          closingReturns,
          servicesDue,
          total: toReview + readFailed + gmailFiles + d.expiringSoon.length + closingReturns.length + servicesDue.length,
        });
      })
      .catch(() => {});
    return () => {
      current = false;
    };
  }, [pathname, revision]);

  return attention;
}
