// @vitest-environment node
import axios from 'axios';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { DEMO_CREDENTIALS } from '@/lib/config';
import type { AuthResponse, DocumentDetail, DocumentSummary } from '@/types';

/**
 * Contract test of the document pipeline the UI relies on:
 * upload → UPLOADED/PROCESSING (OCR → EXTRACTION) → REVIEW_REQUIRED | FAILED.
 */
const api = axios.create({ baseURL: 'http://localhost/api', validateStatus: () => true });

async function login(): Promise<string> {
  const { data } = await api.post<AuthResponse>('/auth/login', DEMO_CREDENTIALS);
  return data.token;
}

function upload(token: string, file: File) {
  const form = new FormData();
  form.append('file', file);
  return api.post<DocumentSummary>('/documents/upload', form, { headers: { Authorization: `Bearer ${token}` } });
}

describe('document pipeline (mock API)', () => {
  beforeEach(() => {
    vi.useFakeTimers({ toFake: ['Date'] });
  });
  afterEach(() => {
    vi.useRealTimers();
  });

  it('accepts a valid upload and moves it to review with an extraction', async () => {
    const token = await login();
    const response = await upload(token, new File(['%PDF-1.4 dell invoice'], 'dell_invoice.pdf', { type: 'application/pdf' }));
    expect(response.status).toBe(201);
    expect(response.data.processingStatus).toBe('UPLOADED');

    vi.setSystemTime(Date.now() + 3000);
    const processing = await api.get<DocumentDetail>(`/documents/${response.data.id}`, { headers: { Authorization: `Bearer ${token}` } });
    expect(processing.data.processingStatus).toBe('PROCESSING');
    expect(processing.data.processingStage).toBe('EXTRACTION');
    expect(processing.data.extraction).toBeNull();

    vi.setSystemTime(Date.now() + 5000);
    const ready = await api.get<DocumentDetail>(`/documents/${response.data.id}`, { headers: { Authorization: `Bearer ${token}` } });
    expect(ready.data.processingStatus).toBe('REVIEW_REQUIRED');
    expect(ready.data.extraction?.brand).toBe('Dell');
    expect(ready.data.extractedText).toMatch(/TAX INVOICE/);
  });

  it('rejects unsupported files server-side', async () => {
    const token = await login();
    const response = await upload(token, new File(['<html></html>'], 'page.html', { type: 'text/html' }));
    expect(response.status).toBe(415);
    expect(response.data).toMatchObject({ success: false, code: 'UNSUPPORTED_FILE_TYPE' });
  });

  it('reports processing failures and succeeds on retry', async () => {
    const token = await login();
    const auth = { headers: { Authorization: `Bearer ${token}` } };
    const { data: document } = await upload(token, new File(['blurry'], 'blurry_photo.jpg', { type: 'image/jpeg' }));

    vi.setSystemTime(Date.now() + 4000);
    const failed = await api.get<DocumentDetail>(`/documents/${document.id}`, auth);
    expect(failed.data.processingStatus).toBe('FAILED');
    expect(failed.data.errorMessage).toMatch(/blurry/);

    await api.post(`/documents/${document.id}/reprocess`, undefined, auth);
    vi.setSystemTime(Date.now() + 8000);
    const retried = await api.get<DocumentDetail>(`/documents/${document.id}`, auth);
    expect(retried.data.processingStatus).toBe('REVIEW_REQUIRED');
  });
});
