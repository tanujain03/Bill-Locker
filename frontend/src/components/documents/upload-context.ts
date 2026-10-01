import { createContext, useContext } from 'react';
import type { DocumentType } from '@/types';

export interface OpenUploadOptions {
  /** Attach the uploaded documents to this product. */
  productId?: string;
  productName?: string;
  documentType?: DocumentType;
  /** Files dropped elsewhere (e.g. the documents page) to start uploading right away. */
  files?: File[];
}

export interface UploadContextValue {
  openUpload: (options?: OpenUploadOptions) => void;
}

export const UploadContext = createContext<UploadContextValue | null>(null);

export function useUpload(): UploadContextValue {
  const context = useContext(UploadContext);
  if (!context) throw new Error('useUpload must be used inside <UploadProvider>');
  return context;
}
