import { Clock } from 'lucide-react';
import { Link } from 'react-router';
import { formatDate } from '../../lib/document-form';
import { DOCUMENT_TYPE_LABELS, type DocumentSummary } from '../../lib/documents';
import { FileTypeIcon, SourceBadge } from '../documents/SourceBadge';
import { StatusBadge } from '../documents/StatusBadge';
import { Panel, PanelEmpty } from './Panel';

/** The newest bills of any status, so whatever just arrived is one click away. */
export function RecentBills({ bills }: { bills: DocumentSummary[] }) {
  return (
    <Panel icon={Clock} title="Recent bills" viewAll="/documents">
      {bills.length === 0 ? (
        <PanelEmpty>No bills yet.</PanelEmpty>
      ) : (
        <ul className="-mx-2 divide-y divide-slate-100">
          {bills.map((d) => (
            <li key={d.id}>
              <Link to={`/documents/${d.id}`} className="flex items-center gap-3 rounded-lg px-2 py-2.5 hover:bg-slate-50">
                <FileTypeIcon contentType={d.contentType} size="sm" />
                <div className="min-w-0 flex-1">
                  <div className="flex min-w-0 items-center gap-2">
                    <p className="truncate text-sm font-medium">{d.sellerName ?? d.fileName}</p>
                    <SourceBadge sourceGmail={d.sourceGmail} />
                  </div>
                  <p className="truncate text-xs text-slate-600">
                    {[d.documentType && DOCUMENT_TYPE_LABELS[d.documentType], d.purchaseDate && formatDate(d.purchaseDate)]
                      .filter(Boolean)
                      .join(' · ') || d.fileName}
                  </p>
                </div>
                <StatusBadge status={d.status} reading={d.readQueued} />
              </Link>
            </li>
          ))}
        </ul>
      )}
    </Panel>
  );
}
