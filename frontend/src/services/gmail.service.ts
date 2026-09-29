import { apiClient } from '@/lib/api-client';
import type {
  ConnectGmailResponse,
  GmailConnection,
  GmailMessage,
  GmailSettingsRequest,
  ImportGmailResponse,
} from '@/types';

const BASE = '/integrations/gmail';

/**
 * Gmail import. OAuth tokens never reach the browser: the backend runs the
 * consent flow (read-only scope), stores tokens server-side and exposes only
 * the connection status and the bill-like emails it found.
 */
export const gmailService = {
  async connection(): Promise<GmailConnection> {
    const { data } = await apiClient.get<GmailConnection>(BASE);
    return data;
  },

  async connect(): Promise<ConnectGmailResponse> {
    const { data } = await apiClient.post<ConnectGmailResponse>(`${BASE}/connect`);
    return data;
  },

  async disconnect(): Promise<void> {
    await apiClient.delete(BASE);
  },

  async sync(): Promise<GmailConnection> {
    const { data } = await apiClient.post<GmailConnection>(`${BASE}/sync`);
    return data;
  },

  async updateSettings(body: GmailSettingsRequest): Promise<GmailConnection> {
    const { data } = await apiClient.put<GmailConnection>(`${BASE}/settings`, body);
    return data;
  },

  async messages(): Promise<GmailMessage[]> {
    const { data } = await apiClient.get<GmailMessage[]>(`${BASE}/messages`);
    return data;
  },

  async importMessages(messageIds: string[]): Promise<ImportGmailResponse> {
    const { data } = await apiClient.post<ImportGmailResponse>(`${BASE}/import`, { messageIds });
    return data;
  },

  async ignoreMessage(id: string): Promise<GmailMessage> {
    const { data } = await apiClient.post<GmailMessage>(`${BASE}/messages/${encodeURIComponent(id)}/ignore`);
    return data;
  },
};
