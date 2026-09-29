import { Download, ExternalLink, ImageOff } from 'lucide-react';
import { useMemo } from 'react';
import { Button } from '@/components/ui/Button';
import { Skeleton } from '@/components/ui/feedback';
import { useDocumentFile } from '@/hooks/useDocuments';
import { useObjectUrl } from '@/hooks/useObjectUrl';
import { cn } from '@/lib/cn';
import type { DocumentSummary } from '@/types';
import { isImageMime, isPdfMime, saveBlob } from '@/utils/file';

interface DocumentPreviewProps {
  document: Pick<DocumentSummary, 'id' | 'fileName' | 'mimeType'>;
  className?: string;
}

/**
 * Inline preview of an uploaded bill. The file is fetched with the auth header
 * and shown through an object URL (images in <img>, PDFs in the browser viewer).
 */
export function DocumentPreview({ document, className }: DocumentPreviewProps) {
  const fileQuery = useDocumentFile(document.id);
  // Re-type the blob so PDFs render inline even if the server sent a generic type.
  const typedBlob = useMemo(() => {
    const blob = fileQuery.data;
    if (!blob) return null;
    return blob.type === document.mimeType ? blob : new Blob([blob], { type: document.mimeType });
  }, [fileQuery.data, document.mimeType]);
  const url = useObjectUrl(typedBlob);

  if (fileQuery.isPending) {
    return <Skeleton className={cn('aspect-[3/4] w-full rounded-xl', className)} />;
  }

  const download = typedBlob ? () => saveBlob(typedBlob, document.fileName) : undefined;

  if (fileQuery.isError || !url) {
    return (
      <PreviewFallback
        className={className}
        message="Preview is not available for this document."
        onRetry={() => void fileQuery.refetch()}
      />
    );
  }

  if (isImageMime(document.mimeType)) {
    return (
      <div className={cn('overflow-hidden rounded-xl border border-slate-200 bg-slate-100', className)}>
        <img src={url} alt={`Preview of ${document.fileName}`} className="mx-auto max-h-[75vh] w-full object-contain" />
      </div>
    );
  }

  if (isPdfMime(document.mimeType)) {
    return (
      <div className={cn('space-y-2', className)}>
        <iframe
          title={`Preview of ${document.fileName}`}
          src={url}
          className="h-[65vh] min-h-96 w-full rounded-xl border border-slate-200 bg-slate-100"
        />
        <div className="flex flex-wrap gap-2">
          <Button
            size="sm"
            variant="secondary"
            leftIcon={<ExternalLink className="size-3.5" aria-hidden />}
            onClick={() => window.open(url, '_blank', 'noopener')}
          >
            Open in new tab
          </Button>
          {download && (
            <Button size="sm" variant="ghost" leftIcon={<Download className="size-3.5" aria-hidden />} onClick={download}>
              Download
            </Button>
          )}
        </div>
      </div>
    );
  }

  return <PreviewFallback className={className} message="This file type cannot be previewed." onDownload={download} />;
}

function PreviewFallback({
  message,
  className,
  onRetry,
  onDownload,
}: {
  message: string;
  className?: string;
  onRetry?: () => void;
  onDownload?: () => void;
}) {
  return (
    <div
      className={cn(
        'flex aspect-[3/4] w-full flex-col items-center justify-center gap-3 rounded-xl border border-dashed border-slate-300 bg-slate-50 p-6 text-center',
        className,
      )}
    >
      <ImageOff className="size-8 text-slate-400" aria-hidden />
      <p className="text-sm text-slate-500">{message}</p>
      <div className="flex gap-2">
        {onRetry && (
          <Button size="sm" variant="secondary" onClick={onRetry}>
            Try again
          </Button>
        )}
        {onDownload && (
          <Button size="sm" variant="secondary" leftIcon={<Download className="size-3.5" aria-hidden />} onClick={onDownload}>
            Download
          </Button>
        )}
      </div>
    </div>
  );
}
