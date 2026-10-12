import { useEffect, useState } from 'react';
import { errorMessage } from '../../lib/api';
import { downloadDocument } from '../../lib/documents';
import { useFeedback } from '../../lib/feedback-context';
import { shareOnWhatsApp } from '../../lib/share';
import { buttonClass } from '../Button';
import { Alert } from '../FormParts';

/**
 * Shows the stored file. The download needs the login token, so a plain
 * <img src="/api/..."> can't fetch it: we download it with the token, turn the
 * bytes into a temporary blob: URL, and show that.
 *
 * Above it, a slim bar with the file name and (once the bill is saved) "WhatsApp".
 * (A PDF's download/zoom icons belong to the browser's own PDF viewer; the page can't
 * add buttons there, so ours sit just above it.)
 */
export function DocumentPreview({ id, contentType, fileName, shareText }: {
  id: string;
  contentType: string;
  fileName: string;
  /** The message sent with the file. Missing until the bill is saved: no button then. */
  shareText?: string;
}) {
  const { toast } = useFeedback();
  const [file, setFile] = useState<{ blob: Blob; url: string } | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [sharing, setSharing] = useState(false);

  useEffect(() => {
    let objectUrl: string | null = null;
    let current = true;
    downloadDocument(id)
      .then((blob) => {
        if (!current) return;
        objectUrl = URL.createObjectURL(blob);
        setFile({ blob, url: objectUrl });
      })
      .catch((e) => current && setError(errorMessage(e)));
    return () => {
      current = false;
      if (objectUrl) URL.revokeObjectURL(objectUrl); // free the memory when leaving the page
    };
  }, [id]);

  async function share() {
    if (!file || !shareText) return;
    setSharing(true);
    try {
      // The same bytes the preview shows, as a named file WhatsApp can attach.
      const result = await shareOnWhatsApp(new File([file.blob], fileName, { type: contentType }), shareText);
      if (result === 'link') {
        toast('WhatsApp opened with the bill’s details. The file was downloaded: attach it there with 📎.', 'info');
      }
    } finally {
      setSharing(false);
    }
  }

  if (error) return <Alert tone="error">{error}</Alert>;
  if (!file) return <div className="h-[70vh] animate-pulse rounded-xl bg-slate-200 lg:h-[calc(100dvh-2rem)]" aria-label="Loading preview" />;

  const viewer =
    contentType === 'application/pdf' ? (
      <iframe src={file.url} title={fileName} className="h-[70vh] w-full bg-white lg:h-[calc(100dvh-6rem)]" />
    ) : (
      <img src={file.url} alt={fileName} className="max-h-[70vh] w-full bg-white object-contain lg:max-h-[calc(100dvh-6rem)]" />
    );

  return (
    <div className="overflow-hidden rounded-xl border border-slate-200 bg-white shadow-xs">
      <div className="flex items-center justify-between gap-3 border-b border-slate-200 px-3 py-2">
        <p className="min-w-0 truncate text-sm text-slate-600" title={fileName}>
          {fileName}
        </p>
        {shareText && (
          <button
            type="button"
            onClick={share}
            disabled={sharing}
            title="Share this bill on WhatsApp"
            className={`${buttonClass({ size: 'sm' })} shrink-0 text-[#128C7E] hover:bg-[#25D366]/10`}
          >
            <WhatsAppIcon />
            WhatsApp
          </button>
        )}
      </div>
      {viewer}
    </div>
  );
}

/** WhatsApp's logo (lucide has no brand icons), in its brand green. */
function WhatsAppIcon() {
  return (
    <svg viewBox="0 0 24 24" className="size-4 shrink-0" fill="#25D366" aria-hidden>
      <path d="M17.47 14.38c-.3-.15-1.76-.87-2.03-.97-.27-.1-.47-.15-.67.15-.2.3-.77.97-.94 1.17-.17.2-.35.22-.65.07-.3-.15-1.26-.46-2.4-1.48-.89-.79-1.49-1.77-1.66-2.07-.17-.3-.02-.46.13-.61.13-.13.3-.35.45-.52.15-.17.2-.3.3-.5.1-.2.05-.37-.03-.52-.07-.15-.67-1.62-.92-2.22-.24-.58-.49-.5-.67-.51h-.57c-.2 0-.52.07-.79.37-.27.3-1.04 1.02-1.04 2.48s1.07 2.88 1.21 3.08c.15.2 2.1 3.2 5.08 4.49.71.31 1.26.49 1.69.63.71.23 1.36.2 1.87.12.57-.09 1.76-.72 2.01-1.41.25-.69.25-1.29.17-1.41-.07-.13-.27-.2-.57-.35M12.04 21.5h-.01a9.45 9.45 0 0 1-4.82-1.32l-.35-.21-3.58.94.96-3.49-.23-.36a9.43 9.43 0 0 1-1.45-5.03c0-5.22 4.25-9.47 9.48-9.47 2.53 0 4.91.99 6.7 2.78a9.4 9.4 0 0 1 2.77 6.7c0 5.22-4.25 9.46-9.47 9.46M20.1 3.9A11.33 11.33 0 0 0 12.04.56C5.76.56.65 5.67.65 11.95c0 2.01.52 3.97 1.52 5.7L.56 23.44l5.93-1.56a11.36 11.36 0 0 0 5.44 1.39h.01c6.28 0 11.39-5.11 11.39-11.39 0-3.04-1.18-5.9-3.33-8.05" />
    </svg>
  );
}
