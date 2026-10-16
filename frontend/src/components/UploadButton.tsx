import { Upload } from 'lucide-react';
import { useRef, type ReactNode } from 'react';
import { UPLOAD_ACCEPT, useUploadBill } from '../lib/useUploadBill';
import { Button, type ButtonVariant } from './Button';

/**
 * "Upload bill" in one click: the button opens the file picker itself (it used to go
 * to the Documents page first). Select one or several files; the shared queue handles progress and reading.
 */
export function UploadButton({ variant = 'primary', className, children = 'Upload bills', iconOnlyOnPhones }: {
  variant?: ButtonVariant;
  className?: string;
  children?: ReactNode;
  /** Top bar: just the icon on narrow screens. */
  iconOnlyOnPhones?: boolean;
}) {
  const input = useRef<HTMLInputElement>(null);
  const { upload, uploading } = useUploadBill();

  return (
    <>
      <Button
        variant={variant}
        icon={Upload}
        busy={uploading}
        onClick={() => input.current?.click()}
        aria-label={iconOnlyOnPhones ? 'Upload bills' : undefined}
        className={`${iconOnlyOnPhones ? 'max-sm:px-3' : ''} ${className ?? ''}`}
      >
        <span className={iconOnlyOnPhones ? 'hidden sm:inline' : undefined}>{uploading ? 'Uploading…' : children}</span>
      </Button>
      <input
        ref={input}
        type="file"
        multiple
        accept={UPLOAD_ACCEPT}
        className="hidden"
        onChange={(e) => {
          upload(Array.from(e.target.files ?? []));
          e.target.value = ''; // so choosing the same file again still fires onChange
        }}
      />
    </>
  );
}
