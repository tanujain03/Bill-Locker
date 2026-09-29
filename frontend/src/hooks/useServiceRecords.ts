import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { invalidateLockerData } from '@/lib/invalidate';
import { queryKeys } from '@/lib/query-keys';
import { serviceRecordService } from '@/services/service-record.service';
import type { ServiceRecordInput } from '@/types';

export function useServiceRecords(productId?: string) {
  return useQuery({
    queryKey: queryKeys.serviceRecords.list(productId),
    queryFn: () => serviceRecordService.list(productId),
  });
}

export function useSaveServiceRecord() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ id, input }: { id?: string; input: ServiceRecordInput }) =>
      id ? serviceRecordService.update(id, input) : serviceRecordService.create(input),
    onSuccess: () => invalidateLockerData(queryClient),
  });
}

export function useDeleteServiceRecord() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (id: string) => serviceRecordService.remove(id),
    onSuccess: () => invalidateLockerData(queryClient),
  });
}
