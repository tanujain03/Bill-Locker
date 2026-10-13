/**
 * "Try with sample data": demo bills (dated from today) that fill every page, and that can
 * be removed again in one go. The user's own bills are never touched (SampleDataService).
 */
import { api } from './api';

/** sampleBills = 0: no sample data, the "Try with sample data" button shows. */
export type SampleDataStatus = { sampleBills: number };

export const getSampleData = () => api<SampleDataStatus>('/sample-data');
export const addSampleData = () => api<SampleDataStatus>('/sample-data', { method: 'POST' });
export const removeSampleData = () => api<SampleDataStatus>('/sample-data', { method: 'DELETE' });
