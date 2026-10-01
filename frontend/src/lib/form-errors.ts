import type { FieldValues, Path, UseFormSetError } from 'react-hook-form';
import { ApiError } from './api-client';

/**
 * Copies server-side `fieldErrors` (VALIDATION_ERROR) onto matching form fields.
 * Returns true when at least one field error was applied.
 */
export function applyServerFieldErrors<T extends FieldValues>(
  error: unknown,
  setError: UseFormSetError<T>,
  fields: readonly string[],
): boolean {
  if (!(error instanceof ApiError) || !error.fieldErrors) return false;
  let applied = false;
  for (const [field, message] of Object.entries(error.fieldErrors)) {
    if (fields.includes(field)) {
      setError(field as Path<T>, { type: 'server', message });
      applied = true;
    }
  }
  return applied;
}
