import { describe, expect, it } from 'vitest';
import { validateUploadFile } from './file';

const MB = 1024 * 1024;

describe('validateUploadFile', () => {
  it('accepts PDFs and images', () => {
    expect(validateUploadFile({ name: 'invoice.pdf', type: 'application/pdf', size: 200_000 }, 10 * MB).valid).toBe(true);
    expect(validateUploadFile({ name: 'bill.JPG', type: 'image/jpeg', size: 2 * MB }, 10 * MB).valid).toBe(true);
    expect(validateUploadFile({ name: 'scan.webp', type: 'image/webp', size: 1_000 }, 10 * MB).valid).toBe(true);
  });

  it('rejects unsupported types, including renamed executables', () => {
    expect(validateUploadFile({ name: 'setup.exe', type: 'application/x-msdownload', size: 1_000 }, 10 * MB).valid).toBe(false);
    expect(validateUploadFile({ name: 'invoice.pdf.exe', type: 'application/pdf', size: 1_000 }, 10 * MB).valid).toBe(false);
    expect(validateUploadFile({ name: 'page.html', type: 'text/html', size: 1_000 }, 10 * MB).error).toMatch(/unsupported/i);
  });

  it('rejects empty and oversized files', () => {
    expect(validateUploadFile({ name: 'empty.pdf', type: 'application/pdf', size: 0 }, 10 * MB).error).toMatch(/empty/i);
    const result = validateUploadFile({ name: 'huge.pdf', type: 'application/pdf', size: 11 * MB }, 10 * MB);
    expect(result.valid).toBe(false);
    expect(result.error).toMatch(/too large/i);
  });
});
