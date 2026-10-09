import { FileText, Package, Plus, ReceiptText, ShieldCheck, Store, Trash2, UserRound, type LucideIcon } from 'lucide-react';
import type { ReactNode } from 'react';
import {
  emptyItem,
  formatDate,
  itemToText,
  warrantyEndFor,
  type FormValues,
  type ItemValues,
} from '../../lib/document-form';
import {
  BILL_CATEGORY_LABELS,
  DOCUMENT_TYPE_LABELS,
  FIELD_LABELS,
  ITEM_LABELS,
  type RegistrationSource,
} from '../../lib/documents';
import { useFeedback } from '../../lib/feedback-context';
import { TextField } from '../FormParts';
import { CopyButton } from './CopyButton';
import { RegistrationLink } from './RegistrationLink';

type Props = {
  value: FormValues;
  onChange: (value: FormValues) => void;
  /** From the backend: "buyerEmail" or "items[0].warrantyEndDate" → message. */
  fieldErrors: Record<string, string>;
  /** Task 5: finding a confirmed brand's registration page, run by the document page. */
  registration: RegistrationSearch;
};

type RegistrationSearch = {
  /** Is product `index`'s page being searched for right now? */
  finding: (index: number) => boolean;
  /** False for a product the backend doesn't know yet (added since the last save). */
  canSearch: (index: number) => boolean;
  /** The user confirmed the brand: find its page and open it in a new tab. */
  onConfirm: (index: number, brand: string) => void;
};

/** The details of a bill, grouped like the CSV, each field with its own copy button. */
export function DetailsForm({ value, onChange, fieldErrors, registration }: Props) {
  const { confirm } = useFeedback();
  type Field = keyof typeof FIELD_LABELS;
  const set = (field: Field) => (text: string) => onChange({ ...value, [field]: text });

  /** A text box for one bill-level field (a bill or receipt uses plainer labels). */
  const field = (name: Field, type = 'text', label: string = FIELD_LABELS[name]) => (
    <Field
      label={label}
      type={type}
      value={value[name]}
      onChange={set(name)}
      error={fieldErrors[name]}
    />
  );

  const setItem = (index: number, item: ItemValues) =>
    onChange({ ...value, items: value.items.map((old, i) => (i === index ? item : old)) });

  async function removeItem(index: number) {
    // Only ask when there is something to lose.
    const filled = Object.values(value.items[index]).some((text) => text.trim());
    const ok =
      !filled ||
      (await confirm({
        title: `Remove product ${index + 1}?`,
        message: 'Its details are taken off this bill when you save.',
        confirmLabel: 'Remove product',
        danger: true,
      }));
    if (!ok) return;
    onChange({ ...value, items: value.items.filter((_, i) => i !== index) });
  }

  const addItem = () => onChange({ ...value, items: [...value.items, emptyItem()] });

  const typePicker = (
    <Select
      label={FIELD_LABELS.documentType}
      value={value.documentType}
      onChange={set('documentType')}
      options={Object.entries(DOCUMENT_TYPE_LABELS)}
      empty="Not set"
    />
  );

  // A bill or receipt (a ride, a meal, a phone bill) keeps just the essentials. The other
  // fields aren't deleted, only hidden: changing the type back shows them again.
  if (value.documentType === 'RECEIPT') {
    return (
      <div className="space-y-4">
        <Card icon={ReceiptText} title="Bill / receipt" description="Just the essentials: no products or warranty.">
          {typePicker}
          <Select
            label={FIELD_LABELS.category}
            value={value.category}
            onChange={set('category')}
            options={Object.entries(BILL_CATEGORY_LABELS)}
            empty="Not set"
          />
          <Wide>{field('sellerName', 'text', 'Shop or company')}</Wide>
          {field('purchaseDate', 'date', 'Date')}
          {field('totalAmount', 'number', 'Amount')}
          <Wide>{field('documentNumber', 'text', 'Bill or receipt number')}</Wide>
        </Card>
        <p className="px-1 text-xs text-slate-600">
          Bought a product with a warranty? Change the type to <strong>Invoice</strong> to see all fields.
        </p>
      </div>
    );
  }

  return (
    <div className="space-y-4">
      <Card icon={FileText} title="Document">
        {typePicker}
        {field('documentNumber')}
      </Card>

      <Card icon={Store} title="Seller" description="The shop or company that sold it">
        {field('sellerName')}
        {field('sellerContact')}
        <Wide>{field('sellerAddress')}</Wide>
      </Card>

      <Card icon={UserRound} title="Buyer" description="Who the bill is made out to">
        {field('buyerName')}
        {field('buyerEmail', 'email')}
        <Wide>{field('buyerAddress')}</Wide>
      </Card>

      <Card icon={ReceiptText} title="Purchase">
        <Wide>
          <div className="grid gap-4 sm:grid-cols-3">
            {field('purchaseDate', 'date')}
            {field('taxAmount', 'number')}
            {field('totalAmount', 'number')}
          </div>
        </Wide>
      </Card>

      <section aria-labelledby="products-title" className="pt-2">
        <div className="flex items-center gap-2">
          <Package className="size-5 text-brand-600" aria-hidden />
          <h2 id="products-title" className="font-semibold">
            Products
          </h2>
          <span className="rounded-full bg-slate-200 px-2 py-0.5 text-xs font-medium text-slate-700 tabular-nums">
            {value.items.length}
          </span>
        </div>
        <div className="mt-3 space-y-4">
          {value.items.map((item, index) => (
            <ItemCard
              key={index}
              index={index}
              item={item}
              onChange={(next) => setItem(index, next)}
              onRemove={() => removeItem(index)}
              fieldErrors={fieldErrors}
              registration={registration}
            />
          ))}
          {/* At the end of the list: you add the next product where you finished the last one. */}
          <button
            type="button"
            onClick={addItem}
            className="flex w-full items-center justify-center gap-1.5 rounded-xl border-2 border-dashed border-slate-300 px-4 py-4 text-sm font-medium text-slate-600 hover:border-brand-500 hover:bg-brand-50 hover:text-brand-700"
          >
            <Plus className="size-4" aria-hidden />
            {value.items.length === 0 ? 'Add the first product' : 'Add another product'}
          </button>
        </div>
      </section>
    </div>
  );
}

