import { ArrowRight, Download, Package, Trash } from 'lucide-react';
import { useState } from 'react';
import { Link } from 'react-router';
import { Button, ButtonLink } from '@/components/ui/Button';
import { ConfirmDialog } from '@/components/ui/ConfirmDialog';
import { useToast } from '@/components/ui/toast-context';
import { useDeleteDocument } from '@/hooks/useDocuments';
import { getErrorMessage } from '@/lib/api-client';
import { cn } from '@/lib/cn';
import { documentService } from '@/services/document.service';
import type { DocumentSummary } from '@/types';
import { saveBlob } from '@/utils/file';
import { formatFileSize, formatRelativeTime } from '@/utils/format';
import { DOCUMENT_TYPE_LABELS, needsReview } from '@/utils/labels';
import { DocumentStatusBadge, FileTypeIcon, SourceBadge } from './DocumentBadges';

interface DocumentListProps {
  documents: DocumentSummary[];
  /** `compact` hides download/delete (dashboard widgets). */
  variant?: 'full' | 'compact';
  showProduct?: boolean;
  className?: string;
}

export function DocumentList({ documents, variant = 'full', showProduct = true, className }: DocumentListProps) {
  const [pendingDelete, setPendingDelete] = useState<DocumentSummary | null>(null);
  const deleteDocument = useDeleteDocument();
  const toast = useToast();

  async function download(document: DocumentSummary) {
    try {
      saveBlob(await documentService.download(document.id), document.fileName);
    } catch (error) {
      toast.error('Download failed', getErrorMessage(error));
    }
  }

  async function confirmDelete() {
    if (!pendingDelete) return;
    try {
      await deleteDocument.mutateAsync(pendingDelete.id);
      toast.success('Document deleted', pendingDelete.fileName);
      setPendingDelete(null);
    } catch (error) {
      toast.error('Could not delete the document', getErrorMessage(error));
    }
  }

  return (
    <>
      <ul className={cn('divide-y divide-slate-100', className)}>
        {documents.map((document) => (
          <li key={document.id} className="flex flex-col gap-3 px-5 py-4 sm:flex-row sm:items-center sm:gap-4 sm:px-6">
            <div className="flex min-w-0 flex-1 items-start gap-3">
              <FileTypeIcon mimeType={document.mimeType} />
              <div className="min-w-0">
                <Link
                  to={`/documents/${document.id}`}
                  className="block truncate text-sm font-medium text-slate-900 hover:text-brand-700"
                >
                  {document.fileName}
                </Link>
                <p className="mt-0.5 flex flex-wrap items-center gap-x-1.5 gap-y-1 text-xs text-slate-500">
                  <span>{DOCUMENT_TYPE_LABELS[document.documentType]}</span>
                  <span aria-hidden>·</span>
                  <span>{formatFileSize(document.fileSize)}</span>
                  <span aria-hidden>·</span>
                  <span>{formatRelativeTime(document.createdAt)}</span>
                  <SourceBadge source={document.source} />
                </p>
                {showProduct && document.productId && document.productName && (
                  <Link
                    to={`/products/${document.productId}`}
                    className="mt-1 inline-flex max-w-full items-center gap-1 truncate text-xs font-medium text-brand-700 hover:text-brand-800"
                  >
                    <Package className="size-3 shrink-0" aria-hidden />
                    {document.productName}
                  </Link>
                )}
              </div>
            </div>

            <div className="flex shrink-0 items-center gap-2 pl-13 sm:pl-0">
              <DocumentStatusBadge status={document.processingStatus} />
              {needsReview(document.processingStatus) && (
                <ButtonLink
                  to={`/documents/${document.id}`}
                  size="sm"
                  variant="subtle"
                  rightIcon={<ArrowRight className="size-3.5" aria-hidden />}
                >
                  Review
                </ButtonLink>
              )}
              {variant === 'full' && (
                <>
                  <Button
                    variant="ghost"
                    size="icon-sm"
                    aria-label={`Download ${document.fileName}`}
                    title="Download"
                    onClick={() => void download(document)}
                  >
                    <Download className="size-4" aria-hidden />
                  </Button>
                  <Button
                    variant="ghost"
                    size="icon-sm"
                    aria-label={`Delete ${document.fileName}`}
                    title="Delete"
                    className="hover:bg-rose-50 hover:text-rose-600"
                    onClick={() => setPendingDelete(document)}
                  >
                    <Trash className="size-4" aria-hidden />
                  </Button>
                </>
              )}
            </div>
          </li>
        ))}
      </ul>

      <ConfirmDialog
        open={pendingDelete !== null}
        onClose={() => setPendingDelete(null)}
        onConfirm={() => void confirmDelete()}
        loading={deleteDocument.isPending}
        title="Delete this document?"
        confirmLabel="Delete document"
        message={
          <>
            <strong className="font-medium text-slate-900">{pendingDelete?.fileName}</strong> will be permanently removed
            from your locker. Products and warranties already saved from it are kept.
          </>
        }
      />
    </>
  );
}
