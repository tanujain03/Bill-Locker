import { apiClient } from '@/lib/api-client';
import type { ServiceRecord, ServiceRecordInput } from '@/types';

export const serviceRecordService = {
  async list(productId?: string): Promise<ServiceRecord[]> {
    const { data } = await apiClient.get<ServiceRecord[]>('/service-records', {
      params: { productId: productId || undefined },
    });
    return data;
  },

  async create(body: ServiceRecordInput): Promise<ServiceRecord> {
    const { data } = await apiClient.post<ServiceRecord>('/service-records', body);
    return data;
  },

  async update(id: string, body: ServiceRecordInput): Promise<ServiceRecord> {
    const { data } = await apiClient.put<ServiceRecord>(`/service-records/${encodeURIComponent(id)}`, body);
    return data;
  },

  async remove(id: string): Promise<void> {
    await apiClient.delete(`/service-records/${encodeURIComponent(id)}`);
  },
};
