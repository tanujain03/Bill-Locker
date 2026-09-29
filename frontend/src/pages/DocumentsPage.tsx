import { FileText, Mail, SearchX, Upload } from 'lucide-react';
import { useMemo, useState } from 'react';
import { DocumentList } from '@/components/documents/DocumentList';
import { UploadDropzone } from '@/components/documents/UploadDropzone';
import { useUpload } from '@/components/documents/upload-context';
import { Button, ButtonLink } from '@/components/ui/Button';
import { Card } from '@/components/ui/Card';
import { Select } from '@/components/ui/Field';
import { EmptyState, ErrorState, Skeleton } from '@/components/ui/feedback';
import { FilterTabs } from '@/components/ui/misc';
import { PageHeader } from '@/components/ui/PageHeader';
import { useDocuments } from '@/hooks/useDocuments';
import { useDocumentTitle } from '@/hooks/useDocumentTitle';
import { DOCUMENT_TYPES, type DocumentSummary, type DocumentType } from '@/types';
import { DOCUMENT_TYPE_LABELS, isProcessing, needsReview } from '@/utils/labels';

type StatusTab = 'all' | 'review' | 'processing' | 'saved' | 'failed';

const TAB_FILTERS: Record<StatusTab, (document: DocumentSummary) => boolean> = {
  all: () => true,
  review: (document) => needsReview(document.processingStatus),
  processing: (document) => isProcessing(document.processingStatus),
  saved: (document) => document.processingStatus === 'CONFIRMED',
  failed: (document) => document.processingStatus === 'FAILED',
};

export function DocumentsPage() {
  useDocumentTitle('Documents');
  const { openUpload } = useUpload();
  const documents = useDocuments();
  const [tab, setTab] = useState<StatusTab>('all');
  const [type, setType] = useState<DocumentType | ''>('');

  const counts = useMemo(() => {
    const list = documents.data ?? [];
    return Object.fromEntries(
      (Object.keys(TAB_FILTERS) as StatusTab[]).map((key) => [key, list.filter(TAB_FILTERS[key]).length]),
    ) as Record<StatusTab, number>;
  }, [documents.data]);

  const visible = (documents.data ?? []).filter(
    (document) => TAB_FILTERS[tab](document) && (!type || document.documentType === type),
  );

  return (
    <>
      <PageHeader
        title="Documents"
        description="Every bill, invoice, warranty card and receipt — safely stored and searchable."
        actions={
          <>
            <ButtonLink to="/gmail" variant="secondary" leftIcon={<Mail className="size-4" aria-hidden />}>
              Import from Gmail
            </ButtonLink>
            <Button onClick={() => openUpload()} leftIcon={<Upload className="size-4" aria-hidden />}>
              Upload
            </Button>
          </>
        }
      />

      <div className="mb-6">
        <UploadDropzone compact onFiles={(files) => openUpload({ files })} />
      </div>

      <div className="mb-4 flex flex-col gap-3 md:flex-row md:items-center md:justify-between">
        <FilterTabs
          label="Filter documents by status"
          value={tab}
          onChange={setTab}
          options={[
            { value: 'all', label: 'All', count: counts.all },
            { value: 'review', label: 'Needs review', count: counts.review },
            { value: 'processing', label: 'Processing', count: counts.processing },
            { value: 'saved', label: 'Saved', count: counts.saved },
            { value: 'failed', label: 'Failed', count: counts.failed },
          ]}
        />
        <div className="md:w-52">
          <Select aria-label="Filter by document type" value={type} onChange={(event) => setType(event.target.value as DocumentType | '')}>
            <option value="">All types</option>
            {DOCUMENT_TYPES.map((value) => (
              <option key={value} value={value}>
                {DOCUMENT_TYPE_LABELS[value]}
              </option>
            ))}
          </Select>
        </div>
      </div>

      <Card>
        {documents.isPending ? (
          <div className="space-y-4 p-5 sm:p-6" aria-busy="true" aria-label="Loading documents">
            {Array.from({ length: 5 }, (_, index) => (
              <Skeleton key={index} className="h-12 w-full" />
            ))}
          </div>
        ) : documents.isError ? (
          <ErrorState title="Could not load your documents" error={documents.error} onRetry={() => void documents.refetch()} />
        ) : documents.data.length === 0 ? (
          <EmptyState
            icon={<FileText aria-hidden />}
            title="No documents yet"
            description="Upload your first bill to get started, or import invoices from Gmail."
            action={
              <>
                <Button onClick={() => openUpload()} leftIcon={<Upload className="size-4" aria-hidden />}>
                  Upload bill
                </Button>
                <ButtonLink to="/gmail" variant="secondary">
                  Connect Gmail
                </ButtonLink>
              </>
            }
          />
        ) : visible.length === 0 ? (
          <EmptyState
            icon={<SearchX aria-hidden />}
            title="Nothing here"
            description="No documents match these filters."
            action={
              <Button
                variant="secondary"
                onClick={() => {
                  setTab('all');
                  setType('');
                }}
              >
                Show all documents
              </Button>
            }
          />
        ) : (
          <DocumentList documents={visible} />
        )}
      </Card>
    </>
  );
}
