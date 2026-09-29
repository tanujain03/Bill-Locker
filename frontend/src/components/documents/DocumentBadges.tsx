import { CircleCheck, CircleX, File, FileImage, FileText, LoaderCircle, Mail, ScanEye, type LucideIcon } from 'lucide-react';
import { Badge, type BadgeTone } from '@/components/ui/Badge';
import { cn } from '@/lib/cn';
import type { DocumentSource, DocumentType, ProcessingStatus } from '@/types';
import { isImageMime, isPdfMime } from '@/utils/file';
import { DOCUMENT_TYPE_LABELS, PROCESSING_STATUS_LABELS } from '@/utils/labels';

const STATUS_STYLE: Record<ProcessingStatus, { tone: BadgeTone; icon: LucideIcon; spin?: boolean }> = {
  UPLOADED: { tone: 'info', icon: LoaderCircle, spin: true },
  PROCESSING: { tone: 'info', icon: LoaderCircle, spin: true },
  PROCESSED: { tone: 'warning', icon: ScanEye },
  REVIEW_REQUIRED: { tone: 'warning', icon: ScanEye },
  CONFIRMED: { tone: 'success', icon: CircleCheck },
  FAILED: { tone: 'danger', icon: CircleX },
};

export function DocumentStatusBadge({ status, size = 'md' }: { status: ProcessingStatus; size?: 'sm' | 'md' }) {
  const { tone, icon: Icon, spin } = STATUS_STYLE[status];
  return (
    <Badge tone={tone} size={size} icon={<Icon className={cn('size-3.5', spin && 'animate-spin')} aria-hidden />}>
      {PROCESSING_STATUS_LABELS[status]}
    </Badge>
  );
}

export function DocumentTypeBadge({ type, size = 'md' }: { type: DocumentType; size?: 'sm' | 'md' }) {
  return (
    <Badge tone="neutral" size={size}>
      {DOCUMENT_TYPE_LABELS[type]}
    </Badge>
  );
}

export function SourceBadge({ source }: { source: DocumentSource }) {
  if (source !== 'GMAIL') return null;
  return (
    <Badge tone="danger" size="sm" icon={<Mail className="size-3" aria-hidden />} title="Imported from Gmail">
      Gmail
    </Badge>
  );
}

export function FileTypeIcon({ mimeType, className }: { mimeType: string; className?: string }) {
  const pdf = isPdfMime(mimeType);
  const image = isImageMime(mimeType);
  const Icon = pdf ? FileText : image ? FileImage : File;
  return (
    <span
      className={cn(
        'flex size-10 shrink-0 items-center justify-center rounded-xl',
        pdf ? 'bg-rose-50 text-rose-600' : image ? 'bg-sky-50 text-sky-600' : 'bg-slate-100 text-slate-600',
        className,
      )}
    >
      <Icon className="size-5" aria-hidden />
    </span>
  );
}
