import { ArrowRight, CircleCheck, Download, FileQuestion, PencilLine, RotateCw, ScanText, Trash, TriangleAlert } from 'lucide-react';
import { useState } from 'react';
import { useNavigate, useParams } from 'react-router';
import { DocumentStatusBadge, DocumentTypeBadge, SourceBadge } from '@/components/documents/DocumentBadges';
import { DocumentPreview } from '@/components/documents/DocumentPreview';
import { ExtractionReview } from '@/components/documents/ExtractionReview';
import { stepForDocument } from '@/components/documents/processing';
import { ProcessingSteps } from '@/components/documents/ProcessingSteps';
import { Button, ButtonLink } from '@/components/ui/Button';
import { Card, CardBody, CardHeader } from '@/components/ui/Card';
import { ConfirmDialog } from '@/components/ui/ConfirmDialog';
import { EmptyState, ErrorState, PageLoader } from '@/components/ui/feedback';
import { PageHeader } from '@/components/ui/PageHeader';
import { useToast } from '@/components/ui/toast-context';
import { useDeleteDocument, useDocument, useReprocessDocument } from '@/hooks/useDocuments';
import { useDocumentTitle } from '@/hooks/useDocumentTitle';
import { useMediaQuery } from '@/hooks/useMediaQuery';
import { ApiError, getErrorMessage } from '@/lib/api-client';
import { documentService } from '@/services/document.service';
import type { DocumentDetail } from '@/types';
import { saveBlob } from '@/utils/file';
import { formatDateTime, formatFileSize } from '@/utils/format';
import { isProcessing, needsReview } from '@/utils/labels';

export function DocumentDetailPage() {
  const { id } = useParams<{ id: string }>();
  const document = useDocument(id);
  useDocumentTitle(document.data?.fileName ?? 'Document');

  if (document.isPending) return <PageLoader />;
  if (document.isError) {
    const notFound = document.error instanceof ApiError && document.error.status === 404;
    return (
      <Card className="mt-4">
        {notFound ? (
          <EmptyState
            icon={<FileQuestion aria-hidden />}
            title="Document not found"
            description="It may have been deleted, or it belongs to another account."
            action={<ButtonLink to="/documents">Back to documents</ButtonLink>}
          />
        ) : (
          <ErrorState title="Could not load this document" error={document.error} onRetry={() => void document.refetch()} />
        )}
      </Card>
    );
  }
  return <DocumentDetailView document={document.data} />;
}

function DocumentDetailView({ document }: { document: DocumentDetail }) {
  const navigate = useNavigate();
  const toast = useToast();
  const deleteDocument = useDeleteDocument();
  const [deleteOpen, setDeleteOpen] = useState(false);
  const [manualEntry, setManualEntry] = useState(false);
  const isDesktop = useMediaQuery('(min-width: 1024px)');
  const status = document.processingStatus;
  const reviewing = needsReview(status) || (status === 'FAILED' && manualEntry);

  async function download() {
    try {
      saveBlob(await documentService.download(document.id), document.fileName);
    } catch (error) {
      toast.error('Download failed', getErrorMessage(error));
    }
  }

  async function confirmDelete() {
    try {
      await deleteDocument.mutateAsync(document.id);
      toast.success('Document deleted', document.fileName);
      navigate('/documents', { replace: true });
    } catch (error) {
      toast.error('Could not delete the document', getErrorMessage(error));
    }
  }

  return (
    <>
      <PageHeader
        back={{ to: '/documents', label: 'Documents' }}
        title={<span className="break-all">{document.fileName}</span>}
        description={`${formatFileSize(document.fileSize)} · added ${formatDateTime(document.createdAt)}`}
        eyebrow={
          <div className="flex flex-wrap items-center gap-2">
            <DocumentStatusBadge status={status} />
            <DocumentTypeBadge type={document.documentType} />
            <SourceBadge source={document.source} />
          </div>
        }
        actions={
          <>
            <Button variant="secondary" onClick={() => void download()} leftIcon={<Download className="size-4" aria-hidden />}>
              Download
            </Button>
            <Button
              variant="ghost"
              className="text-slate-500 hover:bg-rose-50 hover:text-rose-600"
              onClick={() => setDeleteOpen(true)}
              leftIcon={<Trash className="size-4" aria-hidden />}
            >
              Delete
            </Button>
          </>
        }
      />

      <div className="grid gap-6 lg:grid-cols-[minmax(0,5fr)_minmax(0,7fr)]">
        <div className="order-2 lg:order-1">
          <div className="lg:sticky lg:top-24">
            {isDesktop ? (
              <DocumentPreview document={document} />
            ) : (
              // On phones the preview is collapsible so the review form comes first.
              <details className="group" open={!reviewing}>
                <summary className="mb-3 cursor-pointer list-none text-sm font-medium text-brand-700">
                  <span className="group-open:hidden">Show document preview</span>
                  <span className="hidden group-open:inline">Hide document preview</span>
                </summary>
                <DocumentPreview document={document} />
              </details>
            )}
          </div>
        </div>

        <div className="order-1 min-w-0 lg:order-2">
          {isProcessing(status) ? (
            <ProcessingPanel document={document} />
          ) : reviewing ? (
            <ExtractionReview document={document} />
          ) : status === 'CONFIRMED' ? (
            <ConfirmedPanel document={document} />
          ) : status === 'FAILED' ? (
            <FailedPanel document={document} onManualEntry={() => setManualEntry(true)} />
          ) : null}
        </div>
      </div>

      <ConfirmDialog
        open={deleteOpen}
        onClose={() => setDeleteOpen(false)}
        onConfirm={() => void confirmDelete()}
        loading={deleteDocument.isPending}
        title="Delete this document?"
        confirmLabel="Delete document"
        message="The file will be permanently removed from your locker. Products and warranties already saved from it are kept."
      />
    </>
  );
}

