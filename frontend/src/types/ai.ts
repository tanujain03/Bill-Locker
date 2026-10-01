import type { ISODate, ISODateTime } from './api';
import type { Product } from './product';
import type { WarrantyStatus } from './warranty';

export type ChatRole = 'USER' | 'ASSISTANT';

/** A product or document the assistant used to build its answer. */
export interface ChatReference {
  type: 'PRODUCT' | 'DOCUMENT';
  id: string;
  title: string;
  subtitle: string | null;
}

export interface ChatMessage {
  id: string;
  role: ChatRole;
  content: string;
  references: ChatReference[];
  createdAt: ISODateTime;
}

export interface ChatRequest {
  message: string;
  sessionId?: string | null;
}

export interface ChatResponse {
  sessionId: string;
  message: ChatMessage;
}

/**
 * Structured filters the AI derives from a natural-language query. The backend
 * executes them with a parameterised query — the LLM never produces SQL.
 */
export interface SearchFilters {
  text?: string | null;
  categorySlug?: string | null;
  brand?: string | null;
  seller?: string | null;
  warrantyStatus?: WarrantyStatus | null;
  daysUntilExpiry?: number | null;
  purchasedAfter?: ISODate | null;
  purchasedBefore?: ISODate | null;
  minPrice?: number | null;
  maxPrice?: number | null;
  sortBy?: 'PURCHASE_DATE' | 'PRICE' | 'WARRANTY_EXPIRY' | null;
  sortDirection?: 'ASC' | 'DESC' | null;
  limit?: number | null;
}

export interface SearchRequest {
  query: string;
}

export interface SearchResponse {
  query: string;
  filters: SearchFilters;
  /** Human-readable summary of how the query was interpreted. */
  explanation: string;
  results: Product[];
}
