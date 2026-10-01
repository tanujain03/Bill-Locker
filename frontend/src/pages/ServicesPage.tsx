import { CalendarClock, Plus, Wrench } from 'lucide-react';
import { useState } from 'react';
import { Link } from 'react-router';
import { ServiceHistory } from '@/components/services/ServiceHistory';
import { ServiceRecordDialog } from '@/components/services/ServiceRecordDialog';
import { Badge } from '@/components/ui/Badge';
import { Button } from '@/components/ui/Button';
import { Card, CardHeader } from '@/components/ui/Card';
import { ConfirmDialog } from '@/components/ui/ConfirmDialog';
import { Select } from '@/components/ui/Field';
import { EmptyState, ErrorState, Skeleton } from '@/components/ui/feedback';
import { PageHeader } from '@/components/ui/PageHeader';
import { useToast } from '@/components/ui/toast-context';
import { useDocumentTitle } from '@/hooks/useDocumentTitle';
import { useProducts } from '@/hooks/useProducts';
import { useDeleteServiceRecord, useServiceRecords } from '@/hooks/useServiceRecords';
import { getErrorMessage } from '@/lib/api-client';
import type { ServiceRecord } from '@/types';
import { describeDueDate, formatDate } from '@/utils/format';
import { SERVICE_TYPE_LABELS } from '@/utils/labels';
import { dueTone, upcomingServices } from '@/utils/service';

export function ServicesPage() {
  useDocumentTitle('Services');
  const toast = useToast();
  const records = useServiceRecords();
  const { data: products = [] } = useProducts();
  const deleteRecord = useDeleteServiceRecord();
  const [productFilter, setProductFilter] = useState('');
  const [dialog, setDialog] = useState<{ open: boolean; record: ServiceRecord | null }>({ open: false, record: null });
  const [toDelete, setToDelete] = useState<ServiceRecord | null>(null);

  const all = records.data ?? [];
  const upcoming = upcomingServices(all);
  const history = all
    .filter((record) => !productFilter || record.productId === productFilter)
    .sort((a, b) => b.serviceDate.localeCompare(a.serviceDate));

  async function confirmDelete() {
    if (!toDelete) return;
    try {
      await deleteRecord.mutateAsync(toDelete.id);
      toast.success('Service record deleted');
      setToDelete(null);
    } catch (error) {
      toast.error('Could not delete the service record', getErrorMessage(error));
    }
  }

  return (
    <>
      <PageHeader
        title="Services"
        description="Repairs, maintenance and what’s due next — with reminders before each service."
        actions={
          <Button
            onClick={() => setDialog({ open: true, record: null })}
            disabled={products.length === 0}
            leftIcon={<Plus className="size-4" aria-hidden />}
          >
            Add service record
          </Button>
        }
      />

      {records.isPending ? (
        <div className="space-y-6" aria-busy="true" aria-label="Loading services">
          <Skeleton className="h-40 rounded-2xl" />
          <Skeleton className="h-72 rounded-2xl" />
        </div>
      ) : records.isError ? (
        <Card>
          <ErrorState title="Could not load service records" error={records.error} onRetry={() => void records.refetch()} />
        </Card>
      ) : all.length === 0 ? (
        <Card>
          <EmptyState
            icon={<Wrench aria-hidden />}
            title="No service records yet"
            description={
              products.length === 0
                ? 'Add a product first, then log its repairs and maintenance here.'
                : 'Log an AC service, a repair or an installation — and set the next service date to get reminded.'
            }
            action={
              products.length > 0 && (
                <Button onClick={() => setDialog({ open: true, record: null })} leftIcon={<Plus className="size-4" aria-hidden />}>
                  Add service record
                </Button>
              )
            }
          />
        </Card>
      ) : (
        <div className="space-y-6">
          <Card>
            <CardHeader
              icon={<CalendarClock className="size-4 text-slate-400" aria-hidden />}
              title="Upcoming services"
              description="The next scheduled service for each product"
            />
            {upcoming.length === 0 ? (
              <EmptyState compact icon={<CalendarClock aria-hidden />} title="Nothing scheduled" description="Set a next service date on a record to see it here." />
            ) : (
              <ul className="mt-2 divide-y divide-slate-100">
                {upcoming.map((record) => (
                  <li key={record.id} className="flex flex-wrap items-center gap-3 px-5 py-3.5 sm:px-6">
                    <div className="min-w-0 flex-1">
                      <Link to={`/products/${record.productId}`} className="text-sm font-semibold text-slate-900 hover:text-brand-700">
                        {record.productName}
                      </Link>
                      <p className="text-xs text-slate-500">
                        Last: {SERVICE_TYPE_LABELS[record.serviceType]} on {formatDate(record.serviceDate)}
                        {record.serviceCenter && ` · ${record.serviceCenter}`}
                      </p>
                    </div>
                    <div className="text-right">
                      <Badge tone={dueTone(record.nextServiceDate)}>{describeDueDate(record.nextServiceDate)}</Badge>
                      <p className="mt-1 text-xs text-slate-500">{formatDate(record.nextServiceDate)}</p>
                    </div>
                  </li>
                ))}
              </ul>
            )}
          </Card>

          <Card>
            <CardHeader
              icon={<Wrench className="size-4 text-slate-400" aria-hidden />}
              title="Service history"
              description="Every repair and maintenance visit"
              action={
                <div className="w-44 sm:w-56">
                  <Select aria-label="Filter by product" value={productFilter} onChange={(event) => setProductFilter(event.target.value)}>
                    <option value="">All products</option>
                    {products.map((product) => (
                      <option key={product.id} value={product.id}>
                        {product.name}
                      </option>
                    ))}
                  </Select>
                </div>
              }
            />
            <div className="mt-2">
              {history.length === 0 ? (
                <EmptyState compact icon={<Wrench aria-hidden />} title="No records for this product" />
              ) : (
                <ServiceHistory records={history} onEdit={(record) => setDialog({ open: true, record })} onDelete={setToDelete} />
              )}
            </div>
          </Card>
        </div>
      )}

      <ServiceRecordDialog open={dialog.open} record={dialog.record} onClose={() => setDialog({ open: false, record: null })} />
      <ConfirmDialog
        open={toDelete !== null}
        onClose={() => setToDelete(null)}
        onConfirm={() => void confirmDelete()}
        loading={deleteRecord.isPending}
        title="Delete this service record?"
        confirmLabel="Delete record"
        message="This removes the record and its next-service reminder."
      />
    </>
  );
}
