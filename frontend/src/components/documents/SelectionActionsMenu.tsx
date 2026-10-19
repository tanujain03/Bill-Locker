import { Ellipsis, RefreshCw, Share2, Trash2, X } from 'lucide-react';
import { useEffect, useId, useRef, useState, type KeyboardEvent } from 'react';
import { Button } from '../Button';

export function SelectionActionsMenu({ busy, onShare, onRead, onDelete, onClear }: {
  busy: boolean;
  onShare: () => void;
  onRead: () => void;
  onDelete: () => void;
  onClear: () => void;
}) {
  const [open, setOpen] = useState(false);
  const box = useRef<HTMLDivElement>(null);
  const menu = useRef<HTMLDivElement>(null);
  const id = useId();
  const focusTrigger = () => box.current?.querySelector<HTMLButtonElement>('[aria-haspopup="menu"]')?.focus();

  useEffect(() => {
    if (!open) return;
    menu.current?.querySelector<HTMLButtonElement>('[role="menuitem"]')?.focus();
    const outside = (event: PointerEvent) => {
      if (!box.current?.contains(event.target as Node)) setOpen(false);
    };
    const escape = (event: globalThis.KeyboardEvent) => {
      if (event.key === 'Escape') {
        setOpen(false);
        focusTrigger();
      }
    };
    document.addEventListener('pointerdown', outside);
    document.addEventListener('keydown', escape);
    return () => {
      document.removeEventListener('pointerdown', outside);
      document.removeEventListener('keydown', escape);
    };
  }, [open]);

  function navigate(event: KeyboardEvent<HTMLDivElement>) {
    const items = [...(menu.current?.querySelectorAll<HTMLButtonElement>('[role="menuitem"]') ?? [])];
    const index = items.indexOf(document.activeElement as HTMLButtonElement);
    const next = event.key === 'ArrowDown' ? (index + 1) % items.length
      : event.key === 'ArrowUp' ? (index - 1 + items.length) % items.length
      : event.key === 'Home' ? 0 : event.key === 'End' ? items.length - 1 : null;
    if (next !== null) {
      event.preventDefault();
      items[next]?.focus();
    }
    if (event.key === 'Tab') setOpen(false);
  }

  function choose(action: () => void) {
    setOpen(false);
    focusTrigger();
    action();
  }

  const item = 'flex w-full items-center gap-3 rounded-lg px-3 py-2 text-left text-sm font-medium outline-none hover:bg-slate-50 focus-visible:bg-slate-100';
  return (
    <div ref={box} className="relative">
      <Button size="sm" variant="ghost" icon={Ellipsis} busy={busy} disabled={busy} aria-label="Selected document actions" title="Actions" aria-haspopup="menu" aria-expanded={open} aria-controls={open ? id : undefined}
        onClick={() => setOpen((value) => !value)} onKeyDown={(event) => { if (event.key === 'ArrowDown') { event.preventDefault(); setOpen(true); } }} />
      {open && (
        <div ref={menu} id={id} role="menu" aria-label="Selected document actions" onKeyDown={navigate} onBlur={(event) => { if (!box.current?.contains(event.relatedTarget as Node | null)) setOpen(false); }} className="absolute top-full right-0 z-30 mt-2 w-48 rounded-xl border border-slate-200 bg-white p-1.5 text-slate-700 shadow-lg">
          <button type="button" role="menuitem" onClick={() => choose(onShare)} className={item}><Share2 className="size-4" aria-hidden />Share</button>
          <button type="button" role="menuitem" onClick={() => choose(onRead)} className={item}><RefreshCw className="size-4" aria-hidden />Read again</button>
          <button type="button" role="menuitem" onClick={() => choose(onDelete)} className={`${item} text-rose-700 hover:bg-rose-50 focus-visible:bg-rose-50`}><Trash2 className="size-4" aria-hidden />Delete</button>
          <div role="separator" className="my-1 border-t border-slate-100" />
          <button type="button" role="menuitem" onClick={() => choose(onClear)} className={item}><X className="size-4" aria-hidden />Clear selection</button>
        </div>
      )}
    </div>
  );
}
