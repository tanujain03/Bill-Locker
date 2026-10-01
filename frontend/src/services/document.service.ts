import { apiClient } from '@/lib/api-client';
import type {
  ConfirmDocumentRequest,
  ConfirmDocumentResponse,
  DocumentDetail,
  DocumentFilters,
  DocumentSummary,
  UploadDocumentParams,
} from '@/types';

export const documentService = {
  async list(filters: DocumentFilters = {}): Promise<DocumentSummary[]> {
    const { data } = await apiClient.get<DocumentSummary[]>('/documents', {
      params: {
        productId: filters.productId || undefined,
        status: filters.status || undefined,
        documentType: filters.documentType || undefined,
      },
    });
    return data;
  },

  async get(id: string): Promise<DocumentDetail> {
    const { data } = await apiClient.get<DocumentDetail>(`/documents/${encodeURIComponent(id)}`);
    return data;
  },

  async upload({ file, documentType, productId, onProgress, signal }: UploadDocumentParams): Promise<DocumentSummary> {
    const form = new FormData();
    form.append('file', file);
    if (documentType) form.append('documentType', documentType);
    if (productId) form.append('productId', productId);

    const { data } = await apiClient.post<DocumentSummary>('/documents/upload', form, {
      signal,
      timeout: 0,
      onUploadProgress: (event) => {
        if (onProgress && event.total) onProgress(Math.round((event.loaded / event.total) * 100));
      },
    });
    return data;
  },

  /** Fetches the file with the auth header so it can be previewed via an object URL. */
  async download(id: string): Promise<Blob> {
    const { data } = await apiClient.get<Blob>(`/documents/${encodeURIComponent(id)}/download`, {
      responseType: 'blob',
      timeout: 0,
    });
    return data;
  },

  async confirm(id: string, body: ConfirmDocumentRequest): Promise<ConfirmDocumentResponse> {
    const { data } = await apiClient.post<ConfirmDocumentResponse>(`/documents/${encodeURIComponent(id)}/confirm`, body);
    return data;
  },

  async reprocess(id: string): Promise<DocumentSummary> {
    const { data } = await apiClient.post<DocumentSummary>(`/documents/${encodeURIComponent(id)}/reprocess`);
    return data;
  },

  async remove(id: string): Promise<void> {
    await apiClient.delete(`/documents/${encodeURIComponent(id)}`);
  },
};
