import { EllipsisVertical, FileText, PackageX, Pencil, Plus, Sparkles, Trash, Upload, Wrench } from 'lucide-react';
import { useState, type ReactNode } from 'react';
import { useNavigate, useParams } from 'react-router';
import { DocumentList } from '@/components/documents/DocumentList';
import { useUpload } from '@/components/documents/upload-context';
import { ProductFormDialog } from '@/components/products/ProductFormDialog';
import { ProductIcon } from '@/components/products/ProductIcon';
import { ServiceHistory } from '@/components/services/ServiceHistory';
import { ServiceRecordDialog } from '@/components/services/ServiceRecordDialog';
import { Badge } from '@/components/ui/Badge';
import { Button, ButtonLink } from '@/components/ui/Button';
import { Card, CardHeader } from '@/components/ui/Card';
import { ConfirmDialog } from '@/components/ui/ConfirmDialog';
import { EmptyState, ErrorState, PageLoader, Skeleton } from '@/components/ui/feedback';
import { Menu } from '@/components/ui/Menu';
import { CopyButton } from '@/components/ui/misc';
import { PageHeader } from '@/components/ui/PageHeader';
import { useToast } from '@/components/ui/toast-context';
import { WarrantyCard } from '@/components/warranties/WarrantyCard';
import { WarrantyStatusBadge } from '@/components/warranties/WarrantyStatus';
import { useDocuments } from '@/hooks/useDocuments';
import { useDocumentTitle } from '@/hooks/useDocumentTitle';
import { useDeleteProduct, useProduct } from '@/hooks/useProducts';
import { useDeleteServiceRecord, useServiceRecords } from '@/hooks/useServiceRecords';
import { ApiError, getErrorMessage } from '@/lib/api-client';
import { DOCUMENT_TYPES, type DocumentSummary, type Product, type ServiceRecord } from '@/types';
import { formatCurrency, formatDate } from '@/utils/format';
import { DOCUMENT_TYPE_LABELS } from '@/utils/labels';
import { productSubtitle } from '@/utils/product';
import { warrantyStatusOf } from '@/utils/warranty';

export function ProductDetailsPage() {
  const { id } = useParams<{ id: string }>();
  const product = useProduct(id);
  useDocumentTitle(product.data?.name ?? 'Product');

  if (product.isPending) return <PageLoader />;
  if (product.isError) {
    const notFound = product.error instanceof ApiError && product.error.status === 404;
    return (
      <Card className="mt-4">
        {notFound ? (
          <EmptyState
            icon={<PackageX aria-hidden />}
            title="Product not found"
            description="It may have been deleted, or it belongs to another account."
            action={<ButtonLink to="/products">Back to products</ButtonLink>}
          />
        ) : (
          <ErrorState title="Could not load this product" error={product.error} onRetry={() => void product.refetch()} />
        )}
      </Card>
    );
  }
  return <ProductDetails product={product.data} />;
}

