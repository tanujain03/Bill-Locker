import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useEffect, useRef } from 'react';
import { invalidateLockerData } from '@/lib/invalidate';
import { queryKeys } from '@/lib/query-keys';
import { documentService } from '@/services/document.service';
import type { ConfirmDocumentRequest, DocumentFilters, ProcessingStatus } from '@/types';
import { isProcessing, needsReview } from '@/utils/labels';

const DETAIL_POLL_MS = 1500;
const LIST_POLL_MS = 2500;

export function useDocuments(filters: DocumentFilters = {}) {
  return useQuery({
    queryKey: queryKeys.documents.list(filters),
    queryFn: () => documentService.list(filters),
    // Keep the list fresh while anything is still being processed by the AI pipeline.
    refetchInterval: (query) =>
      query.state.data?.some((document) => isProcessing(document.processingStatus)) ? LIST_POLL_MS : false,
  });
}

/**
 * App-wide view of the document pipeline (used by the sidebar): polls only
 * while something is processing and refreshes dashboard, lists and
 * notifications as soon as a document finishes — even if no page is watching it.
 */
export function useDocumentActivity() {
  const { data: documents = [] } = useDocuments();
  const queryClient = useQueryClient();
  const processing = documents.filter((document) => isProcessing(document.processingStatus)).length;
  const previous = useRef(processing);

  useEffect(() => {
    if (processing < previous.current) void invalidateLockerData(queryClient);
    previous.current = processing;
  }, [processing, queryClient]);

  return {
    processing,
    toReview: documents.filter((document) => needsReview(document.processingStatus)).length,
  };
}

/** Refreshes dependent data once when a document leaves the processing states. */
export function useProcessingTransition(status: ProcessingStatus | undefined) {
  const queryClient = useQueryClient();
  const previous = useRef(status);

  useEffect(() => {
    if (isProcessing(previous.current) && status && !isProcessing(status)) {
      void invalidateLockerData(queryClient);
    }
    previous.current = status;
  }, [status, queryClient]);
}

export function useDocument(id: string | undefined) {
  const query = useQuery({
    queryKey: queryKeys.documents.detail(id ?? ''),
    queryFn: () => documentService.get(id as string),
    enabled: Boolean(id),
    refetchInterval: (q) => (isProcessing(q.state.data?.processingStatus) ? DETAIL_POLL_MS : false),
  });
  useProcessingTransition(query.data?.processingStatus);
  return query;
}

/** The original file as a Blob (fetched with the auth header for previews). */
export function useDocumentFile(id: string | undefined, enabled = true) {
  return useQuery({
    queryKey: queryKeys.documents.file(id ?? ''),
    queryFn: () => documentService.download(id as string),
    enabled: Boolean(id) && enabled,
    staleTime: Infinity,
    gcTime: 5 * 60_000,
    retry: false,
  });
}

export function useConfirmDocument(id: string) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (request: ConfirmDocumentRequest) => documentService.confirm(id, request),
    onSuccess: () => invalidateLockerData(queryClient),
  });
}

export function useReprocessDocument(id: string) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: () => documentService.reprocess(id),
    onSuccess: () => invalidateLockerData(queryClient),
  });
}

export function useDeleteDocument() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (id: string) => documentService.remove(id),
    onSuccess: (_data, id) => {
      queryClient.removeQueries({ queryKey: queryKeys.documents.detail(id) });
      queryClient.removeQueries({ queryKey: queryKeys.documents.file(id) });
      return invalidateLockerData(queryClient);
    },
  });
}
