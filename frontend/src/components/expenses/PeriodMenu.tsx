import { CalendarRange, Check, ChevronDown, ChevronLeft, ChevronRight } from 'lucide-react';
import { useEffect, useRef, useState, type FormEvent, type KeyboardEvent } from 'react';
import {
  PERIOD_LABELS,
  PRESETS,
  presetRange,
  rangeLabel,
  shiftRange,
  today,
  type Period,
  type Preset,
  type Range,
} from '../../lib/expenses';
import { Button } from '../Button';

/**
 * The period picker: one dropdown button ("This month · Oct 2026"), with ‹ › beside it to
 * step to the period before or after. The menu lists the ready-made periods (each with its
 * dates) and a "Custom range" with two date fields.
 */
export function PeriodMenu({ period, range, onChoose }: { period: Period; range: Range; onChoose: (p: Preset | Range) => void }) {
  const [open, setOpen] = useState(false);
  const [customOpen, setCustomOpen] = useState(false);
  const [from, setFrom] = useState(range.from);
  const [to, setTo] = useState(range.to);
  const box = useRef<HTMLDivElement>(null);
  const button = useRef<HTMLButtonElement>(null);
  const menu = useRef<HTMLDivElement>(null);
  const next = shiftRange(range, 1);
  const canGoForward = range.to < today() && next.from <= today();

  // The custom dates follow the shown period (a month bar, Back, ‹ ›).
  useEffect(() => {
    setFrom(range.from);
    setTo(range.to);
  }, [range.from, range.to]);

  // While open: a click outside or Escape closes it; focus starts on the chosen item.
  useEffect(() => {
    if (!open) return;
    const items = menu.current;
    (items?.querySelector<HTMLElement>('[aria-checked="true"]') ?? items?.querySelector<HTMLElement>('[role="menuitemradio"]'))?.focus();
    const onDown = (e: MouseEvent) => {
      if (!box.current?.contains(e.target as Node)) setOpen(false);
    };
    document.addEventListener('mousedown', onDown);
    return () => document.removeEventListener('mousedown', onDown);
  }, [open]);

  function close() {
    setOpen(false);
    setCustomOpen(false);
    button.current?.focus();
  }

  function choose(p: Preset | Range) {
    onChoose(p);
    close();
  }

  /** Arrow keys move between the items, Escape closes (like a native menu). */
  function onMenuKey(e: KeyboardEvent<HTMLDivElement>) {
    if (e.key === 'Escape') {
      e.preventDefault();
      close();
      return;
    }
    if (e.key !== 'ArrowDown' && e.key !== 'ArrowUp') return;
    const items = [...(menu.current?.querySelectorAll<HTMLElement>('[role="menuitemradio"]') ?? [])];
    const at = items.indexOf(document.activeElement as HTMLElement);
    if (!items.length) return;
    e.preventDefault();
    const nextIndex = e.key === 'ArrowDown' ? (at + 1) % items.length : (at - 1 + items.length) % items.length;
    items[nextIndex].focus();
  }

  function applyCustom(e: FormEvent) {
    e.preventDefault();
    if (from && to) choose({ from, to }); // the backend checks the order and the length
  }

  const step = 'grid size-10 place-items-center rounded-xl border border-slate-200 bg-white text-slate-600 shadow-xs hover:bg-slate-50 disabled:opacity-40 disabled:hover:bg-white';

  return (
    // The menu hangs from the left of this whole group, so on a phone it stays on screen.
    <div ref={box} className="relative flex items-center gap-2">
      <button type="button" onClick={() => onChoose(shiftRange(range, -1))} aria-label="Previous period" title="Previous period" className={step}>
        <ChevronLeft className="size-5" aria-hidden />
      </button>

      <div>
        <button
          ref={button}
          type="button"
          aria-haspopup="menu"
          aria-expanded={open}
          onClick={() => (open ? close() : setOpen(true))}
          className="flex h-10 items-center gap-2 rounded-xl border border-slate-200 bg-white pr-3 pl-3.5 text-sm shadow-xs hover:bg-slate-50"
        >
          <CalendarRange className="size-4 text-brand-600" aria-hidden />
          {/* A custom period is named by its dates (also on phones, where the dates are hidden). */}
          <span className="font-semibold">{period === 'custom' ? rangeLabel(range) : PERIOD_LABELS[period]}</span>
          {period !== 'custom' && <span className="hidden text-slate-500 sm:inline">· {rangeLabel(range)}</span>}
          <ChevronDown className={`size-4 text-slate-500 transition ${open ? 'rotate-180' : ''}`} aria-hidden />
        </button>

        {open && (
          <div
            ref={menu}
            role="menu"
            aria-label="Period"
            onKeyDown={onMenuKey}
            className="absolute top-full left-0 z-30 mt-2 w-80 max-w-[calc(100vw-2rem)] overflow-hidden rounded-2xl border border-slate-200 bg-white p-1.5 shadow-xl"
          >
            {PRESETS.map((p) => {
              const active = period === p;
              return (
                <button
                  key={p}
                  type="button"
                  role="menuitemradio"
                  aria-checked={active}
                  onClick={() => choose(p)}
                  className={`flex w-full items-center gap-2 rounded-xl px-3 py-2.5 text-left text-sm outline-none hover:bg-slate-50 focus-visible:bg-slate-100 ${
                    active ? 'font-semibold text-brand-700' : 'text-slate-700'
                  }`}
                >
                  <Check className={`size-4 shrink-0 ${active ? 'text-brand-600' : 'invisible'}`} aria-hidden />
                  <span className="flex-1">{PERIOD_LABELS[p]}</span>
                  <span className="text-xs font-normal text-slate-500 tabular-nums">{rangeLabel(presetRange(p))}</span>
                </button>
              );
            })}

            <div className="my-1.5 border-t border-slate-100" />
            <button
              type="button"
              role="menuitemradio"
              aria-checked={period === 'custom'}
              aria-expanded={customOpen || period === 'custom'}
              onClick={() => setCustomOpen((v) => !v)}
              className={`flex w-full items-center gap-2 rounded-xl px-3 py-2.5 text-left text-sm outline-none hover:bg-slate-50 focus-visible:bg-slate-100 ${
                period === 'custom' ? 'font-semibold text-brand-700' : 'text-slate-700'
              }`}
            >
              <Check className={`size-4 shrink-0 ${period === 'custom' ? 'text-brand-600' : 'invisible'}`} aria-hidden />
              <span className="flex-1">{PERIOD_LABELS.custom}</span>
              <ChevronDown className={`size-4 text-slate-400 transition ${customOpen || period === 'custom' ? 'rotate-180' : ''}`} aria-hidden />
            </button>

            {(customOpen || period === 'custom') && (
              <form onSubmit={applyCustom} noValidate className="space-y-3 px-3 pt-1 pb-3">
                <div className="grid grid-cols-2 gap-2">
                  <DateField label="From" value={from} max={to || today()} onChange={setFrom} />
                  <DateField label="To" value={to} min={from} max={today()} onChange={setTo} />
                </div>
                <Button type="submit" variant="primary" size="sm" full>
                  Show this period
                </Button>
                <p className="text-xs text-slate-500">Both days are included. Up to 3 years.</p>
              </form>
            )}
          </div>
        )}
      </div>

      <button
        type="button"
        onClick={() => onChoose(next)}
        disabled={!canGoForward}
        aria-label="Next period"
        title="Next period"
        className={step}
      >
        <ChevronRight className="size-5" aria-hidden />
      </button>
    </div>
  );
}

function DateField(props: { label: string; value: string; min?: string; max?: string; onChange: (v: string) => void }) {
  return (
    <label className="flex flex-col gap-1 text-xs font-medium text-slate-600">
      {props.label}
      <input
        type="date"
        value={props.value}
        min={props.min}
        max={props.max}
        onChange={(e) => props.onChange(e.target.value)}
        className="rounded-lg border border-slate-300 bg-white px-2 py-1.5 text-sm text-slate-900 outline-none focus:border-brand-500 focus:ring-2 focus:ring-brand-100"
      />
    </label>
  );
}