function ProductDetails({ product }: { product: Product }) {
  const navigate = useNavigate();
  const toast = useToast();
  const { openUpload } = useUpload();
  const documents = useDocuments({ productId: product.id });
  const services = useServiceRecords(product.id);
  const deleteProduct = useDeleteProduct();
  const deleteService = useDeleteServiceRecord();

  const [editOpen, setEditOpen] = useState(false);
  const [deleteOpen, setDeleteOpen] = useState(false);
  const [serviceDialog, setServiceDialog] = useState<{ open: boolean; record: ServiceRecord | null }>({ open: false, record: null });
  const [serviceToDelete, setServiceToDelete] = useState<ServiceRecord | null>(null);

  async function confirmDeleteProduct() {
    try {
      await deleteProduct.mutateAsync(product.id);
      toast.success('Product deleted', `${product.name} was removed. Its documents are still in your locker.`);
      navigate('/products', { replace: true });
    } catch (error) {
      toast.error('Could not delete the product', getErrorMessage(error));
    }
  }

  async function confirmDeleteService() {
    if (!serviceToDelete) return;
    try {
      await deleteService.mutateAsync(serviceToDelete.id);
      toast.success('Service record deleted');
      setServiceToDelete(null);
    } catch (error) {
      toast.error('Could not delete the service record', getErrorMessage(error));
    }
  }

  const askAi = `/assistant?q=${encodeURIComponent(`Is my ${product.name} still under warranty?`)}`;

  return (
    <>
      <PageHeader
        back={{ to: '/products', label: 'My Products' }}
        title={
          <span className="flex items-center gap-3">
            <ProductIcon name={product.name} categorySlug={product.categorySlug} size="md" className="hidden sm:flex" />
            <span className="min-w-0">{product.name}</span>
          </span>
        }
        description={productSubtitle(product)}
        eyebrow={
          <div className="flex flex-wrap gap-2">
            <WarrantyStatusBadge status={warrantyStatusOf(product.warranty)} />
            {product.categoryName && <Badge tone="neutral">{product.categoryName}</Badge>}
          </div>
        }
        actions={
          <>
            <ButtonLink to={askAi} variant="secondary" leftIcon={<Sparkles className="size-4 text-brand-600" aria-hidden />}>
              Ask AI
            </ButtonLink>
            <Button
              variant="secondary"
              onClick={() => openUpload({ productId: product.id, productName: product.name })}
              leftIcon={<Upload className="size-4" aria-hidden />}
            >
              Add document
            </Button>
            <Button onClick={() => setEditOpen(true)} leftIcon={<Pencil className="size-4" aria-hidden />}>
              Edit
            </Button>
            <Menu
              label="More product actions"
              trigger={<EllipsisVertical className="size-4" aria-hidden />}
              items={[
                {
                  label: 'Delete product',
                  icon: <Trash className="size-4" aria-hidden />,
                  tone: 'danger',
                  onSelect: () => setDeleteOpen(true),
                },
              ]}
            />
          </>
        }
      />

      <div className="grid gap-6 lg:grid-cols-3">
        <Card className="lg:col-span-2">
          <CardHeader title="Purchase details" />
          <dl className="grid gap-x-6 gap-y-5 px-5 py-5 sm:grid-cols-2 sm:px-6">
            <Detail label="Product">{product.name}</Detail>
            <Detail label="Brand">{product.brand}</Detail>
            <Detail label="Model">{product.model}</Detail>
            <Detail
              label="Serial number"
              mono
              addon={product.serialNumber ? <CopyButton value={product.serialNumber} label="Copy serial number" /> : null}
            >
              {product.serialNumber}
            </Detail>
            <Detail label="Purchase date">{product.purchaseDate ? formatDate(product.purchaseDate) : null}</Detail>
            <Detail label="Price">
              {product.purchasePrice !== null ? formatCurrency(product.purchasePrice, product.currency) : null}
            </Detail>
            <Detail label="Seller">{product.seller}</Detail>
            <Detail
              label="Invoice number"
              mono
              addon={product.invoiceNumber ? <CopyButton value={product.invoiceNumber} label="Copy invoice number" /> : null}
            >
              {product.invoiceNumber}
            </Detail>
          </dl>
        </Card>

        <WarrantyCard product={product} onEdit={() => setEditOpen(true)} />
      </div>

      <Card className="mt-6">
        <CardHeader
          icon={<FileText className="size-4 text-slate-400" aria-hidden />}
          title="Documents"
          description="Invoices, warranty cards and service receipts for this product"
          action={
            <Button
              size="sm"
              variant="secondary"
              onClick={() => openUpload({ productId: product.id, productName: product.name })}
              leftIcon={<Plus className="size-4" aria-hidden />}
            >
              Add
            </Button>
          }
        />
        <div className="mt-2">
          {documents.isPending ? (
            <div className="space-y-3 px-5 py-4 sm:px-6">
              <Skeleton className="h-12 w-full" />
              <Skeleton className="h-12 w-full" />
            </div>
          ) : documents.isError ? (
            <ErrorState compact error={documents.error} onRetry={() => void documents.refetch()} />
          ) : documents.data.length === 0 ? (
            <EmptyState compact icon={<FileText aria-hidden />} title="No documents attached" description="Upload the invoice or warranty card to keep them with the product." />
          ) : (
            <GroupedDocuments documents={documents.data} />
          )}
        </div>
      </Card>

      <Card className="mt-6">
        <CardHeader
          icon={<Wrench className="size-4 text-slate-400" aria-hidden />}
          title="Service history"
          description={product.nextServiceDate ? `Next service ${formatDate(product.nextServiceDate)}` : 'Repairs and maintenance'}
          action={
            <Button
              size="sm"
              variant="secondary"
              onClick={() => setServiceDialog({ open: true, record: null })}
              leftIcon={<Plus className="size-4" aria-hidden />}
            >
              Add service
            </Button>
          }
        />
        <div className="mt-2">
          {services.isPending ? (
            <div className="space-y-3 px-5 py-4 sm:px-6">
              <Skeleton className="h-14 w-full" />
            </div>
          ) : services.isError ? (
            <ErrorState compact error={services.error} onRetry={() => void services.refetch()} />
          ) : services.data.length === 0 ? (
            <EmptyState compact icon={<Wrench aria-hidden />} title="No service records" description="Log repairs and maintenance, and set a next service date for reminders." />
          ) : (
            <ServiceHistory
              records={services.data}
              showProduct={false}
              onEdit={(record) => setServiceDialog({ open: true, record })}
              onDelete={setServiceToDelete}
            />
          )}
        </div>
      </Card>

      <ProductFormDialog open={editOpen} onClose={() => setEditOpen(false)} product={product} />
      <ServiceRecordDialog
        open={serviceDialog.open}
        record={serviceDialog.record}
        productId={product.id}
        onClose={() => setServiceDialog({ open: false, record: null })}
      />
      <ConfirmDialog
        open={deleteOpen}
        onClose={() => setDeleteOpen(false)}
        onConfirm={() => void confirmDeleteProduct()}
        loading={deleteProduct.isPending}
        title={`Delete ${product.name}?`}
        confirmLabel="Delete product"
        message="The product, its warranty and its service history will be removed. Its documents stay in your locker, unlinked — you never lose a bill."
      />
      <ConfirmDialog
        open={serviceToDelete !== null}
        onClose={() => setServiceToDelete(null)}
        onConfirm={() => void confirmDeleteService()}
        loading={deleteService.isPending}
        title="Delete this service record?"
        confirmLabel="Delete record"
        message="This removes the record and its next-service reminder."
      />
    </>
  );
}

