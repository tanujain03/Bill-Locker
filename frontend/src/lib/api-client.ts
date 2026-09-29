import axios, { type AxiosError } from 'axios';
import type { ApiErrorBody } from '@/types';
import { config } from './config';
import { tokenStorage } from './token-storage';

/** Normalised error thrown by every service call. */
export class ApiError extends Error {
  readonly status: number;
  readonly code: string;
  readonly fieldErrors?: Record<string, string>;

  constructor(params: { status: number; code: string; message: string; fieldErrors?: Record<string, string> }) {
    super(params.message);
    this.name = 'ApiError';
    this.status = params.status;
    this.code = params.code;
    this.fieldErrors = params.fieldErrors;
  }
}

export const apiClient = axios.create({
  baseURL: config.apiBaseUrl,
  timeout: 60_000,
  headers: { Accept: 'application/json' },
});

apiClient.interceptors.request.use((request) => {
  const token = tokenStorage.get();
  if (token) request.headers.set('Authorization', `Bearer ${token}`);
  return request;
});

let unauthorizedHandler: (() => void) | null = null;

/** Registered by the auth provider: called when an authenticated call returns 401. */
export function setUnauthorizedHandler(handler: (() => void) | null): void {
  unauthorizedHandler = handler;
}

const AUTH_ENDPOINTS = ['/auth/login', '/auth/register'];

apiClient.interceptors.response.use(
  (response) => response,
  (error: unknown) => {
    const apiError = toApiError(error);
    const url = axios.isAxiosError(error) ? (error.config?.url ?? '') : '';
    const isAuthCall = AUTH_ENDPOINTS.some((endpoint) => url.endsWith(endpoint));
    if (apiError.status === 401 && !isAuthCall) unauthorizedHandler?.();
    return Promise.reject(apiError);
  },
);

function isErrorBody(data: unknown): data is ApiErrorBody {
  return (
    typeof data === 'object' &&
    data !== null &&
    typeof (data as ApiErrorBody).code === 'string' &&
    typeof (data as ApiErrorBody).message === 'string'
  );
}

const DEFAULT_MESSAGES: Record<number, [code: string, message: string]> = {
  400: ['BAD_REQUEST', 'Some of the information provided is invalid.'],
  401: ['UNAUTHORIZED', 'Your session has expired. Please sign in again.'],
  403: ['FORBIDDEN', 'You do not have access to this resource.'],
  404: ['NOT_FOUND', 'The requested item was not found.'],
  409: ['CONFLICT', 'This change conflicts with existing data.'],
  413: ['FILE_TOO_LARGE', 'The file is too large.'],
  415: ['UNSUPPORTED_FILE_TYPE', 'This file type is not supported.'],
  429: ['RATE_LIMITED', 'Too many requests. Please wait a moment and try again.'],
};

export function toApiError(error: unknown): ApiError {
  if (error instanceof ApiError) return error;

  if (axios.isAxiosError(error)) {
    const axiosError = error as AxiosError<unknown>;
    if (axiosError.code === 'ERR_CANCELED') {
      return new ApiError({ status: 0, code: 'REQUEST_CANCELLED', message: 'The request was cancelled.' });
    }
    if (!axiosError.response) {
      const timedOut = axiosError.code === 'ECONNABORTED' || axiosError.code === 'ETIMEDOUT';
      return new ApiError({
        status: 0,
        code: timedOut ? 'TIMEOUT' : 'NETWORK_ERROR',
        message: timedOut
          ? 'The server took too long to respond. Please try again.'
          : 'Unable to reach Bill Locker. Check your connection and try again.',
      });
    }
    const { status, data } = axiosError.response;
    const [fallbackCode, fallbackMessage] = DEFAULT_MESSAGES[status] ?? [
      'SERVER_ERROR',
      'Something went wrong on our side. Please try again.',
    ];
    if (isErrorBody(data)) {
      return new ApiError({ status, code: data.code, message: data.message, fieldErrors: data.fieldErrors });
    }
    return new ApiError({ status, code: fallbackCode, message: fallbackMessage });
  }

  return new ApiError({
    status: 0,
    code: 'UNKNOWN_ERROR',
    message: error instanceof Error ? error.message : 'Something went wrong.',
  });
}

/** Human-readable message for any thrown value. */
export function getErrorMessage(error: unknown, fallback = 'Something went wrong.'): string {
  if (error instanceof ApiError) return error.message;
  if (error instanceof Error && error.message) return error.message;
  return fallback;
}
