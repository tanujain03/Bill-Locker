import { zodResolver } from '@hookform/resolvers/zod';
import { CheckCheck, FileSearch, Link2, PackagePlus, Sparkles, SquarePen, type LucideIcon } from 'lucide-react';
import { useEffect, useMemo, useRef, useState } from 'react';
import { useForm } from 'react-hook-form';
import { useNavigate } from 'react-router';
import { ProductFields } from '@/components/products/ProductFields';
import {
  extractionToFormValues,
  mergeProductWithExtraction,
  PRODUCT_FORM_FIELDS,
  productFormSchema,
  productToFormValues,
  toProductInput,
  type ProductFormValues,
} from '@/components/products/product-form';
import { Button } from '@/components/ui/Button';
import { Card, CardBody, CardHeader } from '@/components/ui/Card';
import { Field, Select } from '@/components/ui/Field';
import { useToast } from '@/components/ui/toast-context';
import { useConfirmDocument } from '@/hooks/useDocuments';
import { useCategories, useProducts } from '@/hooks/useProducts';
import { getErrorMessage } from '@/lib/api-client';
import { cn } from '@/lib/cn';
import { config } from '@/lib/config';
import { applyServerFieldErrors } from '@/lib/form-errors';
import { DOCUMENT_TYPES, type Category, type DocumentDetail, type DocumentType } from '@/types';
import { calculateWarrantyExpiry, isValidISODate } from '@/utils/date';
import { formatCurrency, formatDate } from '@/utils/format';
import { DOCUMENT_TYPE_LABELS } from '@/utils/labels';
import { formatWarrantyPeriod } from '@/utils/warranty';
import { ConfidenceBadge } from './ConfidenceBadge';
import {
  confidenceLevel,
  REVIEW_FIELDS,
  summarizeExtraction,
  type FieldSource,
  type ReviewField,
} from './confidence';

type Target = 'new' | 'existing';

interface ExtractionReviewProps {
  document: DocumentDetail;
}

/**
 * The human-in-the-loop step: AI results are shown with per-field confidence,
 * the user checks/edits them, and only "Confirm & Save" creates or updates data.
 */
