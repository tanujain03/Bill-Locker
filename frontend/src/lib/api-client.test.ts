import { AxiosError, AxiosHeaders, type AxiosResponse } from 'axios';
import { describe, expect, it } from 'vitest';
import { ApiError, getErrorMessage, toApiError } from './api-client';

function axiosErrorWith(status: number, data: unknown): AxiosError {
  const config = { headers: new AxiosHeaders() };
  const response = { status, statusText: '', data, headers: {}, config } as AxiosResponse;
  return new AxiosError('Request failed', 'ERR_BAD_REQUEST', config, {}, response);
}

describe('toApiError', () => {
  it('uses the API error body (code + message + field errors)', () => {
    const error = toApiError(
      axiosErrorWith(400, {
        success: false,
        code: 'VALIDATION_ERROR',
        message: 'Some of the information provided is invalid.',
        fieldErrors: { name: 'Product name is required' },
      }),
    );
    expect(error).toBeInstanceOf(ApiError);
    expect(error.status).toBe(400);
    expect(error.code).toBe('VALIDATION_ERROR');
    expect(error.fieldErrors).toEqual({ name: 'Product name is required' });
  });

  it('falls back to a safe message when the body is not the API format', () => {
    const error = toApiError(axiosErrorWith(500, '<html>Internal error</html>'));
    expect(error.code).toBe('SERVER_ERROR');
    expect(error.message).not.toContain('<html>');
  });

  it('maps a missing response to a network error', () => {
    const error = toApiError(new AxiosError('Network Error', 'ERR_NETWORK'));
    expect(error.status).toBe(0);
    expect(error.code).toBe('NETWORK_ERROR');
  });

  it('keeps ApiError instances and wraps unknown values', () => {
    const original = new ApiError({ status: 404, code: 'DOCUMENT_NOT_FOUND', message: 'Missing' });
    expect(toApiError(original)).toBe(original);
    expect(getErrorMessage('boom', 'Fallback')).toBe('Fallback');
    expect(getErrorMessage(new Error('Readable'))).toBe('Readable');
  });
});
