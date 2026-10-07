/**
 * The dashboard: the backend sends every number ready-made (DashboardService);
 * this file holds the shape of that answer and the links the dashboard opens.
 */
import { api } from './api';
import type { DocumentSummary } from './documents';
import type { WarrantyView } from './warranties';

export type MonthSpend = { month: string /* "YYYY-MM" */; amount: number; bills: number };
export type ShopSpend = { name: string; amount: number; bills: number };

export type Dashboard = {
  savedBills: number;
  savedBillsThisMonth: number;
  products: number;
  productsWithWarranty: number;
  totalSpent: number;
  spentThisMonth: number;
  attention: { toReview: number; readFailed: number; reading: number };
  warranties: { active: number; expiringSoon: number; expired: number; noInfo: number };
  /** The 5 nearest "expiring soon" warranties. */
  expiringSoon: WarrantyView[];
  /** 12 months, oldest first, the current month last. */
  spendingByMonth: MonthSpend[];
  billsWithoutDateOrTotal: number;
  topShops: ShopSpend[];
  /** The 5 newest bills, any status. */
  recentBills: DocumentSummary[];
};

export const getDashboard = () => api<Dashboard>('/dashboard');

// The chart and the shop list only count saved bills, so their links ask for saved
// bills too: "Sep 2026 · 4 bills" must open exactly those 4 bills.
export const monthLink = (month: string) => `/documents?status=SAVED&month=${month}`;
export const shopLink = (name: string) => `/documents?status=SAVED&q=${encodeURIComponent(name)}`;
