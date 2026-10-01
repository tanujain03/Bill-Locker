import { useQueryClient } from '@tanstack/react-query';
import { ArrowRight, CircleCheck, Mail, Package, RotateCw, X } from 'lucide-react';
import { useEffect, useRef, useState } from 'react';
import { Link, useNavigate } from 'react-router';
import { Button } from '@/components/ui/Button';
import { Field, Select } from '@/components/ui/Field';
import { Modal } from '@/components/ui/Modal';
import { useDocument } from '@/hooks/useDocuments';
import { getErrorMessage } from '@/lib/api-client';
import { isFeatureEnabled } from '@/lib/features';
import { invalidateLockerData } from '@/lib/invalidate';
import { documentService } from '@/services/document.service';
import { DOCUMENT_TYPES, type DocumentSummary, type DocumentType } from '@/types';
import { validateUploadFile } from '@/utils/file';
import { formatFileSize } from '@/utils/format';
import { DOCUMENT_TYPE_LABELS, isStoredOnly, needsReview } from '@/utils/labels';
import { FileTypeIcon } from './DocumentBadges';
import { stepForDocument } from './processing';
import { ProcessingSteps } from './ProcessingSteps';
import { UploadDropzone } from './UploadDropzone';
import type { OpenUploadOptions } from './upload-context';

interface UploadEntry {
  key: string;
  file: File;
  phase: 'invalid' | 'uploading' | 'uploaded' | 'error';
  progress: number;
  error?: string;
  document?: DocumentSummary;
}

let entrySequence = 0;

interface UploadDialogProps {
  open: boolean;
  onClose: () => void;
  options: OpenUploadOptions;
}

export function UploadDialog({ open, onClose, options }: UploadDialogProps) {
  const [entries, setEntries] = useState<UploadEntry[]>([]);
  const [documentType, setDocumentType] = useState<DocumentType | ''>(options.documentType ?? '');
  const queryClient = useQueryClient();
  const navigate = useNavigate();

  function patch(key: string, changes: Partial<UploadEntry>) {
    setEntries((list) => list.map((entry) => (entry.key === key ? { ...entry, ...changes } : entry)));
  }

  async function upload(entry: UploadEntry) {
    patch(entry.key, { phase: 'uploading', progress: 0, error: undefined });
    try {
      const document = await documentService.upload({
        file: entry.file,
        documentType: documentType || undefined,
        productId: options.productId,
        onProgress: (progress) => patch(entry.key, { progress }),
      });
      patch(entry.key, { phase: 'uploaded', progress: 100, document });
      void invalidateLockerData(queryClient);
    } catch (error) {
      patch(entry.key, { phase: 'error', error: getErrorMessage(error, 'Upload failed.') });
    }
  }

  function addFiles(files: File[]) {
    const added = files.map<UploadEntry>((file) => {
      const validation = validateUploadFile(file);
      entrySequence += 1;
      return {
        key: `upload-${entrySequence}`,
        file,
        phase: validation.valid ? 'uploading' : 'invalid',
        progress: 0,
        error: validation.error,
      };
    });
    setEntries((list) => [...list, ...added]);
    added.filter((entry) => entry.phase === 'uploading').forEach((entry) => void upload(entry));
  }

  // Files handed over by the opener start uploading immediately (once — the ref
  // survives React's development double-invocation of effects).
  const initialFilesHandled = useRef(false);
  useEffect(() => {
    if (initialFilesHandled.current || !options.files?.length) return;
    initialFilesHandled.current = true;
    addFiles(options.files);
    // eslint-disable-next-line react-hooks/exhaustive-deps -- run once per dialog session
  }, []);

  function remove(key: string) {
    setEntries((list) => list.filter((entry) => entry.key !== key));
  }

  function openReview(documentId: string) {
    onClose();
    navigate(`/documents/${documentId}`);
  }

  const hasUploads = entries.length > 0;
  const aiReading = isFeatureEnabled('documentProcessing');

  return (
    <Modal
      open={open}
      onClose={onClose}
      size="lg"
      title="Upload bills & documents"
      description={
        aiReading
          ? 'AI reads each document and pre-fills the details. You review everything before it is saved.'
          : 'Your files are stored safely in your locker.'
      }
      footer={
        <>
          {hasUploads && aiReading && (
            <p className="mr-auto self-center text-xs text-slate-500">
              You can close this window — processing continues and you’ll be notified.
            </p>
          )}
          <Button variant={hasUploads ? 'primary' : 'secondary'} onClick={onClose}>
            {hasUploads ? 'Done' : 'Cancel'}
          </Button>
        </>
      }
    >
      <div className="space-y-5">
        {options.productName && (
          <div className="flex items-center gap-2 rounded-xl bg-brand-50 px-3 py-2 text-sm text-brand-800">
            <Package className="size-4 shrink-0" aria-hidden />
            <span>
              Documents will be attached to <strong className="font-semibold">{options.productName}</strong>.
            </span>
          </div>
        )}

        <Field
          label="What are you uploading?"
          hint={aiReading ? 'Leave on auto-detect and the AI will classify it for you.' : 'Optional — helps you find it later.'}
        >
          <Select value={documentType} onChange={(event) => setDocumentType(event.target.value as DocumentType | '')}>
            <option value="">{aiReading ? 'Auto-detect (recommended)' : 'Not specified'}</option>
            {DOCUMENT_TYPES.map((type) => (
              <option key={type} value={type}>
                {DOCUMENT_TYPE_LABELS[type]}
              </option>
            ))}
          </Select>
        </Field>

        <UploadDropzone onFiles={addFiles} compact={hasUploads} />

        {hasUploads && (
          <ul className="space-y-3" aria-label="Uploads">
            {entries.map((entry) => (
              <UploadRow
                key={entry.key}
                entry={entry}
                onRetry={() => void upload(entry)}
                onRemove={() => remove(entry.key)}
                onReview={openReview}
              />
            ))}
          </ul>
        )}

        {!options.productId && isFeatureEnabled('gmail') && (
          <p className="flex items-center justify-center gap-1.5 text-sm text-slate-500">
            <Mail className="size-4" aria-hidden />
            Bills arrive by email?
            <Link to="/gmail" onClick={onClose} className="font-medium text-brand-700 hover:text-brand-800">
              Import them from Gmail
            </Link>
          </p>
        )}
      </div>
    </Modal>
  );
}

