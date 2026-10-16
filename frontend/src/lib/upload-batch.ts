export type UploadEntry = { key: number; file: File };
export type UploadResult = UploadEntry & {
  status: 'waiting' | 'uploading' | 'uploaded' | 'duplicate' | 'failed';
  documentId?: string;
  message?: string;
};

export function validateUpload(file: File): string | null {
  if (file.size === 0) return 'This file is empty.';
  if (file.size > 10 * 1024 * 1024) return 'This file exceeds the 10 MB limit.';
  if (!/\.(pdf|jpe?g|png|webp)$/i.test(file.name)) return 'Choose a PDF, JPG, PNG or WebP file.';
  return null;
}

/** Sequential uploads avoid duplicate-file races and keep a large selection off the server at once. */
export async function processUploadBatch(entries: UploadEntry[], options: {
  upload: (file: File) => Promise<{ id: string }>;
  duplicateId: (error: unknown) => string | null;
  errorText: (error: unknown) => string;
  onUpdate: (result: UploadResult) => void;
  shouldContinue: () => boolean;
}) {
  const results: UploadResult[] = [];
  for (const entry of entries) {
    if (!options.shouldContinue()) break;
    let result: UploadResult;
    const invalid = validateUpload(entry.file);
    if (invalid) {
      result = { ...entry, status: 'failed', message: invalid };
    } else {
      options.onUpdate({ ...entry, status: 'uploading' });
      try {
        const document = await options.upload(entry.file);
        result = { ...entry, status: 'uploaded', documentId: document.id };
      } catch (error) {
        const documentId = options.duplicateId(error);
        result = documentId
          ? { ...entry, status: 'duplicate', documentId, message: 'Already in your locker.' }
          : { ...entry, status: 'failed', message: options.errorText(error) };
      }
    }
    results.push(result);
    if (options.shouldContinue()) options.onUpdate(result);
  }
  return results;
}
