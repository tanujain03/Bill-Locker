import { useCallback, useState } from 'react';
import { useNavigate } from 'react-router';
import { ApiError, errorMessage } from './api';
import { uploadDocument } from './documents';
import { useFeedback } from './feedback-context';

/** The file types the backend accepts (it checks the real bytes too). */
export const UPLOAD_ACCEPT = '.pdf,.jpg,.jpeg,.png,.webp';

/**
 * Uploading a bill from anywhere (top bar, phone tab bar, Documents, drag and drop):
 * upload, then open it with ?read=1 so the document page reads it with AI at once.
 */
export function useUploadBill() {
  const navigate = useNavigate();
  const { toast } = useFeedback();
  const [uploading, setUploading] = useState(false);

  const upload = useCallback(
    async (file: File | undefined) => {
      if (!file) return;
      setUploading(true);
      try {
        const document = await uploadDocument(file);
        navigate(`/documents/${document.id}?read=1`);
      } catch (error) {
        if (error instanceof ApiError && error.code === 'DUPLICATE_FILE' && error.documentId) {
          // The very same file is already in the locker: open that one instead of a copy.
          navigate(`/documents/${error.documentId}`);
          toast('You already uploaded this file, so here it is.', 'info');
          return;
        }
        toast(errorMessage(error), 'error'); // e.g. "Only PDF, JPG, PNG or WebP files"
      } finally {
        setUploading(false);
      }
    },
    [navigate, toast],
  );

  return { upload, uploading };
}
