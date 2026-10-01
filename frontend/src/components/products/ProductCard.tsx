import { Link } from 'react-router';
import { WarrantyMeter, WarrantyStatusBadge } from '@/components/warranties/WarrantyStatus';
import type { Product } from '@/types';
import { formatCurrency, formatDate } from '@/utils/format';
import { productSubtitle } from '@/utils/product';
import { warrantyStatusOf } from '@/utils/warranty';
import { ProductIcon } from './ProductIcon';

export function ProductCard({ product }: { product: Product }) {
  return (
    <article className="group relative flex flex-col rounded-2xl border border-slate-200/80 bg-white p-5 shadow-card transition focus-within:ring-2 focus-within:ring-brand-500 hover:-translate-y-0.5 hover:border-slate-300 hover:shadow-md">
      <div className="flex items-start gap-3">
        <ProductIcon name={product.name} categorySlug={product.categorySlug} />
        <div className="min-w-0 flex-1">
          <h3 className="truncate text-[0.95rem] font-semibold text-slate-900">
            <Link to={`/products/${product.id}`} className="after:absolute after:inset-0 focus:outline-none">
              {product.name}
            </Link>
          </h3>
          <p className="truncate text-sm text-slate-500">{productSubtitle(product)}</p>
        </div>
        <WarrantyStatusBadge status={warrantyStatusOf(product.warranty)} size="sm" />
      </div>

      <dl className="mt-4 grid grid-cols-2 gap-3 text-sm">
        <div>
          <dt className="text-xs text-slate-500">Purchased</dt>
          <dd className="mt-0.5 font-medium text-slate-800">{formatDate(product.purchaseDate)}</dd>
        </div>
        <div>
          <dt className="text-xs text-slate-500">Price</dt>
          <dd className="mt-0.5 font-medium text-slate-800">{formatCurrency(product.purchasePrice, product.currency)}</dd>
        </div>
      </dl>

      <div className="mt-4 border-t border-slate-100 pt-4">
        <WarrantyMeter warranty={product.warranty} />
      </div>
    </article>
  );
}
