import type { QueryClient } from '@tanstack/react-query';

const LOCKER_ROOTS = new Set(['products', 'documents', 'warranties', 'service-records', 'dashboard', 'notifications', 'ai']);

/**
 * Refreshes everything derived from the user's purchases after a write.
 * Downloaded files (`['documents', 'file', id]`) are immutable and kept.
 */
export function invalidateLockerData(queryClient: QueryClient): Promise<void> {
  return queryClient.invalidateQueries({
    predicate: ({ queryKey }) =>
      typeof queryKey[0] === 'string' && LOCKER_ROOTS.has(queryKey[0]) && queryKey[1] !== 'file',
  });
}
