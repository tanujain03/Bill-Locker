import type { DocumentSummary } from './document';
import type { ServiceRecord } from './service-record';
import type { Warranty, WarrantyStats } from './warranty';

export interface CategorySpending {
  categoryName: string;
  categorySlug: string | null;
  amount: number;
}

export interface DashboardSummary {
  totalProducts: number;
  totalDocuments: number;
  /** Documents waiting for the user to review the AI extraction. */
  documentsToReview: number;
  totalSpending: number;
  currency: string;
  warranties: WarrantyStats;
  spendingByCategory: CategorySpending[];
  /** Warranties expiring within the next 90 days, soonest first. */
  upcomingExpirations: Warranty[];
  /** Service records whose next service date is within 60 days (or overdue). */
  upcomingServices: ServiceRecord[];
  recentDocuments: DocumentSummary[];
}