function ItemCard(props: {
  index: number;
  item: ItemValues;
  onChange: (item: ItemValues) => void;
  onRemove: () => void;
  fieldErrors: Record<string, string>;
  registration: RegistrationSearch;
}) {
  const { index, item } = props;
  const field = (name: keyof typeof ITEM_LABELS, type = 'text', hint?: string) => (
    <Field
      label={ITEM_LABELS[name]}
      type={type}
      value={item[name]}
      onChange={(text) => props.onChange({ ...item, [name]: text })}
      error={props.fieldErrors[`items[${index}].${name}`]}
      hint={hint}
    />
  );

  // An empty end date is filled in on save; say which date it will be.
  const computedEnd = item.warrantyEndDate ? null : warrantyEndFor(item.warrantyStartDate, item.warrantyPeriodMonths);
  const endHint = computedEnd ? `Will be ${formatDate(computedEnd)} when you save` : 'Or leave empty: start + months';

  return (
    <article className="rounded-xl border border-slate-200 bg-white shadow-xs">
      <header className="flex items-center justify-between gap-2 border-b border-slate-100 px-5 py-3">
        <div className="min-w-0">
          <p className="text-xs font-medium tracking-wide text-slate-500 uppercase">Product {index + 1}</p>
          <h3 className="truncate font-semibold">{item.productName.trim() || 'Unnamed product'}</h3>
        </div>
        <div className="flex shrink-0 gap-1">
          <CopyButton text={itemToText(item, index)} label={`Copy product ${index + 1}`}>
            Copy
          </CopyButton>
          <button
            type="button"
            onClick={props.onRemove}
            aria-label={`Remove product ${index + 1}`}
            title="Remove product"
            className="rounded-lg p-2 text-slate-500 hover:bg-rose-50 hover:text-rose-700"
          >
            <Trash2 className="size-4" aria-hidden />
          </button>
        </div>
      </header>
      <div className="grid gap-4 p-5 sm:grid-cols-2">
        <Wide>{field('productName')}</Wide>
        {/* data-brand: "No, change the brand" puts the cursor here. */}
        <div data-brand={index}>{field('brand', 'text', 'The maker, detected from the product name')}</div>
        {field('modelNumber')}
        {field('serialNumber')}
        {field('unitPrice', 'number')}
      </div>
      <div className="rounded-b-xl border-t border-slate-100 bg-slate-50/70 p-5">
        <h4 className="flex items-center gap-1.5 text-sm font-semibold text-slate-700">
          <ShieldCheck className="size-4 text-emerald-600" aria-hidden />
          Warranty
        </h4>
        <div className="mt-3 grid gap-4 sm:grid-cols-2">
          {field('warrantyPeriodMonths', 'number')}
          {field('warrantyProvider')}
          {field('warrantyStartDate', 'date')}
          {field('warrantyEndDate', 'date', endHint)}
        </div>
        <RegistrationLink
          url={item.registrationUrl}
          source={item.registrationSource as RegistrationSource | ''}
          brand={item.brand}
          finding={props.registration.finding(index)}
          canSearch={props.registration.canSearch(index)}
          onConfirm={() => props.registration.onConfirm(index, item.brand.trim())}
          onChangeBrand={() => {
            const input = window.document.querySelector<HTMLInputElement>(`[data-brand="${index}"] input`);
            input?.focus();
            input?.select();
          }}
        />
      </div>
    </article>
  );
}

