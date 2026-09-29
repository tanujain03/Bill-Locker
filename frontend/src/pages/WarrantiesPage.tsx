import { ShieldAlert, ShieldCheck, ShieldX, Shield, Upload } from 'lucide-react';
import { useMemo } from 'react';
import { Link, useSearchParams } from 'react-router';
import { useUpload } from '@/components/documents/upload-context';
import { ProductIcon } from '@/components/products/ProductIcon';
import { Button } from '@/components/ui/Button';
import { Card } from '@/components/ui/Card';
import { EmptyState, ErrorState, Skeleton } from '@/components/ui/feedback';
import { FilterTabs } from '@/components/ui/misc';
import { PageHeader } from '@/components/ui/PageHeader';
import { StatCard } from '@/components/ui/StatCard';
import { WarrantyMeter, WarrantyStatusBadge } from '@/components/warranties/WarrantyStatus';
import { useDocumentTitle } from '@/hooks/useDocumentTitle';
import { useWarranties } from '@/hooks/useWarranties';
import { WARRANTY_STATUSES, type Warranty, type WarrantyStatus } from '@/types';
import { formatDate } from '@/utils/format';
import { WARRANTY_STATUS_LABELS } from '@/utils/warranty';

type Tab = 'ALL' | WarrantyStatus;

function isTab(value: string | null): value is Tab {
  return value === 'ALL' || (WARRANTY_STATUSES as string[]).includes(value ?? '');
}

/** Soonest expiry first; unknown warranties last. */
function byExpiry(a: Warranty, b: Warranty): number {
  return (a.expiryDate ?? '9999-12-31').localeCompare(b.expiryDate ?? '9999-12-31');
}

export function WarrantiesPage() {
  useDocumentTitle('Warranties');
  const [params, setParams] = useSearchParams();
  const statusParam = params.get('status');
  const tab: Tab = isTab(statusParam) ? statusParam : 'ALL';
  const warranties = useWarranties();
  const { openUpload } = useUpload();

  const counts = useMemo(() => {
    const list = warranties.data ?? [];
    const count = (status: WarrantyStatus) => list.filter((warranty) => warranty.status === status).length;
    return { ALL: list.length, ACTIVE: count('ACTIVE'), EXPIRING_SOON: count('EXPIRING_SOON'), EXPIRED: count('EXPIRED'), UNKNOWN: count('UNKNOWN') };
  }, [warranties.data]);

  const setTab = (next: Tab) => setParams(next === 'ALL' ? {} : { status: next }, { replace: true });
  const visible = (warranties.data ?? []).filter((warranty) => tab === 'ALL' || warranty.status === tab).sort(byExpiry);

  return (
    <>
      <PageHeader title="Warranties" description="Every warranty in one place — sorted by what expires first." />

      <section aria-label="Warranty summary" className="mb-6 grid grid-cols-2 gap-3 sm:gap-4 lg:grid-cols-4">
        <StatCard label="Total warranties" value={counts.ALL} icon={<Shield aria-hidden />} tone="brand" loading={warranties.isPending} />
        <StatCard label="Active" value={counts.ACTIVE} icon={<ShieldCheck aria-hidden />} tone="success" loading={warranties.isPending} />
        <StatCard
          label="Expiring soon"
          value={counts.EXPIRING_SOON}
          icon={<ShieldAlert aria-hidden />}
          tone="warning"
          hint="Within 30 days"
          loading={warranties.isPending}
        />
        <StatCard label="Expired" value={counts.EXPIRED} icon={<ShieldX aria-hidden />} tone="danger" loading={warranties.isPending} />
      </section>

      <FilterTabs
        label="Filter warranties by status"
        value={tab}
        onChange={setTab}
        className="mb-4"
        options={[
          { value: 'ALL', label: 'All', count: counts.ALL },
          ...WARRANTY_STATUSES.map((status) => ({ value: status, label: WARRANTY_STATUS_LABELS[status], count: counts[status] })),
        ]}
      />

      <Card>
        {warranties.isPending ? (
          <div className="space-y-4 p-5 sm:p-6" aria-busy="true" aria-label="Loading warranties">
            {Array.from({ length: 4 }, (_, index) => (
              <Skeleton key={index} className="h-16 w-full" />
            ))}
          </div>
        ) : warranties.isError ? (
          <ErrorState title="Could not load warranties" error={warranties.error} onRetry={() => void warranties.refetch()} />
        ) : warranties.data.length === 0 ? (
          <EmptyState
            icon={<ShieldCheck aria-hidden />}
            title="No warranties tracked yet"
            description="Upload a bill and Bill Locker calculates the warranty expiry for you."
            action={
              <Button onClick={() => openUpload()} leftIcon={<Upload className="size-4" aria-hidden />}>
                Upload bill
              </Button>
            }
          />
        ) : visible.length === 0 ? (
          <EmptyState compact icon={<ShieldCheck aria-hidden />} title={`No ${WARRANTY_STATUS_LABELS[tab as WarrantyStatus]?.toLowerCase() ?? ''} warranties`} description="Nothing in this category right now." />
        ) : (
          <ul className="divide-y divide-slate-100">
            {visible.map((warranty) => (
              <li key={warranty.id}>
                <Link
                  to={`/products/${warranty.productId}`}
                  className="grid gap-3 px-5 py-4 transition-colors hover:bg-slate-50 sm:grid-cols-[minmax(0,1.3fr)_minmax(0,1fr)_auto] sm:items-center sm:gap-6 sm:px-6"
                >
                  <div className="flex min-w-0 items-center gap-3">
                    <ProductIcon name={warranty.productName} categorySlug={warranty.categorySlug} />
                    <div className="min-w-0">
                      <p className="truncate text-sm font-semibold text-slate-900">{warranty.productName}</p>
                      <p className="truncate text-xs text-slate-500">
                        {[warranty.productBrand, warranty.categoryName].filter(Boolean).join(' · ') || '—'}
                        {warranty.startDate && ` · bought ${formatDate(warranty.startDate)}`}
                      </p>
                    </div>
                  </div>
                  <WarrantyMeter warranty={warranty} />
                  <div className="sm:justify-self-end">
                    <WarrantyStatusBadge status={warranty.status} />
                  </div>
                </Link>
              </li>
            ))}
          </ul>
        )}
      </Card>
    </>
  );
}
