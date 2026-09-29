import { apiClient } from '@/lib/api-client';
import type { DashboardSummary, Warranty } from '@/types';

export const warrantyService = {
  async list(): Promise<Warranty[]> {
    const { data } = await apiClient.get<Warranty[]>('/warranties');
    return data;
  },
};

export const dashboardService = {
  async summary(): Promise<DashboardSummary> {
    const { data } = await apiClient.get<DashboardSummary>('/dashboard/summary');
    return data;
  },
};