/** One group of fields in a white card, with an icon so groups are easy to find while scrolling. */
function Card({
  icon: Icon,
  title,
  description,
  children,
}: {
  icon: LucideIcon;
  title: string;
  description?: string;
  children: ReactNode;
}) {
  return (
    <section className="rounded-xl border border-slate-200 bg-white p-5 shadow-xs">
      <div className="flex items-center gap-2.5">
        <span className="grid size-8 place-items-center rounded-lg bg-brand-50 text-brand-600">
          <Icon className="size-4" aria-hidden />
        </span>
        <div>
          <h2 className="leading-tight font-semibold">{title}</h2>
          {description && <p className="text-xs text-slate-600">{description}</p>}
        </div>
      </div>
      <div className="mt-4 grid gap-4 sm:grid-cols-2">{children}</div>
    </section>
  );
}

/** A labelled drop-down (document type, category), styled like the text boxes. */
function Select(props: {
  label: string;
  value: string;
  onChange: (value: string) => void;
  options: [string, string][];
  empty: string;
}) {
  return (
    <label className="block text-sm font-medium text-slate-700">
      {props.label}
      <select
        value={props.value}
        onChange={(e) => props.onChange(e.target.value)}
        className="mt-1.5 block w-full rounded-lg border border-slate-300 bg-white px-3 py-2 text-sm font-normal shadow-xs outline-none focus:border-brand-500 focus:ring-2 focus:ring-brand-100"
      >
        <option value="">{props.empty}</option>
        {props.options.map(([value, label]) => (
          <option key={value} value={value}>
            {label}
          </option>
        ))}
      </select>
    </label>
  );
}

/** Spans both columns (addresses, product name). */
const Wide = ({ children }: { children: ReactNode }) => <div className="sm:col-span-2">{children}</div>;

/** A TextField with a copy button inside it. */
function Field(props: {
  label: string;
  type: string;
  value: string;
  onChange: (value: string) => void;
  error?: string;
  hint?: string;
}) {
  return (
    <TextField
      label={props.label}
      type={props.type}
      // Amounts have cents; "any" lets the browser accept 1499.5 as well as 1499.
      step={props.type === 'number' ? 'any' : undefined}
      min={props.type === 'number' ? 0 : undefined}
      inputMode={props.type === 'number' ? 'decimal' : undefined}
      value={props.value}
      onChange={(e) => props.onChange(e.target.value)}
      error={props.error}
      hint={props.hint}
      trailing={<CopyButton text={props.value} label={`Copy ${props.label.toLowerCase()}`} />}
    />
  );
}
