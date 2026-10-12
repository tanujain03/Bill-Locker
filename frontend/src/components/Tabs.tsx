import type { ReactNode } from 'react';

/** A row of tabs (Warranties, Gmail): the chosen one is underlined; counts sit in a pill. */
export function TabList({ label, children }: { label: string; children: ReactNode }) {
  return (
    <div role="tablist" aria-label={label} className="-mx-1 flex gap-1 overflow-x-auto border-b border-slate-200 px-1">
      {children}
    </div>
  );
}

export function Tab(props: { label: string; count?: number; active: boolean; onClick: () => void }) {
  return (
    <button
      type="button"
      role="tab"
      aria-selected={props.active}
      onClick={props.onClick}
      className={`-mb-px inline-flex shrink-0 items-center gap-1.5 border-b-2 px-3 py-2.5 text-sm font-medium whitespace-nowrap ${
        props.active ? 'border-brand-600 text-brand-700' : 'border-transparent text-slate-600 hover:text-slate-900'
      }`}
    >
      {props.label}
      {props.count !== undefined && (
        <span
          className={`rounded-full px-1.5 text-xs tabular-nums ${
            props.active ? 'bg-brand-100 text-brand-700' : 'bg-slate-100 text-slate-600'
          }`}
        >
          {props.count}
        </span>
      )}
    </button>
  );
}
