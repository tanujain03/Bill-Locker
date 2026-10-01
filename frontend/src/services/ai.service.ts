import { apiClient } from '@/lib/api-client';
import type { ChatRequest, ChatResponse, SearchResponse } from '@/types';

export const aiService = {
  async chat(body: ChatRequest): Promise<ChatResponse> {
    const { data } = await apiClient.post<ChatResponse>('/ai/chat', body);
    return data;
  },

  async search(query: string): Promise<SearchResponse> {
    const { data } = await apiClient.post<SearchResponse>('/ai/search', { query });
    return data;
  },
};
