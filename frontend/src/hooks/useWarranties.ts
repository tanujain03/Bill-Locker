import { useQuery } from '@tanstack/react-query';
import { queryKeys } from '@/lib/query-keys';
import { dashboardService, warrantyService } from '@/services/warranty.service';

export function useWarranties() {
  return useQuery({
    queryKey: queryKeys.warranties.all,
    queryFn: warrantyService.list,
  });
}

export function useDashboard() {
  return useQuery({
    queryKey: queryKeys.dashboard,
    queryFn: dashboardService.summary,
  });
}
