import { ArrowRight, type LucideIcon } from 'lucide-react';
import type { ReactNode } from 'react';
import { Link } from 'react-router';

/** The white card every dashboard section sits in: icon + title, optional "View all", content. */
export function Panel(props: {
  icon: LucideIcon;
  title: string;
  /** e.g. "Last 12 months" next to the title. */
  hint?: string;
  viewAll?: string;
  id?: string;
  children: ReactNode;
}) {
  const Icon = props.icon;
  return (
    <section id={props.id} className="flex flex-col rounded-2xl border border-slate-200 bg-white p-5 shadow-xs">
      <div className="flex items-center justify-between gap-3">
        <h2 className="flex items-center gap-2 font-semibold">
          <Icon className="size-4.5 text-brand-600" aria-hidden />
          {props.title}
          {props.hint && <span className="text-sm font-normal text-slate-500">· {props.hint}</span>}
        </h2>
        {props.viewAll && (
          <Link
            to={props.viewAll}
            className="inline-flex shrink-0 items-center gap-1 text-sm font-medium text-brand-700 hover:text-brand-800"
          >
            View all
            <ArrowRight className="size-4" aria-hidden />
          </Link>
        )}
      </div>
      <div className="mt-4 flex-1">{props.children}</div>
    </section>
  );
}

/** A calm "nothing here yet" line inside a panel. */
export function PanelEmpty({ children }: { children: ReactNode }) {
  return (
    <p className="flex h-full min-h-24 items-center justify-center rounded-xl bg-slate-50 px-4 text-center text-sm text-slate-500">
      {children}
    </p>
  );
}