function ProcessingPanel({ document }: { document: DocumentDetail }) {
  return (
    <Card>
      <CardHeader
        icon={<ScanText className="size-4 text-brand-600" aria-hidden />}
        title="AI is reading your document"
        description="This usually takes a few seconds. You can leave this page — we’ll notify you when it’s ready."
      />
      <CardBody>
        <ProcessingSteps step={stepForDocument(document)} />
        <ul className="mt-6 space-y-2 text-sm text-slate-600">
          <li>1. Text is extracted from the file (OCR).</li>
          <li>2. AI identifies the product, price, dates, seller and warranty.</li>
          <li>3. You review everything before anything is saved.</li>
        </ul>
      </CardBody>
    </Card>
  );
}

function ConfirmedPanel({ document }: { document: DocumentDetail }) {
  return (
    <Card>
      <div className="flex flex-col items-start gap-4 p-6 sm:flex-row sm:items-center">
        <span className="flex size-12 shrink-0 items-center justify-center rounded-2xl bg-emerald-50 text-emerald-600">
          <CircleCheck className="size-6" aria-hidden />
        </span>
        <div className="flex-1">
          <p className="font-semibold text-slate-900">Saved to your locker</p>
          <p className="text-sm text-slate-500">
            {document.productName
              ? `This document is linked to ${document.productName}.`
              : 'This document was reviewed and saved.'}
          </p>
        </div>
        {document.productId && (
          <ButtonLink to={`/products/${document.productId}`} rightIcon={<ArrowRight className="size-4" aria-hidden />}>
            View product
          </ButtonLink>
        )}
      </div>
      {document.extractedText && (
        <details className="border-t border-slate-100">
          <summary className="cursor-pointer px-6 py-4 text-sm font-medium text-slate-700">View text read from the document</summary>
          <pre className="max-h-72 overflow-auto px-6 pb-5 font-mono text-xs leading-relaxed whitespace-pre-wrap text-slate-600">
            {document.extractedText}
          </pre>
        </details>
      )}
    </Card>
  );
}

function FailedPanel({ document, onManualEntry }: { document: DocumentDetail; onManualEntry: () => void }) {
  const reprocess = useReprocessDocument(document.id);
  const toast = useToast();

  async function retry() {
    try {
      await reprocess.mutateAsync();
      toast.info('Processing restarted', 'We’ll let you know when the document is ready.');
    } catch (error) {
      toast.error('Could not restart processing', getErrorMessage(error));
    }
  }

  return (
    <Card className="p-6">
      <div className="flex items-start gap-4">
        <span className="flex size-12 shrink-0 items-center justify-center rounded-2xl bg-rose-50 text-rose-600">
          <TriangleAlert className="size-6" aria-hidden />
        </span>
        <div>
          <p className="font-semibold text-slate-900">We couldn’t read this document</p>
          <p className="mt-1 text-sm text-slate-500">
            {document.errorMessage ?? 'The file may be blurry, password-protected or not a bill.'}
          </p>
        </div>
      </div>
      <div className="mt-6 flex flex-wrap gap-2">
        <Button onClick={() => void retry()} loading={reprocess.isPending} leftIcon={<RotateCw className="size-4" aria-hidden />}>
          Try again
        </Button>
        <Button variant="secondary" onClick={onManualEntry} leftIcon={<PencilLine className="size-4" aria-hidden />}>
          Enter details manually
        </Button>
      </div>
    </Card>
  );
}
