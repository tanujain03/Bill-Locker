import { ArrowRight, Download, EyeOff, FileText, Paperclip, Sparkles } from 'lucide-react';
import { Link } from 'react-router';
import { Badge } from '@/components/ui/Badge';
import { Button } from '@/components/ui/Button';
import { cn } from '@/lib/cn';
import type { GmailMessage } from '@/types';
import { formatFileSize, formatRelativeTime } from '@/utils/format';
import { DOCUMENT_TYPE_LABELS } from '@/utils/labels';

interface GmailMessageRowProps {
  message: GmailMessage;
  onImport: (message: GmailMessage) => void;
  onIgnore: (message: GmailMessage) => void;
  busy?: boolean;
}

export function GmailMessageRow({ message, onImport, onIgnore, busy = false }: GmailMessageRowProps) {
  const likelyBill = message.confidence >= 0.6;
  const percent = Math.round(message.confidence * 100);

  return (
    <li className="flex flex-col gap-3 px-5 py-4 sm:flex-row sm:items-start sm:gap-4 sm:px-6">
      <span
        className="flex size-10 shrink-0 items-center justify-center rounded-full bg-slate-100 text-sm font-semibold text-slate-600"
        aria-hidden
      >
        {message.fromName.slice(0, 1).toUpperCase()}
      </span>

      <div className="min-w-0 flex-1">
        <div className="flex flex-wrap items-center gap-x-2 gap-y-1">
          <p className="text-sm font-semibold text-slate-900">{message.fromName}</p>
          <p className="truncate text-xs text-slate-500">{message.fromEmail}</p>
          <span className="text-xs text-slate-400">· {formatRelativeTime(message.receivedAt)}</span>
        </div>
        <p className="mt-0.5 text-sm font-medium text-slate-800">{message.subject}</p>
        <p className="mt-0.5 line-clamp-1 text-sm text-slate-500">{message.snippet}</p>

        <div className="mt-2 flex flex-wrap items-center gap-2">
          <Badge
            tone={likelyBill ? 'brand' : 'neutral'}
            size="sm"
            icon={<Sparkles className="size-3" aria-hidden />}
            title="How likely this email contains a purchase document"
          >
            {likelyBill && message.detectedType
              ? `${DOCUMENT_TYPE_LABELS[message.detectedType]} · ${percent}% match`
              : `Unlikely to be a bill · ${percent}%`}
          </Badge>
          {message.attachments.length > 0 ? (
            message.attachments.map((attachment) => (
              <span
                key={attachment.fileName}
                className="inline-flex max-w-full items-center gap-1 rounded-md bg-slate-100 px-2 py-0.5 text-xs text-slate-600"
              >
                <Paperclip className="size-3 shrink-0" aria-hidden />
                <span className="truncate">{attachment.fileName}</span>
                <span className="text-slate-400">· {formatFileSize(attachment.size)}</span>
              </span>
            ))
          ) : (
            <span className="inline-flex items-center gap-1 rounded-md bg-slate-100 px-2 py-0.5 text-xs text-slate-600">
              <FileText className="size-3" aria-hidden />
              Bill is in the email body
            </span>
          )}
        </div>
      </div>

      <div className={cn('flex shrink-0 items-center gap-2 pl-14 sm:pl-0', busy && 'opacity-70')}>
        {message.status === 'NEW' && (
          <>
            <Button
              size="sm"
              variant={likelyBill ? 'primary' : 'secondary'}
              onClick={() => onImport(message)}
              disabled={busy}
              leftIcon={<Download className="size-3.5" aria-hidden />}
            >
              Import
            </Button>
            <Button
              size="sm"
              variant="ghost"
              onClick={() => onIgnore(message)}
              disabled={busy}
              leftIcon={<EyeOff className="size-3.5" aria-hidden />}
            >
              Ignore
            </Button>
          </>
        )}
        {message.status === 'IMPORTED' &&
          (message.documentIds.length > 0 ? (
            <Link
              to={`/documents/${message.documentIds[0]}`}
              className="inline-flex items-center gap-1 text-sm font-medium text-brand-700 hover:text-brand-800"
            >
              View document
              <ArrowRight className="size-3.5" aria-hidden />
            </Link>
          ) : (
            <Badge tone="success">Imported</Badge>
          ))}
        {message.status === 'IGNORED' && (
          <Button size="sm" variant="secondary" onClick={() => onImport(message)} disabled={busy}>
            Import anyway
          </Button>
        )}
      </div>
    </li>
  );
}
