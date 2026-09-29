import type { ISODate, ISODateTime } from './api';

export type ServiceType = 'ROUTINE_MAINTENANCE' | 'REPAIR' | 'INSTALLATION' | 'INSPECTION' | 'OTHER';

export const SERVICE_TYPES: ServiceType[] = ['ROUTINE_MAINTENANCE', 'REPAIR', 'INSTALLATION', 'INSPECTION', 'OTHER'];

export interface ServiceRecord {
  id: string;
  productId: string;
  productName: string;
  serviceDate: ISODate;
  serviceType: ServiceType;
  serviceCenter: string | null;
  cost: number | null;
  /** Currency of the linked product. */
  currency: string;
  nextServiceDate: ISODate | null;
  notes: string | null;
  createdAt: ISODateTime;
  updatedAt: ISODateTime;
}

export interface ServiceRecordInput {
  productId: string;
  serviceDate: ISODate;
  serviceType: ServiceType;
  serviceCenter: string | null;
  cost: number | null;
  nextServiceDate: ISODate | null;
  notes: string | null;
}
