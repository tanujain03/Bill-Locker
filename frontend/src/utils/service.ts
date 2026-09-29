import type { ServiceRecord } from '@/types';
import { daysUntil } from './date';

/** Overdue = danger, due within a week = warning. */
export function dueTone(date: string, from: Date = new Date()): 'danger' | 'warning' | 'neutral' {
  const days = daysUntil(date, from);
  if (days < 0) return 'danger';
  if (days <= 7) return 'warning';
  return 'neutral';
}

/**
 * The next scheduled service per product: taken from each product's most
 * recent record that has a next-service date, soonest first.
 */
export type ScheduledService = ServiceRecord & { nextServiceDate: string };

export function upcomingServices(records: ServiceRecord[]): ScheduledService[] {
  const latestByProduct = new Map<string, ServiceRecord>();
  for (const record of records) {
    const current = latestByProduct.get(record.productId);
    if (!current || record.serviceDate > current.serviceDate) latestByProduct.set(record.productId, record);
  }
  return [...latestByProduct.values()]
    .filter((record): record is ScheduledService => Boolean(record.nextServiceDate))
    .sort((a, b) => a.nextServiceDate.localeCompare(b.nextServiceDate));
}
