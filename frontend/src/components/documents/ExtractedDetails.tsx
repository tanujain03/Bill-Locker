import { FileSearch, RotateCw, ScanText } from 'lucide-react';
import { Button } from '@/components/ui/Button';
import { Card, CardBody, CardHeader } from '@/components/ui/Card';
import { useToast } from '@/components/ui/toast-context';
import { useReprocessDocument } from '@/hooks/useDocuments';
import { getErrorMessage } from '@/lib/api-client';
import type { DocumentDetail, ExtractionField, ExtractionResult } from '@/types';
import { formatCurrency, formatDate } from '@/utils/format';
import { DOCUMENT_TYPE_LABELS } from '@/utils/labels';
import { formatWarrantyPeriod } from '@/utils/warranty';
import { ConfidenceBadge } from './ConfidenceBadge';
import { confidenceLevel, REVIEW_FIELDS, summarizeExtraction } from './confidence';

/** The details shown, in review order (the category is only chosen when a product is saved). */
const DETAIL_FIELDS: { key: ExtractionField; label: string }[] = [
  { key: 'documentType', label: 'Document type' },
  ...REVIEW_FIELDS.flatMap((field) => (field.extractionKey ? [{ key: field.extractionKey, label: field.label }] : [])),
];

function displayValue(extraction: ExtractionResult, key: ExtractionField): string | null {
  const value = extraction[key];
  if (value === null || value === undefined || value === '') return null;
  switch (key) {
    case 'documentType':
      return extraction.documentType ? DOCUMENT_TYPE_LABELS[extraction.documentType] : null;
    case 'purchaseDate':
      return formatDate(extraction.purchaseDate);
    case 'purchasePrice':
      return formatCurrency(extraction.purchasePrice, extraction.currency ?? 'INR');
    case 'warrantyMonths':
      return formatWarrantyPeriod(extraction.warrantyMonths);
    default:
      return String(value);
  }
}

interface ExtractedDetailsProps {
  document: DocumentDetail;
}

/**
 * Read-only view of what was read from a document, used until products can be
 * saved: each detail with how sure the reader is, plus the full text it read.
 */
export function ExtractedDetails({ document }: ExtractedDetailsProps) {
  const extraction = document.extraction;
  const stats = summarizeExtraction(extraction);
  const reprocess = useReprocessDocument(document.id);
  const toast = useToast();

  // Reads the file again with the current rules (the page then shows the reading progress).
  async function readAgain() {
    try {
      await reprocess.mutateAsync();
    } catch (error) {
      toast.error('Could not read the document again', getErrorMessage(error));
    }
  }

  return (
    <Card>
      <CardHeader
        icon={<FileSearch className="size-4 text-brand-600" aria-hidden />}
        title="Details found in this document"
        description={`${stats.found} of ${stats.total} details found. Check them against the document — saving them as a product comes in a later step.`}
        action={
          <Button
            variant="secondary"
            size="sm"
            onClick={() => void readAgain()}
            loading={reprocess.isPending}
            leftIcon={<RotateCw className="size-4" aria-hidden />}
          >
            Read again
          </Button>
        }
      />
      <CardBody>
        {extraction ? (
          <dl className="divide-y divide-slate-100">
            {DETAIL_FIELDS.map(({ key, label }) => {
              const value = displayValue(extraction, key);
              const score = extraction.confidence?.[key];
              return (
                <div key={key} className="flex flex-wrap items-center justify-between gap-x-4 gap-y-1 py-3">
                  <dt className="text-sm text-slate-500">{label}</dt>
                  <dd className="flex min-w-0 flex-wrap items-center justify-end gap-2 text-sm font-medium text-slate-900">
                    {value && <span className="break-words">{value}</span>}
                    <ConfidenceBadge source={confidenceLevel(value, score)} score={score} />
                  </dd>
                </div>
              );
            })}
          </dl>
        ) : (
          <p className="text-sm text-slate-500">No details were found in this document.</p>
        )}

        {document.extractedText && (
          <details className="mt-5 rounded-xl border border-slate-200">
            <summary className="flex cursor-pointer items-center gap-2 px-4 py-3 text-sm font-medium text-slate-700">
              <ScanText className="size-4 text-slate-400" aria-hidden />
              Text read from the document
            </summary>
            <pre className="max-h-80 overflow-auto border-t border-slate-200 px-4 py-3 font-mono text-xs leading-relaxed whitespace-pre-wrap text-slate-700">
              {document.extractedText}
            </pre>
          </details>
        )}
      </CardBody>
    </Card>
  );
}
