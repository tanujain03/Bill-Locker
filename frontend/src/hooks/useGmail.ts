import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useEffect, useRef } from 'react';
import { invalidateLockerData } from '@/lib/invalidate';
import { queryKeys } from '@/lib/query-keys';
import { gmailService } from '@/services/gmail.service';

/** `enabled: false` skips the request (e.g. while the backend has no Gmail import). */
export function useGmailConnection(enabled = true) {
  const queryClient = useQueryClient();
  const query = useQuery({
    queryKey: queryKeys.gmail.connection,
    queryFn: gmailService.connection,
    enabled,
    refetchInterval: (q) => (q.state.data?.syncStatus === 'SYNCING' ? 1500 : false),
  });

  // When a scan finishes, pull in the newly found emails and notifications.
  const syncStatus = query.data?.syncStatus;
  const previous = useRef(syncStatus);
  useEffect(() => {
    if (previous.current === 'SYNCING' && syncStatus && syncStatus !== 'SYNCING') {
      void queryClient.invalidateQueries({ queryKey: queryKeys.gmail.messages });
      void queryClient.invalidateQueries({ queryKey: queryKeys.notifications.all });
    }
    previous.current = syncStatus;
  }, [syncStatus, queryClient]);

  return query;
}

export function useGmailMessages(enabled: boolean) {
  return useQuery({
    queryKey: queryKeys.gmail.messages,
    queryFn: gmailService.messages,
    enabled,
  });
}

export function useConnectGmail() {
  return useMutation({ mutationFn: gmailService.connect });
}

export function useDisconnectGmail() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: gmailService.disconnect,
    onSuccess: () => queryClient.invalidateQueries({ queryKey: queryKeys.gmail.all }),
  });
}

export function useSyncGmail() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: gmailService.sync,
    onSuccess: (connection) => queryClient.setQueryData(queryKeys.gmail.connection, connection),
  });
}

export function useUpdateGmailSettings() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: gmailService.updateSettings,
    onSuccess: (connection) => queryClient.setQueryData(queryKeys.gmail.connection, connection),
  });
}

export function useImportGmailMessages() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (messageIds: string[]) => gmailService.importMessages(messageIds),
    onSuccess: async () => {
      await queryClient.invalidateQueries({ queryKey: queryKeys.gmail.messages });
      await invalidateLockerData(queryClient);
    },
  });
}

export function useIgnoreGmailMessage() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (id: string) => gmailService.ignoreMessage(id),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: queryKeys.gmail.messages }),
  });
}
