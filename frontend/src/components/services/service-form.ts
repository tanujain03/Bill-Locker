import { z } from 'zod';
import type { ServiceRecord, ServiceRecordInput } from '@/types';
import { isValidISODate, todayISO } from '@/utils/date';

export const serviceFormSchema = z
  .object({
    productId: z.string().min(1, 'Choose a product'),
    serviceDate: z
      .string()
      .refine((value) => isValidISODate(value), 'Enter the service date')
      .refine((value) => value <= todayISO(), 'Service date cannot be in the future'),
    serviceType: z.enum(['ROUTINE_MAINTENANCE', 'REPAIR', 'INSTALLATION', 'INSPECTION', 'OTHER']),
    serviceCenter: z.string().trim().max(120, 'Keep it under 120 characters'),
    cost: z
      .string()
      .trim()
      .refine((value) => value === '' || /^\d+(\.\d{1,2})?$/.test(value), 'Enter a valid amount'),
    nextServiceDate: z.string().refine((value) => value === '' || isValidISODate(value), 'Enter a valid date'),
    notes: z.string().trim().max(500, 'Keep notes under 500 characters'),
  })
  .refine((values) => !values.nextServiceDate || values.nextServiceDate > values.serviceDate, {
    path: ['nextServiceDate'],
    message: 'The next service must be after the service date',
  });

export type ServiceFormValues = z.infer<typeof serviceFormSchema>;

export const SERVICE_FORM_FIELDS: (keyof ServiceFormValues)[] = [
  'productId',
  'serviceDate',
  'serviceType',
  'serviceCenter',
  'cost',
  'nextServiceDate',
  'notes',
];

export function serviceToFormValues(record?: ServiceRecord | null, productId = ''): ServiceFormValues {
  return {
    productId: record?.productId ?? productId,
    serviceDate: record?.serviceDate ?? todayISO(),
    serviceType: record?.serviceType ?? 'ROUTINE_MAINTENANCE',
    serviceCenter: record?.serviceCenter ?? '',
    cost: record?.cost === null || record?.cost === undefined ? '' : String(record.cost),
    nextServiceDate: record?.nextServiceDate ?? '',
    notes: record?.notes ?? '',
  };
}

export function toServiceInput(values: ServiceFormValues): ServiceRecordInput {
  return {
    productId: values.productId,
    serviceDate: values.serviceDate,
    serviceType: values.serviceType,
    serviceCenter: values.serviceCenter.trim() || null,
    cost: values.cost.trim() === '' ? null : Number(values.cost),
    nextServiceDate: values.nextServiceDate || null,
    notes: values.notes.trim() || null,
  };
}
