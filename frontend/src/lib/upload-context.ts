import { createContext } from 'react';

export const UploadContext = createContext<{
  upload: (files: File | File[] | undefined) => Promise<void>;
  uploading: boolean;
} | null>(null);