export function ExtractionReview({ document }: ExtractionReviewProps) {
  const extraction = document.extraction;
  const navigate = useNavigate();
  const toast = useToast();
  const { data: categories = [] } = useCategories();
  const productsQuery = useProducts();
  const products = useMemo(() => productsQuery.data ?? [], [productsQuery.data]);
  const confirmDocument = useConfirmDocument(document.id);

  const suggestedCategoryId = categories.find((c) => c.slug === extraction?.suggestedCategorySlug)?.id ?? '';
  const extractedValues = useMemo(
    () => extractionToFormValues(extraction, suggestedCategoryId),
    [extraction, suggestedCategoryId],
  );

  const [documentType, setDocumentType] = useState<DocumentType>(extraction?.documentType ?? document.documentType);
  const [target, setTarget] = useState<Target>(document.productId ? 'existing' : 'new');
  const [productId, setProductId] = useState(document.productId ?? '');
  const [productError, setProductError] = useState<string>();
  const [editing, setEditing] = useState(() => !extraction?.productName);

  const form = useForm<ProductFormValues>({
    resolver: zodResolver(productFormSchema),
    defaultValues: extractedValues,
  });
  const { reset, setValue, getValues, watch, handleSubmit, setError, formState } = form;

  // Categories load asynchronously: apply the AI's category suggestion once they arrive.
  useEffect(() => {
    if (suggestedCategoryId && !getValues('categoryId')) setValue('categoryId', suggestedCategoryId);
  }, [suggestedCategoryId, getValues, setValue]);

  // "Existing product": show the saved values, filling only empty fields from the document.
  const selectedProduct = target === 'existing' ? products.find((product) => product.id === productId) : undefined;
  const mergedFor = useRef<string | null>(null);
  useEffect(() => {
    if (!selectedProduct || mergedFor.current === selectedProduct.id) return;
    mergedFor.current = selectedProduct.id;
    reset(mergeProductWithExtraction(selectedProduct, extractedValues));
  }, [selectedProduct, extractedValues, reset]);

  function chooseTarget(next: Target) {
    setTarget(next);
    setProductError(undefined);
    if (next === 'new') {
      mergedFor.current = null;
      reset(extractedValues);
    }
  }

  const savedValues = useMemo(
    () => (selectedProduct ? productToFormValues(selectedProduct) : null),
    [selectedProduct],
  );

  function sourceFor(field: ReviewField): FieldSource | null {
    if (formState.dirtyFields[field.key]) return 'edited';
    if (savedValues && savedValues[field.key].trim() !== '') return 'saved';
    if (field.extractionKey === null) return extraction?.suggestedCategorySlug && getValues('categoryId') ? 'suggested' : null;
    return confidenceLevel(extraction?.[field.extractionKey], extraction?.confidence?.[field.extractionKey]);
  }

  const scoreFor = (field: ReviewField) =>
    field.extractionKey ? extraction?.confidence?.[field.extractionKey] : undefined;

  const addons = Object.fromEntries(
    REVIEW_FIELDS.map((field) => [field.key, <ConfidenceBadge key={field.key} source={sourceFor(field)} score={scoreFor(field)} />]),
  );

  const onConfirm = handleSubmit(
    async (values) => {
      if (target === 'existing' && !productId) {
        setProductError('Choose the product this document belongs to.');
        return;
      }
      try {
        const result = await confirmDocument.mutateAsync({
          documentType,
          productId: target === 'existing' ? productId : null,
          product: toProductInput(values),
        });
        toast.success(
          'Saved to your locker',
          target === 'existing' ? `${result.product.name} was updated.` : `${result.product.name} is now being tracked.`,
        );
        navigate(`/products/${result.product.id}`);
      } catch (error) {
        if (applyServerFieldErrors(error, setError, PRODUCT_FORM_FIELDS)) setEditing(true);
        else toast.error('Could not save', getErrorMessage(error));
      }
    },
    () => setEditing(true),
  );

  const values = watch();
  const stats = summarizeExtraction(extraction);
  const typeSource = confidenceLevel(extraction?.documentType, extraction?.confidence?.documentType);

  return (
    <form onSubmit={onConfirm} noValidate className="space-y-5">
      <AiSummary stats={stats} />

      <Card>
        <CardHeader title="Save as" description="Choose what this document is and where it belongs." />
        <CardBody className="space-y-5">
          <Field
            label="Document type"
            labelAddon={
              documentType === extraction?.documentType ? (
                <ConfidenceBadge source={typeSource} score={extraction?.confidence?.documentType} />
              ) : (
                <ConfidenceBadge source="edited" />
              )
            }
          >
            <Select value={documentType} onChange={(event) => setDocumentType(event.target.value as DocumentType)}>
              {DOCUMENT_TYPES.map((type) => (
                <option key={type} value={type}>
                  {DOCUMENT_TYPE_LABELS[type]}
                </option>
              ))}
            </Select>
          </Field>

          <fieldset>
            <legend className="text-sm font-medium text-slate-700">Link to</legend>
            <div className="mt-2 grid gap-2 sm:grid-cols-2">
              <TargetOption
                name={`target-${document.id}`}
                checked={target === 'new'}
                onSelect={() => chooseTarget('new')}
                icon={PackagePlus}
                title="A new product"
                description="Create a product and its warranty from this document."
              />
              <TargetOption
                name={`target-${document.id}`}
                checked={target === 'existing'}
                onSelect={() => chooseTarget('existing')}
                icon={Link2}
                title="An existing product"
                description={
                  products.length > 0 ? 'Attach it to something you already track.' : 'You have no products yet.'
                }
                disabled={products.length === 0}
              />
            </div>
          </fieldset>

          {target === 'existing' && (
            <Field label="Product" required error={productError}>
              <Select
                value={productId}
                onChange={(event) => {
                  setProductId(event.target.value);
                  setProductError(undefined);
                }}
              >
                <option value="">Choose a product</option>
                {products.map((product) => (
                  <option key={product.id} value={product.id}>
                    {product.name}
                    {product.brand ? ` — ${product.brand}` : ''}
                  </option>
                ))}
              </Select>
            </Field>
          )}
        </CardBody>
      </Card>

      <Card>
        <CardHeader
          icon={<Sparkles className="size-4 text-brand-600" aria-hidden />}
          title="AI extracted information"
          description={
            editing
              ? 'Correct anything that looks wrong. Nothing is saved until you confirm.'
              : 'Check each value against the document. Fields marked “Not found” were not on the document — the AI never guesses.'
          }
        />
        <CardBody>
          {editing ? (
            <ProductFields form={form} categories={categories} addons={addons} />
          ) : (
            <ReadOnlyFields values={values} categories={categories} sourceFor={sourceFor} scoreFor={scoreFor} />
          )}
        </CardBody>
      </Card>

      {document.extractedText && (
        <details className="group rounded-2xl border border-slate-200 bg-white shadow-card">
          <summary className="flex cursor-pointer list-none items-center gap-2 px-5 py-4 text-sm font-medium text-slate-700 sm:px-6">
            <FileSearch className="size-4 text-slate-500" aria-hidden />
            View text read from the document (OCR)
            <span className="ml-auto text-xs text-slate-400 group-open:hidden">Show</span>
            <span className="ml-auto hidden text-xs text-slate-400 group-open:inline">Hide</span>
          </summary>
          <pre className="scrollbar-thin max-h-72 overflow-auto border-t border-slate-100 px-5 py-4 font-mono text-xs leading-relaxed whitespace-pre-wrap text-slate-600 sm:px-6">
            {document.extractedText}
          </pre>
        </details>
      )}

      <div className="sticky bottom-0 z-10 -mx-4 flex flex-wrap items-center justify-end gap-2 border-t border-slate-200 bg-white/95 px-4 py-3 backdrop-blur sm:mx-0 sm:rounded-2xl sm:border sm:shadow-card">
        {config.apiMocking && (
          <span className="mr-auto hidden text-xs text-slate-500 md:inline">Demo mode · extraction simulated by the mock API</span>
        )}
        <Button variant="ghost" onClick={() => navigate('/documents')} disabled={formState.isSubmitting}>
          Cancel
        </Button>
        {!editing && (
          <Button variant="secondary" onClick={() => setEditing(true)} leftIcon={<SquarePen className="size-4" aria-hidden />}>
            Edit
          </Button>
        )}
        <Button type="submit" loading={formState.isSubmitting} leftIcon={<CheckCheck className="size-4" aria-hidden />}>
          Confirm & Save
        </Button>
      </div>
    </form>
  );
}

