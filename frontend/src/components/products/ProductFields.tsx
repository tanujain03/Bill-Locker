import type { ReactNode } from 'react';
import type { UseFormReturn } from 'react-hook-form';
import { Field, Input, Select } from '@/components/ui/Field';
import { cn } from '@/lib/cn';
import type { Category } from '@/types';
import { calculateWarrantyExpiry, isValidISODate, todayISO } from '@/utils/date';
import { formatDate } from '@/utils/format';
import { CURRENCIES } from '@/utils/labels';
import type { ProductFormValues } from './product-form';

const WARRANTY_PRESETS: { months: number; label: string }[] = [
  { months: 6, label: '6 mo' },
  { months: 12, label: '1 yr' },
  { months: 24, label: '2 yrs' },
  { months: 36, label: '3 yrs' },
  { months: 60, label: '5 yrs' },
];

interface ProductFieldsProps {
  form: UseFormReturn<ProductFormValues>;
  categories: Category[];
  /** Per-field element shown next to the label (e.g. AI confidence). */
  addons?: Partial<Record<keyof ProductFormValues, ReactNode>>;
  autoFocusName?: boolean;
}

/** Product + warranty inputs shared by the manual form and the AI review screen. */
export function ProductFields({ form, categories, addons = {}, autoFocusName = false }: ProductFieldsProps) {
  const {
    register,
    setValue,
    watch,
    formState: { errors },
  } = form;
  const [purchaseDate, warrantyMonths] = watch(['purchaseDate', 'warrantyMonths']);
  const months = /^\d{1,3}$/.test(warrantyMonths.trim()) ? Number(warrantyMonths) : 0;
  const expiryPreview = isValidISODate(purchaseDate) && months > 0 ? calculateWarrantyExpiry(purchaseDate, months) : null;

  return (
    <div className="grid gap-x-4 gap-y-5 sm:grid-cols-2">
      <Field
        label="Product name"
        required
        error={errors.name?.message}
        labelAddon={addons.name}
        className="sm:col-span-2"
      >
        <Input
          placeholder="e.g. Dell Inspiron 15 laptop"
          autoComplete="off"
          data-autofocus={autoFocusName || undefined}
          {...register('name')}
        />
      </Field>

      <Field label="Category" error={errors.categoryId?.message} labelAddon={addons.categoryId}>
        <Select {...register('categoryId')}>
          <option value="">Select a category</option>
          {categories.map((category) => (
            <option key={category.id} value={category.id}>
              {category.name}
            </option>
          ))}
        </Select>
      </Field>

      <Field label="Brand" error={errors.brand?.message} labelAddon={addons.brand}>
        <Input placeholder="e.g. Dell" autoComplete="off" {...register('brand')} />
      </Field>

      <Field label="Model" error={errors.model?.message} labelAddon={addons.model}>
        <Input placeholder="e.g. Inspiron 15 3530" autoComplete="off" {...register('model')} />
      </Field>

      <Field label="Serial number" error={errors.serialNumber?.message} labelAddon={addons.serialNumber}>
        <Input placeholder="As printed on the invoice" autoComplete="off" className="font-mono" {...register('serialNumber')} />
      </Field>

      <Field label="Purchase date" error={errors.purchaseDate?.message} labelAddon={addons.purchaseDate}>
        <Input type="date" max={todayISO()} {...register('purchaseDate')} />
      </Field>

      <Field label="Price" error={errors.purchasePrice?.message} labelAddon={addons.purchasePrice}>
        <div className="flex">
          <select
            aria-label="Currency"
            className="h-10 shrink-0 rounded-l-lg border border-r-0 border-slate-300 bg-slate-50 px-2 text-sm font-medium text-slate-700 focus:outline-none focus:ring-4 focus:ring-brand-500/15"
            {...register('currency')}
          >
            {CURRENCIES.map((currency) => (
              <option key={currency} value={currency}>
                {currency}
              </option>
            ))}
          </select>
          <Input inputMode="decimal" placeholder="0" className="rounded-l-none" {...register('purchasePrice')} />
        </div>
      </Field>

      <Field label="Seller" error={errors.seller?.message} labelAddon={addons.seller}>
        <Input placeholder="Store or website" autoComplete="off" {...register('seller')} />
      </Field>

      <Field label="Invoice number" error={errors.invoiceNumber?.message} labelAddon={addons.invoiceNumber}>
        <Input autoComplete="off" className="font-mono" {...register('invoiceNumber')} />
      </Field>

      <Field
        label="Warranty (months)"
        error={errors.warrantyMonths?.message}
        labelAddon={addons.warrantyMonths}
        hint={
          expiryPreview
            ? `Covered until ${formatDate(expiryPreview)} (calculated from the purchase date).`
            : 'Leave empty if the document does not mention a warranty.'
        }
        className="sm:col-span-2"
      >
        <div className="flex flex-wrap items-center gap-2">
          <Input inputMode="numeric" placeholder="e.g. 12" className="w-28" {...register('warrantyMonths')} />
          {WARRANTY_PRESETS.map((preset) => {
            const selected = months === preset.months;
            return (
              <button
                key={preset.months}
                type="button"
                aria-pressed={selected}
                onClick={() =>
                  setValue('warrantyMonths', String(preset.months), { shouldDirty: true, shouldValidate: true })
                }
                className={cn(
                  'h-8 rounded-full border px-3 text-xs font-medium transition-colors',
                  selected
                    ? 'border-brand-300 bg-brand-50 text-brand-700'
                    : 'border-slate-200 bg-white text-slate-600 hover:border-slate-300 hover:bg-slate-50',
                )}
              >
                {preset.label}
              </button>
            );
          })}
        </div>
      </Field>
    </div>
  );
}
