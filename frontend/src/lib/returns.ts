/**
 * Return windows (task 14): the last day a purchase can be returned, stored on the bill as
 * returnByDate. This file turns that date into words and a state, the same everywhere.
 */
import { formatDate } from './document-form';
import { daysBetween, today } from './expenses';

/** open: more than 3 days left · closing: 3 days or fewer (today counts) · closed: the day has passed. */
export type ReturnState = 'open' | 'closing' | 'closed';

/** The last 3 days count as "closing": the dashboard and 🔔 warn then. */
export const CLOSING_DAYS = 3;

export function returnWindow(returnByDate: string): { state: ReturnState; daysLeft: number } {
  const daysLeft = daysBetween(today(), returnByDate) - 1; // 0 = today is the last day
  return { daysLeft, state: daysLeft < 0 ? 'closed' : daysLeft <= CLOSING_DAYS ? 'closing' : 'open' };
}

/** "Returnable until 18 Oct · 5 days left", "Last day to return: today", "Return window closed on 2 Oct". */
export function returnText(returnByDate: string): string {
  const { state, daysLeft } = returnWindow(returnByDate);
  const date = formatDate(returnByDate);
  if (state === 'closed') return `Return window closed on ${date}`;
  if (daysLeft === 0) return 'Last day to return: today';
  return `Returnable until ${date} · ${daysLeft === 1 ? '1 day' : `${daysLeft} days`} left`;
}

/** Return-window lengths offered on the bill form, counted from the purchase date. */
export const RETURN_PRESETS = [7, 10, 15, 30];
