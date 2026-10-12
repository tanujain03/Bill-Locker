import { ArrowRight, ShieldCheck } from 'lucide-react';
import { Link } from 'react-router';
import type { Dashboard } from '../../lib/dashboard';
import { WARRANTY_STATUS_LABELS, WARRANTY_STATUSES, warrantiesLink, type WarrantyStatus } from '../../lib/warranties';
import { WARRANTY_STYLE } from '../warranties/WarrantyStatusPill';
import { Panel, PanelEmpty } from './Panel';

/**
 * One bar split by warranty status (each part as wide as its share), plus a legend.
 * Every part of the bar and every legend item opens that tab of the warranties page.
 */
export function WarrantyHealth({ counts }: { counts: Dashboard['warranties'] }) {
  const value: Record<WarrantyStatus, number> = {
    ACTIVE: counts.active,
    EXPIRING_SOON: counts.expiringSoon,
    EXPIRED: counts.expired,
    NO_INFO: counts.noInfo,
  };
  const total = WARRANTY_STATUSES.reduce((sum, s) => sum + value[s], 0);

  return (
    <Panel icon={ShieldCheck} title="Warranty health" viewAll={warrantiesLink()}>
      {total === 0 ? (
        <PanelEmpty>No warranty details yet. Save a bill with its products to see them here.</PanelEmpty>
      ) : (
        <>
          <p className="text-sm text-slate-600">
            <span className="text-2xl font-semibold text-slate-900 tabular-nums">{total}</span> products on your saved bills
          </p>
          <div className="mt-4 flex h-4 gap-0.5 overflow-hidden rounded-full bg-slate-100">
            {WARRANTY_STATUSES.filter((s) => value[s] > 0).map((s) => (
              <Link
                key={s}
                to={warrantiesLink(s)}
                aria-label={`${WARRANTY_STATUS_LABELS[s]}: ${value[s]}`}
                title={`${WARRANTY_STATUS_LABELS[s]}: ${value[s]}`}
                // min-w keeps a group of 1 out of 100 visible and clickable.
                className={`min-w-3 transition hover:opacity-80 ${WARRANTY_STYLE[s].bar}`}
                style={{ flexGrow: value[s] }}
              />
            ))}
          </div>
          <ul className="mt-4 grid grid-cols-2 gap-2">
            {WARRANTY_STATUSES.map((s) => {
              const { Icon } = WARRANTY_STYLE[s];
              return (
                <li key={s}>
                  <Link
                    to={warrantiesLink(s)}
                    className="flex items-center gap-2 rounded-lg px-2 py-1.5 text-sm hover:bg-slate-50"
                  >
                    <span className={`size-2.5 shrink-0 rounded-full ${WARRANTY_STYLE[s].dot}`} aria-hidden />
                    <Icon className="size-4 shrink-0 text-slate-400" aria-hidden />
                    <span className="truncate text-slate-600">{WARRANTY_STATUS_LABELS[s]}</span>
                    <span className="ml-auto font-semibold tabular-nums">{value[s]}</span>
                  </Link>
                </li>
              );
            })}
          </ul>
          {/* Products without dates can't warn you; say how to fix that. */}
          {value.NO_INFO > 0 && (
            <Link
              to={warrantiesLink('NO_INFO')}
              className="mt-3 flex items-center gap-2 rounded-lg bg-slate-50 px-3 py-2.5 text-sm text-slate-700 hover:bg-slate-100"
            >
              <span className="min-w-0 flex-1">
                {value.NO_INFO === 1 ? '1 product has' : `${value.NO_INFO} products have`} no warranty date.{' '}
                <span className="font-semibold text-brand-700">Add dates</span> so we can remind you.
              </span>
              <ArrowRight className="size-4 shrink-0 text-brand-700" aria-hidden />
            </Link>
          )}
        </>
      )}
    </Panel>
  );
}
