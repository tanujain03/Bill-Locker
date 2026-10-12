import { ChevronRight, ClockAlert } from 'lucide-react';
import { Link } from 'react-router';
import { formatDate } from '../../lib/document-form';
import { warrantiesLink, type WarrantyView } from '../../lib/warranties';
import { WarrantyStatusPill } from '../warranties/WarrantyStatusPill';
import { Panel, PanelEmpty } from './Panel';

/** The warranties that end soonest (within 30 days), each opening its bill. */
export function ExpiringList({ items }: { items: WarrantyView[] }) {
  return (
    <Panel icon={ClockAlert} title="Expiring soon" hint="next 30 days" viewAll={warrantiesLink('EXPIRING_SOON')}>
      {items.length === 0 ? (
        <PanelEmpty>Nothing expires in the next 30 days.</PanelEmpty>
      ) : (
        <ul className="-mx-2 divide-y divide-slate-100">
          {items.map((w, i) => (
            <li key={`${w.documentId}-${i}`}>
              <Link to={`/documents/${w.documentId}`} className="flex items-center gap-3 rounded-lg px-2 py-2.5 hover:bg-slate-50">
                <div className="min-w-0 flex-1">
                  <p className="truncate text-sm font-medium">{w.productName ?? 'Unnamed product'}</p>
                  <p className="truncate text-xs text-slate-600">
                    {[w.sellerName, w.endDate && `Ends ${formatDate(w.endDate)}`].filter(Boolean).join(' · ')}
                  </p>
                </div>
                <WarrantyStatusPill status={w.status} daysLeft={w.daysLeft} />
                <ChevronRight className="size-4 shrink-0 text-slate-400" aria-hidden />
              </Link>
            </li>
          ))}
        </ul>
      )}
    </Panel>
  );
}
