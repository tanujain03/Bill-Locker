import {
  ArrowRight,
  CalendarClock,
  FileText,
  Mail,
  Package,
  PackagePlus,
  ShieldAlert,
  ShieldCheck,
  ShieldX,
  Upload,
  Wallet,
  Wrench,
} from 'lucide-react';
import { useState } from 'react';
import { Link } from 'react-router';
import { SpendingByCategoryChart, SpendingTable } from '@/components/charts/SpendingByCategoryChart';
import { WarrantyStatusBar } from '@/components/charts/WarrantyStatusBar';
import { DocumentList } from '@/components/documents/DocumentList';
import { useUpload } from '@/components/documents/upload-context';
import { ProductIcon } from '@/components/products/ProductIcon';
import { ProductFormDialog } from '@/components/products/ProductFormDialog';
import { Alert } from '@/components/ui/Alert';
import { Badge } from '@/components/ui/Badge';
import { Button, ButtonLink } from '@/components/ui/Button';
import { Card, CardBody, CardHeader } from '@/components/ui/Card';
import { EmptyState, ErrorState, Skeleton } from '@/components/ui/feedback';
import { FilterTabs } from '@/components/ui/misc';
import { PageHeader } from '@/components/ui/PageHeader';
import { StatCard } from '@/components/ui/StatCard';
import { useDocumentTitle } from '@/hooks/useDocumentTitle';
import { useGmailConnection } from '@/hooks/useGmail';
import { useDashboard } from '@/hooks/useWarranties';
import { useAuth } from '@/lib/auth-context';
import { cn } from '@/lib/cn';
import { isFeatureEnabled } from '@/lib/features';
import type { DashboardSummary } from '@/types';
import { describeDueDate, formatCompactCurrency, formatCurrency, formatDate, greeting, pluralize } from '@/utils/format';
import { SERVICE_TYPE_LABELS } from '@/utils/labels';
import { dueTone } from '@/utils/service';
import { describeWarranty } from '@/utils/warranty';

export function DashboardPage() {
  useDocumentTitle('Dashboard');
  const { user } = useAuth();
  const { openUpload } = useUpload();
  const dashboard = useDashboard();
  const firstName = user?.name.split(' ')[0] ?? 'there';

  return (
    <>
      <PageHeader
        title={`${greeting()}, ${firstName}`}
        description="Here’s everything happening with your purchases and warranties."
        actions={
          <>
            {isFeatureEnabled('gmail') && (
              <ButtonLink to="/gmail" variant="secondary" leftIcon={<Mail className="size-4" aria-hidden />}>
                Import from Gmail
              </ButtonLink>
            )}
            <Button onClick={() => openUpload()} leftIcon={<Upload className="size-4" aria-hidden />}>
              Upload bill
            </Button>
          </>
        }
      />

      {dashboard.isPending ? (
        <DashboardSkeleton />
      ) : dashboard.isError ? (
        <Card>
          <ErrorState title="Could not load your dashboard" error={dashboard.error} onRetry={() => void dashboard.refetch()} />
        </Card>
      ) : dashboard.data.totalProducts === 0 && dashboard.data.totalDocuments === 0 ? (
        <Onboarding />
      ) : (
        <DashboardContent data={dashboard.data} />
      )}
    </>
  );
}

