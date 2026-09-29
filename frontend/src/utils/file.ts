import { config } from '@/lib/config';
import { formatFileSize } from './format';

/** Keep in sync with the backend's allow-list (docs/api-contract.md). */
export const ALLOWED_MIME_TYPES = ['application/pdf', 'image/jpeg', 'image/png', 'image/webp'] as const;

export const ALLOWED_EXTENSIONS = ['.pdf', '.jpg', '.jpeg', '.png', '.webp'] as const;

/** Value for `<input accept>`. */
export const FILE_ACCEPT = [...ALLOWED_MIME_TYPES, ...ALLOWED_EXTENSIONS].join(',');

export interface FileValidationResult {
  valid: boolean;
  error?: string;
}

function extensionOf(fileName: string): string {
  const dot = fileName.lastIndexOf('.');
  return dot >= 0 ? fileName.slice(dot).toLowerCase() : '';
}

/** Client-side pre-check for fast feedback; the server re-validates everything. */
export function validateUploadFile(
  file: Pick<File, 'name' | 'size' | 'type'>,
  maxBytes: number = config.maxUploadBytes,
): FileValidationResult {
  const extension = extensionOf(file.name);
  const mimeAllowed = (ALLOWED_MIME_TYPES as readonly string[]).includes(file.type);
  const extensionAllowed = (ALLOWED_EXTENSIONS as readonly string[]).includes(extension);

  if (!extensionAllowed || (file.type && !mimeAllowed)) {
    return { valid: false, error: 'Unsupported file type. Upload a PDF, JPG, PNG or WEBP.' };
  }
  if (file.size === 0) {
    return { valid: false, error: 'This file is empty.' };
  }
  if (file.size > maxBytes) {
    return {
      valid: false,
      error: `File is too large (${formatFileSize(file.size)}). The limit is ${formatFileSize(maxBytes)}.`,
    };
  }
  return { valid: true };
}

export function isImageMime(mimeType: string): boolean {
  return mimeType.startsWith('image/');
}

export function isPdfMime(mimeType: string): boolean {
  return mimeType === 'application/pdf';
}

/** Triggers a browser download for a blob. */
export function saveBlob(blob: Blob, fileName: string): void {
  const url = URL.createObjectURL(blob);
  const anchor = document.createElement('a');
  anchor.href = url;
  anchor.download = fileName;
  anchor.rel = 'noopener';
  document.body.appendChild(anchor);
  anchor.click();
  anchor.remove();
  setTimeout(() => URL.revokeObjectURL(url), 1_000);
}
