import { Store } from 'lucide-react';
import { Link } from 'react-router';
import { shopLink, type ShopSpend } from '../../lib/dashboard';
import { formatAmount } from '../../lib/documents';
import { Panel, PanelEmpty } from './Panel';

/** Where the money went: shops by amount, each with a bar relative to the biggest. */
export function TopShops({ shops }: { shops: ShopSpend[] }) {
  const max = Math.max(0, ...shops.map((s) => s.amount));
  return (
    <Panel icon={Store} title="Top shops">
      {shops.length === 0 ? (
        <PanelEmpty>No shops yet. They appear once a saved bill has a seller and a total.</PanelEmpty>
      ) : (
        <ul className="-mx-2 space-y-1">
          {shops.map((shop) => (
            <li key={shop.name}>
              <Link to={shopLink(shop.name)} className="block rounded-lg px-2 py-2 hover:bg-slate-50">
                <div className="flex items-baseline justify-between gap-3 text-sm">
                  <span className="truncate font-medium">{shop.name}</span>
                  <span className="shrink-0 font-semibold tabular-nums">{formatAmount(shop.amount)}</span>
                </div>
                <div className="mt-1.5 flex items-center gap-2">
                  <span className="h-2 flex-1 overflow-hidden rounded-full bg-slate-100">
                    <span
                      className="block h-full rounded-full bg-brand-500"
                      style={{ width: `${max ? (shop.amount / max) * 100 : 0}%` }}
                    />
                  </span>
                  <span className="w-14 shrink-0 text-right text-xs text-slate-600">
                    {shop.bills === 1 ? '1 bill' : `${shop.bills} bills`}
                  </span>
                </div>
              </Link>
            </li>
          ))}
        </ul>
      )}
    </Panel>
  );
}