function DashboardContent({ data }: { data: DashboardSummary }) {
  const [spendingView, setSpendingView] = useState<'chart' | 'table'>('chart');
  const gmail = useGmailConnection(isFeatureEnabled('gmail'));
  const showServices = isFeatureEnabled('services');
  const { warranties } = data;

  return (
    <div className="space-y-6">
      {data.documentsToReview > 0 && (
        <Alert
          tone="warning"
          title={`${pluralize(data.documentsToReview, 'document')} ready for your review`}
          action={
            <ButtonLink to="/documents" size="sm" variant="secondary" rightIcon={<ArrowRight className="size-3.5" aria-hidden />}>
              Review
            </ButtonLink>
          }
        >
          Their details have been read — confirm them to add the products to your locker.
        </Alert>
      )}

      <section aria-label="Summary" className="grid grid-cols-2 gap-3 sm:gap-4 md:grid-cols-3 xl:grid-cols-5">
        <StatCard
          label="Total products"
          value={data.totalProducts}
          icon={<Package aria-hidden />}
          tone="brand"
          hint={`${pluralize(data.totalDocuments, 'document')} stored`}
          to="/products"
        />
        <StatCard
          label="Total spending"
          value={formatCompactCurrency(data.totalSpending, data.currency)}
          icon={<Wallet aria-hidden />}
          tone="neutral"
          hint={formatCurrency(data.totalSpending, data.currency)}
        />
        <StatCard
          label="Active warranties"
          value={warranties.active}
          icon={<ShieldCheck aria-hidden />}
          tone="success"
          to="/warranties?status=ACTIVE"
        />
        <StatCard
          label="Expiring soon"
          value={warranties.expiringSoon}
          icon={<ShieldAlert aria-hidden />}
          tone="warning"
          hint="Within 30 days"
          to="/warranties?status=EXPIRING_SOON"
        />
        <StatCard
          label="Expired"
          value={warranties.expired}
          icon={<ShieldX aria-hidden />}
          tone="danger"
          to="/warranties?status=EXPIRED"
        />
      </section>

      <div className="grid gap-6 lg:grid-cols-5">
        <Card className="lg:col-span-3">
          <CardHeader
            title="Spending by category"
            description={`${formatCurrency(data.totalSpending, data.currency)} across ${pluralize(data.totalProducts, 'purchase')}`}
            action={
              data.spendingByCategory.length > 0 && (
                <FilterTabs
                  label="Spending view"
                  value={spendingView}
                  onChange={setSpendingView}
                  options={[
                    { value: 'chart', label: 'Chart' },
                    { value: 'table', label: 'Table' },
                  ]}
                  className="text-xs"
                />
              )
            }
          />
          <CardBody>
            {data.spendingByCategory.length === 0 ? (
              <EmptyState compact icon={<Wallet aria-hidden />} title="No prices recorded yet" description="Prices from your invoices will show up here." />
            ) : spendingView === 'chart' ? (
              <SpendingByCategoryChart data={data.spendingByCategory} currency={data.currency} />
            ) : (
              <SpendingTable data={data.spendingByCategory} currency={data.currency} />
            )}
          </CardBody>
        </Card>

        <Card className="lg:col-span-2">
          <CardHeader title="Warranty status" description={`${pluralize(warranties.total, 'product')} tracked`} />
          <CardBody>
            <WarrantyStatusBar stats={warranties} />
          </CardBody>
        </Card>
      </div>

      <div className={cn('grid gap-6', showServices && 'lg:grid-cols-2')}>
        <Card>
          <CardHeader
            title="Upcoming warranty expirations"
            description="Next 90 days"
            action={
              <Link to="/warranties" className="text-sm font-medium text-brand-700 hover:text-brand-800">
                View all
              </Link>
            }
          />
          {data.upcomingExpirations.length === 0 ? (
            <EmptyState compact icon={<ShieldCheck aria-hidden />} title="Nothing expiring soon" description="No warranty ends in the next 90 days." />
          ) : (
            <ul className="mt-2 divide-y divide-slate-100">
              {data.upcomingExpirations.map((warranty) => (
                <li key={warranty.id}>
                  <Link
                    to={`/products/${warranty.productId}`}
                    className="flex items-center gap-3 px-5 py-3.5 transition-colors hover:bg-slate-50 sm:px-6"
                  >
                    <ProductIcon name={warranty.productName} categorySlug={warranty.categorySlug} size="sm" />
                    <div className="min-w-0 flex-1">
                      <p className="truncate text-sm font-medium text-slate-900">{warranty.productName}</p>
                      <p className="text-xs text-slate-500">Expires {formatDate(warranty.expiryDate)}</p>
                    </div>
                    <Badge tone={warranty.status === 'EXPIRING_SOON' ? 'warning' : 'neutral'}>{describeWarranty(warranty)}</Badge>
                  </Link>
                </li>
              ))}
            </ul>
          )}
        </Card>

        {showServices && (
          <Card>
            <CardHeader
              title="Upcoming services"
              description="Maintenance due in the next 60 days"
              action={
                <Link to="/services" className="text-sm font-medium text-brand-700 hover:text-brand-800">
                  View all
                </Link>
              }
            />
            {data.upcomingServices.length === 0 ? (
              <EmptyState compact icon={<Wrench aria-hidden />} title="No services due" description="Add a next service date when you log maintenance." />
            ) : (
              <ul className="mt-2 divide-y divide-slate-100">
                {data.upcomingServices.map((record) => (
                  <li key={record.id}>
                    <Link
                      to={`/products/${record.productId}`}
                      className="flex items-center gap-3 px-5 py-3.5 transition-colors hover:bg-slate-50 sm:px-6"
                    >
                      <span className="flex size-8 shrink-0 items-center justify-center rounded-lg bg-sky-50 text-sky-600">
                        <CalendarClock className="size-4" aria-hidden />
                      </span>
                      <div className="min-w-0 flex-1">
                        <p className="truncate text-sm font-medium text-slate-900">{record.productName}</p>
                        <p className="text-xs text-slate-500">
                          {SERVICE_TYPE_LABELS[record.serviceType]} · {formatDate(record.nextServiceDate)}
                        </p>
                      </div>
                      {record.nextServiceDate && (
                        <Badge tone={dueTone(record.nextServiceDate)}>{describeDueDate(record.nextServiceDate)}</Badge>
                      )}
                    </Link>
                  </li>
                ))}
              </ul>
            )}
          </Card>
        )}
      </div>

      <Card>
        <CardHeader
          title="Recent documents"
          description="The latest bills and receipts in your locker"
          action={
            <Link to="/documents" className="text-sm font-medium text-brand-700 hover:text-brand-800">
              View all
            </Link>
          }
        />
        {data.recentDocuments.length === 0 ? (
          <EmptyState
            compact
            icon={<FileText aria-hidden />}
            title="No documents yet"
            description={isFeatureEnabled('gmail') ? 'Upload a bill or import one from Gmail.' : 'Upload a bill to keep it here.'}
          />
        ) : (
          <DocumentList documents={data.recentDocuments} variant="compact" className="mt-2" />
        )}
      </Card>

      {gmail.data && !gmail.data.connected && (
        <Card className="flex flex-col items-start gap-4 p-5 sm:flex-row sm:items-center sm:p-6">
          <span className="flex size-11 shrink-0 items-center justify-center rounded-2xl bg-rose-50 text-rose-600">
            <Mail className="size-5" aria-hidden />
          </span>
          <div className="flex-1">
            <p className="font-semibold text-slate-900">Your bills are probably already in your inbox</p>
            <p className="text-sm text-slate-500">Connect Gmail and let AI find invoices and warranty cards for you.</p>
          </div>
          <ButtonLink to="/gmail" variant="secondary">
            Connect Gmail
          </ButtonLink>
        </Card>
      )}
    </div>
  );
}

