import { useContext } from 'react';
import { UploadContext } from './upload-context';

export const UPLOAD_ACCEPT = '.pdf,.jpg,.jpeg,.png,.webp';

/** Every upload entry point shares the same queue and busy state. */
export function useUploadBill() {
  const context = useContext(UploadContext);
  if (!context) throw new Error('useUploadBill must be used inside UploadProvider');
  return context;
}