interface UploadRowProps {
  entry: UploadEntry;
  onRetry: () => void;
  onRemove: () => void;
  onReview: (documentId: string) => void;
}

function UploadRow({ entry, onRetry, onRemove, onReview }: UploadRowProps) {
  // Poll the document once it exists so the steps follow the backend pipeline.
  const { data: document } = useDocument(entry.document?.id);
  const current = document ?? entry.document;
  const failed = entry.phase === 'error' || entry.phase === 'invalid' || current?.processingStatus === 'FAILED';
  const ready = current && (needsReview(current.processingStatus) || current.processingStatus === 'CONFIRMED');
  // Without OCR/AI in the backend yet, a finished upload is the last step.
  const stored = entry.phase === 'uploaded' && isStoredOnly(current?.processingStatus);
  const step = entry.phase === 'uploading' || entry.phase === 'invalid' || entry.phase === 'error' ? 'upload' : stepForDocument(current);

  return (
    <li className="rounded-xl border border-slate-200 bg-white p-4">
      <div className="flex items-start gap-3">
        <FileTypeIcon mimeType={entry.file.type} />
        <div className="min-w-0 flex-1">
          <div className="flex items-start justify-between gap-2">
            <div className="min-w-0">
              <p className="truncate text-sm font-medium text-slate-900">{entry.file.name}</p>
              <p className="text-xs text-slate-500">{formatFileSize(entry.file.size)}</p>
            </div>
            {(failed || ready || stored) && (
              <button
                type="button"
                onClick={onRemove}
                aria-label={`Remove ${entry.file.name} from the list`}
                className="-m-1 rounded-md p-1 text-slate-400 hover:bg-slate-100 hover:text-slate-600"
              >
                <X className="size-4" aria-hidden />
              </button>
            )}
          </div>

          <div className="mt-3">
            {entry.phase === 'invalid' ? (
              <p className="text-xs font-medium text-rose-600" role="alert">
                {entry.error}
              </p>
            ) : stored ? (
              <p className="flex items-center gap-1.5 text-xs font-medium text-emerald-700" aria-live="polite">
                <CircleCheck className="size-3.5" aria-hidden />
                Uploaded — saved in your locker
              </p>
            ) : (
              <ProcessingSteps
                step={step}
                failed={failed}
                uploadProgress={entry.phase === 'uploading' ? entry.progress : undefined}
                caption={
                  entry.phase === 'error'
                    ? entry.error
                    : current?.processingStatus === 'FAILED'
                      ? (current.errorMessage ?? 'We could not read this document.')
                      : undefined
                }
              />
            )}
          </div>

          {(entry.phase === 'error' || ready || stored || current?.processingStatus === 'FAILED') && (
            <div className="mt-3 flex flex-wrap gap-2">
              {entry.phase === 'error' && (
                <Button size="sm" variant="secondary" onClick={onRetry} leftIcon={<RotateCw className="size-3.5" aria-hidden />}>
                  Retry upload
                </Button>
              )}
              {current && (ready || stored || current.processingStatus === 'FAILED') && (
                <Button
                  size="sm"
                  variant={ready ? 'primary' : 'secondary'}
                  onClick={() => onReview(current.id)}
                  rightIcon={<ArrowRight className="size-3.5" aria-hidden />}
                >
                  {ready ? 'Review extracted details' : stored ? 'View document' : 'View details'}
                </Button>
              )}
            </div>
          )}
        </div>
      </div>
    </li>
  );
}