function Onboarding() {
  const { openUpload } = useUpload();
  const [manualOpen, setManualOpen] = useState(false);

  const options = [
    {
      icon: Upload,
      title: 'Upload a bill',
      text: 'Snap a photo or drop a PDF. Bill Locker reads it for you.',
      action: <Button onClick={() => openUpload()}>Upload your first bill</Button>,
    },
    isFeatureEnabled('gmail') && {
      icon: Mail,
      title: 'Import from Gmail',
      text: 'Let AI find invoices already sitting in your inbox.',
      action: (
        <ButtonLink to="/gmail" variant="secondary">
          Connect Gmail
        </ButtonLink>
      ),
    },
    {
      icon: PackagePlus,
      title: 'Add manually',
      text: 'No bill at hand? Enter the product details yourself.',
      action: (
        <Button variant="secondary" onClick={() => setManualOpen(true)}>
          Add a product
        </Button>
      ),
    },
  ].filter((option) => option !== false);

  return (
    <Card className="px-6 py-10 text-center sm:px-10 sm:py-14">
      <h2 className="text-xl font-semibold text-slate-900">No products yet</h2>
      <p className="mx-auto mt-1 max-w-md text-sm text-slate-500">
        Upload your first bill to get started — Bill Locker will track the warranty and remind you before it expires.
      </p>
      <div className={cn('mt-8 grid gap-4 text-left', options.length === 3 ? 'md:grid-cols-3' : 'md:grid-cols-2')}>
        {options.map(({ icon: Icon, title, text, action }) => (
          <div key={title} className="flex flex-col rounded-2xl border border-slate-200 p-5">
            <span className="flex size-10 items-center justify-center rounded-xl bg-brand-50 text-brand-600">
              <Icon className="size-5" aria-hidden />
            </span>
            <p className="mt-4 font-semibold text-slate-900">{title}</p>
            <p className="mt-1 flex-1 text-sm text-slate-500">{text}</p>
            <div className="mt-5">{action}</div>
          </div>
        ))}
      </div>
      <ProductFormDialog open={manualOpen} onClose={() => setManualOpen(false)} />
    </Card>
  );
}

function DashboardSkeleton() {
  return (
    <div className="space-y-6" aria-busy="true" aria-label="Loading dashboard">
      <div className="grid grid-cols-2 gap-3 sm:gap-4 md:grid-cols-3 xl:grid-cols-5">
        {Array.from({ length: 5 }, (_, index) => (
          <Skeleton key={index} className="h-28 rounded-2xl" />
        ))}
      </div>
      <div className="grid gap-6 lg:grid-cols-5">
        <Skeleton className="h-80 rounded-2xl lg:col-span-3" />
        <Skeleton className="h-80 rounded-2xl lg:col-span-2" />
      </div>
      <div className="grid gap-6 lg:grid-cols-2">
        <Skeleton className="h-64 rounded-2xl" />
        <Skeleton className="h-64 rounded-2xl" />
      </div>
    </div>
  );
}
