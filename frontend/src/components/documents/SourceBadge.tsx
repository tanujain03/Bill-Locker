import { File, FileImage, FileText, Mail, Upload } from 'lucide-react';

/**
 * Where a document came from: imported from a Gmail account, or uploaded by hand.
 * Like the status, the colour always comes with an icon and a label.
 * `showAccount`: also print the Gmail address (on the document page); in lists it
 * is in the tooltip, to keep rows short.
 */
export function SourceBadge({ sourceGmail, showAccount = false }: { sourceGmail: string | null; showAccount?: boolean }) {
  if (sourceGmail) {
    return (
      <span
        title={`Imported from Gmail (${sourceGmail})`}
        className={`inline-flex max-w-full items-center gap-1 rounded-full ${showAccount ? '' : 'shrink-0 '}bg-red-50 px-2 py-0.5 text-xs font-medium text-red-700 ring-1 ring-red-100`}
      >
        <Mail className="size-3.5 shrink-0" aria-hidden />
        <span className="truncate">{showAccount ? `Gmail · ${sourceGmail}` : 'Gmail'}</span>
      </span>
    );
  }
  return (
    <span
      title="Uploaded from this device"
      className="inline-flex shrink-0 items-center gap-1 rounded-full bg-slate-100 px-2 py-0.5 text-xs font-medium text-slate-600 ring-1 ring-slate-200"
    >
      <Upload className="size-3.5" aria-hidden />
      Uploaded
    </span>
  );
}

/** A coloured tile that tells PDFs and photos apart at a glance. */
export function FileTypeIcon({ contentType, size = 'md' }: { contentType: string; size?: 'sm' | 'md' }) {
  const pdf = contentType === 'application/pdf';
  const image = contentType.startsWith('image/');
  const Icon = pdf ? FileText : image ? FileImage : File;
  const colours = pdf ? 'bg-rose-50 text-rose-600' : image ? 'bg-sky-50 text-sky-600' : 'bg-slate-100 text-slate-500';
  const box = size === 'sm' ? 'size-8 rounded-lg' : 'size-10 rounded-xl';
  return (
    <span className={`grid shrink-0 place-items-center ${box} ${colours}`} title={pdf ? 'PDF' : image ? 'Photo' : 'File'}>
      <Icon className={size === 'sm' ? 'size-4' : 'size-5'} aria-hidden />
    </span>
  );
}
