import { api } from './api';

export type ServiceType = 'REPAIR' | 'ROUTINE_SERVICE' | 'PART_REPLACED' | 'WARRANTY_CLAIM' | 'OTHER';
export type ServiceDueStatus = 'UPCOMING' | 'DUE_SOON' | 'OVERDUE' | 'NONE';
export type ClaimStatus = 'OPEN' | 'RESOLVED' | 'REJECTED';
export type ServiceRecordInput = {
  type: ServiceType; title: string; description: string | null; serviceDate: string;
  providerName: string | null; providerContact: string | null; cost: number | null;
  coveredByWarranty: boolean; claimNumber: string | null; claimStatus: ClaimStatus | null;
  nextDueDate: string | null; repeatMonths: number | null; attachmentDocumentId: string | null;
};
export type ServiceRecord = ServiceRecordInput & {
  id: string; documentId: string; productId: string; itemPosition: number; productName: string | null;
  dueStatus: ServiceDueStatus; reminderActive: boolean;
};
export type ServiceProduct = { documentId: string; productId: string; position: number };
export const SERVICE_TYPES: Record<ServiceType, string> = {
  REPAIR: 'Repair', ROUTINE_SERVICE: 'Routine service', PART_REPLACED: 'Part replaced', WARRANTY_CLAIM: 'Warranty claim', OTHER: 'Other',
};
export const DUE_LABELS: Record<ServiceDueStatus, string> = { UPCOMING: 'Upcoming', DUE_SOON: 'Due soon', OVERDUE: 'Overdue', NONE: 'No next service set' };
export function listServiceRecords(filters: { type?: string; q?: string; due?: string } = {}) {
  const params = new URLSearchParams();
  for (const [key,value] of Object.entries(filters)) if (value) params.set(key,value);
  return api<ServiceRecord[]>(`/services${params.size ? `?${params}` : ''}`);
}
export const getServiceHistory = (documentId: string, position: number) => api<ServiceRecord[]>(`/documents/${documentId}/items/${position}/services`);
async function changed<T>(request: Promise<T>): Promise<T> {
  const value = await request;
  window.dispatchEvent(new Event('services-changed'));
  return value;
}
export const createServiceRecord = (p: ServiceProduct, data: ServiceRecordInput) => changed(api<ServiceRecord>(`/documents/${p.documentId}/items/${p.position}/services?productId=${p.productId}`, { body: data }));
export const updateServiceRecord = (id: string, data: ServiceRecordInput) => changed(api<ServiceRecord>(`/services/${id}`, { method: 'PUT', body: data }));
export const deleteServiceRecord = (id: string) => changed(api<null>(`/services/${id}`, { method: 'DELETE' }));
