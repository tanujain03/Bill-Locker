import { CircleCheck, Eye, FileUp, LoaderCircle } from 'lucide-react';
import type { DocumentStatus } from '../../lib/documents';

const STATUS = {
  UPLOADED: { Icon: FileUp, label: 'Not read yet', colours: 'bg-slate-100 text-slate-700' },
  EXTRACTED: { Icon: Eye, label: 'Needs review', colours: 'bg-amber-50 text-amber-800' },
  SAVED: { Icon: CircleCheck, label: 'Saved', colours: 'bg-emerald-50 text-emerald-800' },
};

/**
 * Where a document is in its life. The colour always comes with an icon and a label.
 * `reading`: it is queued for the background AI read, which beats the stored status.
 */
export function StatusBadge({ status, reading }: { status: DocumentStatus; reading?: boolean }) {
  const { Icon, label, colours } = reading
    ? { Icon: LoaderCircle, label: 'Reading…', colours: 'bg-slate-100 text-slate-700' }
    : STATUS[status];
  return (
    <span className={`inline-flex items-center gap-1 rounded-full px-2 py-0.5 text-xs font-medium ${colours}`}>
      <Icon className={`size-3.5 ${reading ? 'animate-spin' : ''}`} aria-hidden />
      {label}
    </span>
  );
}