function Detail({ label, children, mono = false, addon }: { label: string; children: ReactNode; mono?: boolean; addon?: ReactNode }) {
  const empty = children === null || children === undefined || children === '';
  return (
    <div className="min-w-0">
      <dt className="text-xs font-medium text-slate-500">{label}</dt>
      <dd className="mt-1 flex items-center gap-1 text-sm font-medium text-slate-900">
        {empty ? (
          <span className="font-normal text-slate-400">Not recorded</span>
        ) : (
          <span className={mono ? 'font-mono break-all' : 'break-words'}>{children}</span>
        )}
        {addon}
      </dd>
    </div>
  );
}

/** Documents grouped by type: Invoice, Warranty card, Service receipts… */
function GroupedDocuments({ documents }: { documents: DocumentSummary[] }) {
  return (
    <div className="divide-y divide-slate-100 border-t border-slate-100">
      {DOCUMENT_TYPES.filter((type) => documents.some((document) => document.documentType === type)).map((type) => (
        <section key={type} aria-label={DOCUMENT_TYPE_LABELS[type]}>
          <h3 className="bg-slate-50/80 px-5 py-2 text-xs font-semibold tracking-wide text-slate-500 uppercase sm:px-6">
            {DOCUMENT_TYPE_LABELS[type]}
          </h3>
          <DocumentList documents={documents.filter((document) => document.documentType === type)} showProduct={false} />
        </section>
      ))}
    </div>
  );
}
