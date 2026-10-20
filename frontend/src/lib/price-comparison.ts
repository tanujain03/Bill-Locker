import { api } from './api';
export type PriceOffer = {
  title: string; seller: string; price: number; priceText: string; url: string;
  delivery: string; match: 'MODEL_MATCH' | 'POSSIBLE_MATCH'; conditional: boolean; sponsored: boolean; lookupToken?: string | null; directUrl?: string | null;
};
export type PriceComparison = { query: string; model: string; checkedAt: string; cached: boolean; offers: PriceOffer[] };
export type PriceSearchStatus = { configured: boolean; remaining: number; limit: number; dailyLimit: number; provider: string };
export const getPriceSearchStatus = () => api<PriceSearchStatus>('/price-comparison/status');
export const comparePrices = (query: string, model: string) => api<PriceComparison>('/price-comparison/search', { body: { query, model } });
export const getPriceStoreLink = (search: { query: string; model: string }, offer: PriceOffer) =>
  api<{ url: string }>('/price-comparison/store-link', { body: { search, url: offer.url, seller: offer.seller } });
export const RETAILERS = ['Amazon', 'Flipkart', 'Myntra', 'AJIO'] as const;
export function offerRetailer(offer: PriceOffer): string {
  let host = '';
  try { host = new URL(offer.url).hostname.toLowerCase(); } catch { return 'Other'; }
  for (const [retailer, domain] of [['Amazon', 'amazon.in'], ['Flipkart', 'flipkart.com'], ['Myntra', 'myntra.com'], ['AJIO', 'ajio.com']]) {
    if (host === domain || host.endsWith(`.${domain}`)) return retailer;
  }
  const seller = offer.seller.toLowerCase().replace(/^www\./, '');
  return RETAILERS.find(r => seller === r.toLowerCase() || seller === `${r.toLowerCase()}.com` || (r === 'Amazon' && seller === 'amazon.in')) ?? 'Other';
}
export function isGoogleListing(url: string): boolean {
  try { return ['google.com', 'www.google.com', 'google.co.in', 'www.google.co.in'].includes(new URL(url).hostname); }
  catch { return false; }
}
