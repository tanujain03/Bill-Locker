import { useMutation, useQuery } from '@tanstack/react-query';
import { queryKeys } from '@/lib/query-keys';
import { aiService } from '@/services/ai.service';
import type { ChatRequest } from '@/types';

/** Natural-language product search; runs only for a non-empty query. */
export function useAiSearch(query: string) {
  const trimmed = query.trim();
  return useQuery({
    queryKey: queryKeys.ai.search(trimmed),
    queryFn: () => aiService.search(trimmed),
    enabled: trimmed.length > 0,
    staleTime: 60_000,
  });
}

export function useChat() {
  return useMutation({
    mutationFn: (request: ChatRequest) => aiService.chat(request),
  });
}
