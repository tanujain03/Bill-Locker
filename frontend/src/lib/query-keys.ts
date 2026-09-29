import type { DocumentFilters, ProductFilters } from '@/types';

/** Central query-key factory so invalidation stays consistent. */
export const queryKeys = {
  me: ['auth', 'me'] as const,
  categories: ['categories'] as const,
  dashboard: ['dashboard'] as const,
  products: {
    all: ['products'] as const,
    list: (filters: ProductFilters) => ['products', 'list', filters] as const,
    detail: (id: string) => ['products', 'detail', id] as const,
  },
  documents: {
    all: ['documents'] as const,
    list: (filters: DocumentFilters) => ['documents', 'list', filters] as const,
    detail: (id: string) => ['documents', 'detail', id] as const,
    file: (id: string) => ['documents', 'file', id] as const,
  },
  warranties: {
    all: ['warranties'] as const,
  },
  serviceRecords: {
    all: ['service-records'] as const,
    list: (productId?: string) => ['service-records', 'list', productId ?? 'all'] as const,
  },
  notifications: {
    all: ['notifications'] as const,
    list: ['notifications', 'list'] as const,
    unreadCount: ['notifications', 'unread-count'] as const,
  },
  ai: {
    search: (query: string) => ['ai', 'search', query] as const,
  },
  gmail: {
    all: ['gmail'] as const,
    connection: ['gmail', 'connection'] as const,
    messages: ['gmail', 'messages'] as const,
  },
};
