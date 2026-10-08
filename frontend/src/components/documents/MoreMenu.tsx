import { Download, Ellipsis, LoaderCircle, Trash2 } from 'lucide-react';
import { useEffect, useRef, useState } from 'react';
import { buttonClass } from '../Button';

/**
 * "⋯" on the document page: the less common actions, Download and Delete. Keeping Delete
 * in here (and red, and confirmed) means it's never one slip away from Copy or Read.
 */
export function MoreMenu({ disabled, deleting, onDownload, onDelete }: {
  disabled: boolean;
  deleting: boolean;
  onDownload: () => void;
  onDelete: () => void;
}) {
  const [open, setOpen] = useState(false);
  const box = useRef<HTMLDivElement>(null);

  useEffect(() => {
    if (!open) return;
    const onClick = (e: MouseEvent) => !box.current?.contains(e.target as Node) && setOpen(false);
    const onKey = (e: KeyboardEvent) => e.key === 'Escape' && setOpen(false);
    document.addEventListener('mousedown', onClick);
    document.addEventListener('keydown', onKey);
    return () => {
      document.removeEventListener('mousedown', onClick);
      document.removeEventListener('keydown', onKey);
    };
  }, [open]);

  const item = 'flex w-full items-center gap-3 rounded-lg px-3 py-2 text-sm font-medium';
  const choose = (action: () => void) => () => {
    setOpen(false);
    action();
  };

  return (
    <div ref={box} className="relative">
      <button
        type="button"
        disabled={disabled}
        onClick={() => setOpen((o) => !o)}
        aria-haspopup="menu"
        aria-expanded={open}
        aria-label="More actions"
        title="More actions"
        className={`${buttonClass()} px-3`}
      >
        {deleting ? <LoaderCircle className="size-4 animate-spin" aria-hidden /> : <Ellipsis className="size-4" aria-hidden />}
      </button>
      {open && (
        <div role="menu" className="absolute top-full right-0 z-30 mt-2 w-48 rounded-xl border border-slate-200 bg-white p-1.5 shadow-lg">
          <button type="button" role="menuitem" onClick={choose(onDownload)} className={`${item} text-slate-700 hover:bg-slate-100`}>
            <Download className="size-4" aria-hidden />
            Download file
          </button>
          <button type="button" role="menuitem" onClick={choose(onDelete)} className={`${item} text-rose-700 hover:bg-rose-50`}>
            <Trash2 className="size-4" aria-hidden />
            Delete…
          </button>
        </div>
      )}
    </div>
  );
}
