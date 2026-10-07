import { useEffect, useState } from 'react';
import { errorMessage } from '../../lib/api';
import { downloadDocument } from '../../lib/documents';
import { Alert } from '../FormParts';

/**
 * Shows the stored file. The download needs the login token, so a plain
 * <img src="/api/..."> can't fetch it: we download it with the token, turn the
 * bytes into a temporary blob: URL, and show that.
 */
export function DocumentPreview({ id, contentType, fileName }: { id: string; contentType: string; fileName: string }) {
  const [url, setUrl] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    let objectUrl: string | null = null;
    let current = true;
    downloadDocument(id)
      .then((blob) => {
        if (!current) return;
        objectUrl = URL.createObjectURL(blob);
        setUrl(objectUrl);
      })
      .catch((e) => current && setError(errorMessage(e)));
    return () => {
      current = false;
      if (objectUrl) URL.revokeObjectURL(objectUrl); // free the memory when leaving the page
    };
  }, [id]);

  if (error) return <Alert tone="error">{error}</Alert>;
  if (!url) return <div className="h-[70vh] animate-pulse rounded-xl bg-slate-200 lg:h-[calc(100dvh-2rem)]" aria-label="Loading preview" />;
  return contentType === 'application/pdf' ? (
    <iframe src={url} title={fileName} className="h-[70vh] w-full rounded-xl border border-slate-200 bg-white shadow-xs lg:h-[calc(100dvh-2rem)]" />
  ) : (
    <img src={url} alt={fileName} className="max-h-[70vh] w-full rounded-xl shadow-xs lg:max-h-[calc(100dvh-2rem)] border border-slate-200 bg-white object-contain" />
  );
}
