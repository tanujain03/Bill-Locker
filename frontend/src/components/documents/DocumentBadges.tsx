import { CircleCheck, CircleX, File, FileImage, FileText, LoaderCircle, Mail, ScanEye, type LucideIcon } from 'lucide-react';
import { Badge, type BadgeTone } from '@/components/ui/Badge';
import { cn } from '@/lib/cn';
import type { DocumentSource, DocumentType, ProcessingStatus } from '@/types';
import { isImageMime, isPdfMime } from '@/utils/file';
import { DOCUMENT_TYPE_LABELS, isStoredOnly, PROCESSING_STATUS_LABELS } from '@/utils/labels';

interface StatusStyle {
  tone: BadgeTone;
  icon: LucideIcon;
  spin?: boolean;
}

const STATUS_STYLE: Record<ProcessingStatus, StatusStyle> = {
  UPLOADED: { tone: 'info', icon: LoaderCircle, spin: true },
  PROCESSING: { tone: 'info', icon: LoaderCircle, spin: true },
  PROCESSED: { tone: 'warning', icon: ScanEye },
  REVIEW_REQUIRED: { tone: 'warning', icon: ScanEye },
  CONFIRMED: { tone: 'success', icon: CircleCheck },
  FAILED: { tone: 'danger', icon: CircleX },
};

/** While the backend only stores files, "Uploaded" is final: no spinner. */
const STORED_STYLE: StatusStyle = { tone: 'neutral', icon: CircleCheck };

export function DocumentStatusBadge({ status, size = 'md' }: { status: ProcessingStatus; size?: 'sm' | 'md' }) {
  const { tone, icon: Icon, spin } = isStoredOnly(status) ? STORED_STYLE : STATUS_STYLE[status];
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
