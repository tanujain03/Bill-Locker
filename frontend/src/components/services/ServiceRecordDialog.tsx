import { zodResolver } from '@hookform/resolvers/zod';
import { useEffect, useId } from 'react';
import { useForm } from 'react-hook-form';
import { Button } from '@/components/ui/Button';
import { Field, Input, Select, Textarea } from '@/components/ui/Field';
import { Modal } from '@/components/ui/Modal';
import { useToast } from '@/components/ui/toast-context';
import { useProducts } from '@/hooks/useProducts';
import { useSaveServiceRecord } from '@/hooks/useServiceRecords';
import { getErrorMessage } from '@/lib/api-client';
import { applyServerFieldErrors } from '@/lib/form-errors';
import { SERVICE_TYPES, type ServiceRecord } from '@/types';
import { addMonths, isValidISODate, parseISODate, todayISO, toISODate } from '@/utils/date';
import { formatDate } from '@/utils/format';
import { SERVICE_TYPE_LABELS } from '@/utils/labels';
import {
  SERVICE_FORM_FIELDS,
  serviceFormSchema,
  serviceToFormValues,
  toServiceInput,
  type ServiceFormValues,
} from './service-form';

const NEXT_SERVICE_PRESETS = [
  { months: 3, label: '+3 months' },
  { months: 6, label: '+6 months' },
  { months: 12, label: '+1 year' },
];

interface ServiceRecordDialogProps {
  open: boolean;
  onClose: () => void;
  record?: ServiceRecord | null;
  /** Pre-selects (and locks) the product, e.g. when opened from a product page. */
  productId?: string;
}

export function ServiceRecordDialog({ open, onClose, record, productId }: ServiceRecordDialogProps) {
  const formId = useId();
  const toast = useToast();
  const { data: products = [] } = useProducts();
  const saveRecord = useSaveServiceRecord();
  const isEdit = Boolean(record);

  const form = useForm<ServiceFormValues>({
    resolver: zodResolver(serviceFormSchema),
    defaultValues: serviceToFormValues(record, productId),
  });
  const {
    register,
    reset,
    watch,
    setValue,
    setError,
    handleSubmit,
    formState: { errors, isSubmitting },
  } = form;

  useEffect(() => {
    if (open) reset(serviceToFormValues(record, productId));
  }, [open, record, productId, reset]);

  const serviceDate = watch('serviceDate');

  const onSubmit = handleSubmit(async (values) => {
    try {
      await saveRecord.mutateAsync({ id: record?.id, input: toServiceInput(values) });
      toast.success(isEdit ? 'Service record updated' : 'Service record added');
      onClose();
    } catch (error) {
      if (!applyServerFieldErrors(error, setError, SERVICE_FORM_FIELDS)) {
        toast.error('Could not save the service record', getErrorMessage(error));
      }
    }
  });

  const lockedProduct = Boolean(productId) && !isEdit;

  return (
    <Modal
      open={open}
      onClose={onClose}
      size="lg"
      dismissible={!isSubmitting}
      title={isEdit ? 'Edit service record' : 'Add a service record'}
      description="Track repairs and maintenance. Set a next service date and we’ll remind you."
      footer={
        <>
          <Button variant="secondary" onClick={onClose} disabled={isSubmitting}>
            Cancel
          </Button>
          <Button type="submit" form={formId} loading={isSubmitting}>
            {isEdit ? 'Save changes' : 'Add record'}
          </Button>
        </>
      }
    >
      <form id={formId} onSubmit={onSubmit} noValidate className="grid gap-x-4 gap-y-5 sm:grid-cols-2">
        {lockedProduct ? (
          <Field label="Product" className="sm:col-span-2">
            <Input value={products.find((product) => product.id === productId)?.name ?? 'This product'} readOnly />
            <input type="hidden" {...register('productId')} />
          </Field>
        ) : (
          <Field label="Product" required error={errors.productId?.message} className="sm:col-span-2">
            <Select {...register('productId')}>
              <option value="">Choose a product</option>
              {products.map((product) => (
                <option key={product.id} value={product.id}>
                  {product.name}
                </option>
              ))}
            </Select>
          </Field>
        )}

        <Field label="Service date" required error={errors.serviceDate?.message}>
          <Input type="date" max={todayISO()} data-autofocus {...register('serviceDate')} />
        </Field>

        <Field label="Type" required error={errors.serviceType?.message}>
          <Select {...register('serviceType')}>
            {SERVICE_TYPES.map((type) => (
              <option key={type} value={type}>
                {SERVICE_TYPE_LABELS[type]}
              </option>
            ))}
          </Select>
        </Field>

        <Field label="Service center" error={errors.serviceCenter?.message}>
          <Input placeholder="e.g. Authorised service center" autoComplete="off" {...register('serviceCenter')} />
        </Field>

        <Field label="Cost" error={errors.cost?.message}>
          <Input inputMode="decimal" placeholder="0" {...register('cost')} />
        </Field>

        <Field
          label="Next service date"
          error={errors.nextServiceDate?.message}
          hint="Optional — you’ll get a reminder a week before."
          className="sm:col-span-2"
        >
          <div className="flex flex-wrap items-center gap-2">
            <Input type="date" className="w-auto" {...register('nextServiceDate')} />
            {isValidISODate(serviceDate) &&
              NEXT_SERVICE_PRESETS.map((preset) => {
                const date = toISODate(addMonths(parseISODate(serviceDate), preset.months));
                return (
                  <button
                    key={preset.months}
                    type="button"
                    title={formatDate(date)}
                    onClick={() => setValue('nextServiceDate', date, { shouldDirty: true, shouldValidate: true })}
                    className="h-8 rounded-full border border-slate-200 bg-white px-3 text-xs font-medium text-slate-600 hover:border-slate-300 hover:bg-slate-50"
                  >
                    {preset.label}
                  </button>
                );
              })}
          </div>
        </Field>

        <Field label="Notes" error={errors.notes?.message} className="sm:col-span-2">
          <Textarea rows={3} placeholder="What was done, parts replaced, technician name…" {...register('notes')} />
        </Field>
      </form>
    </Modal>
  );
}