function AiSummary({ stats }: { stats: ReturnType<typeof summarizeExtraction> }) {
  return (
    <div className="flex items-start gap-3 rounded-2xl border border-brand-100 bg-gradient-to-br from-brand-50 to-white p-4 sm:p-5">
      <span className="flex size-9 shrink-0 items-center justify-center rounded-xl bg-brand-600 text-white">
        <Sparkles className="size-[18px]" aria-hidden />
      </span>
      <div className="min-w-0">
        <p className="font-semibold text-slate-900">
          AI found {stats.found} of {stats.total} details
        </p>
        <p className="mt-0.5 text-sm text-slate-600">
          {stats.needsAttention > 0
            ? `${stats.needsAttention} ${stats.needsAttention === 1 ? 'field needs' : 'fields need'} your attention. `
            : 'Everything was read with high confidence. '}
          Review the details — nothing is saved until you confirm.
        </p>
      </div>
    </div>
  );
}

interface TargetOptionProps {
  name: string;
  checked: boolean;
  onSelect: () => void;
  icon: LucideIcon;
  title: string;
  description: string;
  disabled?: boolean;
}

function TargetOption({ name, checked, onSelect, icon: Icon, title, description, disabled }: TargetOptionProps) {
  return (
    <label
      className={cn(
        'flex cursor-pointer items-start gap-3 rounded-xl border p-3.5 transition-colors has-[:focus-visible]:ring-2 has-[:focus-visible]:ring-brand-500',
        checked ? 'border-brand-400 bg-brand-50/60' : 'border-slate-200 hover:border-slate-300',
        disabled && 'cursor-not-allowed opacity-60',
      )}
    >
      <input type="radio" name={name} checked={checked} onChange={onSelect} disabled={disabled} className="sr-only" />
      <Icon className={cn('mt-0.5 size-5 shrink-0', checked ? 'text-brand-600' : 'text-slate-400')} aria-hidden />
      <span>
        <span className="block text-sm font-semibold text-slate-900">{title}</span>
        <span className="block text-xs text-slate-500">{description}</span>
      </span>
    </label>
  );
}

interface ReadOnlyFieldsProps {
  values: ProductFormValues;
  categories: Category[];
  sourceFor: (field: ReviewField) => FieldSource | null;
  scoreFor: (field: ReviewField) => number | undefined;
}

function displayValue(field: ReviewField, values: ProductFormValues, categories: Category[]): string {
  const raw = values[field.key].trim();
  if (!raw) return '';
  switch (field.key) {
    case 'categoryId':
      return categories.find((category) => category.id === raw)?.name ?? '';
    case 'purchaseDate':
      return formatDate(raw);
    case 'purchasePrice':
      return formatCurrency(Number(raw), values.currency);
    case 'warrantyMonths': {
      const months = Number(raw);
      if (!Number.isFinite(months)) return raw;
      if (months === 0) return 'No warranty';
      const expiry = isValidISODate(values.purchaseDate) ? calculateWarrantyExpiry(values.purchaseDate, months) : null;
      return expiry ? `${formatWarrantyPeriod(months)} · until ${formatDate(expiry)}` : formatWarrantyPeriod(months);
    }
    default:
      return raw;
  }
}

function ReadOnlyFields({ values, categories, sourceFor, scoreFor }: ReadOnlyFieldsProps) {
  return (
    <dl className="grid gap-3 sm:grid-cols-2">
      {REVIEW_FIELDS.map((field) => {
        const source = sourceFor(field);
        const value = displayValue(field, values, categories);
        const mono = field.key === 'serialNumber' || field.key === 'invoiceNumber';
        return (
          <div
            key={field.key}
            className={cn(
              'rounded-xl border px-3.5 py-3',
              field.key === 'name' && 'sm:col-span-2',
              source === 'missing'
                ? 'border-amber-200 bg-amber-50/50'
                : source === 'low'
                  ? 'border-rose-200 bg-rose-50/50'
                  : 'border-slate-200',
            )}
          >
            <dt className="flex items-center justify-between gap-2 text-xs font-medium text-slate-500">
              {field.label}
              <ConfidenceBadge source={source} score={scoreFor(field)} />
            </dt>
            <dd className={cn('mt-1 text-sm font-medium break-words text-slate-900', mono && 'font-mono')}>
              {value || <span className="font-normal text-slate-400">—</span>}
            </dd>
          </div>
        );
      })}
    </dl>
  );
}
