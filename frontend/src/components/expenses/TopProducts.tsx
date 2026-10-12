import { Package } from 'lucide-react';
import { Link } from 'react-router';
import { BILL_CATEGORY_LABELS, formatAmount } from '../../lib/documents';
import { categoryIcon, type ProductSpend } from '../../lib/expenses';
import { Panel, PanelEmpty } from '../dashboard/Panel';

/** The month's most expensive products, each opening its bill. */
export function TopProducts({ products }: { products: ProductSpend[] }) {
  return (
    <Panel icon={Package} title="Biggest purchases">
      {products.length === 0 ? (
        <PanelEmpty>No products with a price this month. Bills and receipts show up under categories.</PanelEmpty>
      ) : (
        <ul className="-mx-2 divide-y divide-slate-100">
          {products.map((p, i) => {
            const Icon = categoryIcon(p.category);
            return (
              <li key={`${p.documentId}-${i}`}>
                <Link to={`/documents/${p.documentId}`} className="flex items-center gap-3 rounded-lg px-2 py-2.5 hover:bg-slate-50">
                  <span className="grid size-9 shrink-0 place-items-center rounded-lg bg-slate-100 text-slate-600">
                    <Icon className="size-4" aria-hidden />
                  </span>
                  <span className="min-w-0 flex-1">
                    <span className="block truncate text-sm font-medium">{p.name}</span>
                    <span className="block truncate text-xs text-slate-500">
                      {[p.category ? BILL_CATEGORY_LABELS[p.category] : 'No category', p.seller].filter(Boolean).join(' · ')}
                    </span>
                  </span>
                  <span className="shrink-0 text-sm font-semibold tabular-nums">{formatAmount(p.amount)}</span>
                </Link>
              </li>
            );
          })}
        </ul>
      )}
    </Panel>
  );
}
