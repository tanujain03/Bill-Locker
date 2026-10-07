import { ChevronDown, ScanSearch } from 'lucide-react';
import { useEffect, useRef, useState } from 'react';
import { SCAN_RANGE_LABELS, type ScanRange } from '../../lib/gmail';

/** The "Scan" button: opens a small list of how far back to look. */
export function ScanMenu({ disabled, onScan }: { disabled: boolean; onScan: (range: ScanRange) => void }) {
  const [open, setOpen] = useState(false);
  const box = useRef<HTMLDivElement>(null);

  // Close on Escape or a click anywhere outside the menu.
  useEffect(() => {
    if (!open) return;
    const onKey = (e: KeyboardEvent) => e.key === 'Escape' && setOpen(false);
    const onClick = (e: MouseEvent) => !box.current?.contains(e.target as Node) && setOpen(false);
    document.addEventListener('keydown', onKey);
    document.addEventListener('mousedown', onClick);
    return () => {
      document.removeEventListener('keydown', onKey);
      document.removeEventListener('mousedown', onClick);
    };
  }, [open]);

  return (
    <div ref={box} className="relative">
      <button
        type="button"
        disabled={disabled}
        aria-haspopup="true"
        aria-expanded={open}
        onClick={() => setOpen((o) => !o)}
        className="inline-flex items-center gap-1.5 rounded-lg bg-brand-600 px-3 py-2 text-sm font-semibold text-white hover:bg-brand-700 disabled:opacity-60"
      >
        <ScanSearch className="size-4" aria-hidden />
        Scan
        <ChevronDown className="size-4" aria-hidden />
      </button>
      {open && (
        <ul className="absolute left-0 z-20 mt-1 w-44 rounded-lg border border-slate-200 bg-white p-1 shadow-lg sm:right-0 sm:left-auto">
          {(Object.entries(SCAN_RANGE_LABELS) as [ScanRange, string][]).map(([range, label]) => (
            <li key={range}>
              <button
                type="button"
                onClick={() => {
                  setOpen(false);
                  onScan(range);
                }}
                className="block w-full rounded-md px-3 py-2 text-left text-sm text-slate-700 hover:bg-slate-100"
              >
                {label}
              </button>
            </li>
          ))}
        </ul>
      )}
    </div>
  );
}
