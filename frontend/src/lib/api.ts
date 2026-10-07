/**
 * The one place that talks to the backend. Every call goes to /api/... (Vite
 * forwards it to Spring Boot), adds the login token if we have one, and turns
 * error responses into an ApiError the pages can show.
 */

const TOKEN_KEY = 'billLocker.token';

/**
 * Where the login token is kept. "Remember me" → localStorage (stays after the
 * browser is closed); otherwise sessionStorage (gone when the browser closes).
 * Both survive a page reload.
 */
export const tokenStore = {
  get: () => localStorage.getItem(TOKEN_KEY) ?? sessionStorage.getItem(TOKEN_KEY),
  set: (token: string, remember: boolean) => {
    tokenStore.clear();
    (remember ? localStorage : sessionStorage).setItem(TOKEN_KEY, token);
  },
  clear: () => {
    localStorage.removeItem(TOKEN_KEY);
    sessionStorage.removeItem(TOKEN_KEY);
  },
};

/** The backend's error body: { success: false, code, message, fieldErrors? }. */
export class ApiError extends Error {
  readonly status: number;
  readonly code: string;
  /** Form field name → message to show under that input. */
  readonly fieldErrors: Record<string, string>;

  constructor(status: number, code: string, message: string, fieldErrors: Record<string, string> = {}) {
    super(message);
    this.status = status;
    this.code = code;
    this.fieldErrors = fieldErrors;
  }
}

const SERVER_DOWN = 'Can’t reach the server. Is the backend running on port 8080?';

/**
 * Calls the backend and returns its JSON answer. `body` is sent as JSON, except a
 * FormData (a file upload), which the browser sends as multipart/form-data.
 */
export async function api<T>(path: string, options: { method?: string; body?: unknown } = {}): Promise<T> {
  const response = await send(path, options);
  const data = await response.json().catch(() => null); // null when the body isn't JSON (e.g. 204)
  if (!response.ok) throw toApiError(response.status, data);
  return data as T;
}

/** Downloads a file (e.g. a stored bill) as a Blob, with the login token. */
export async function fetchBlob(path: string): Promise<Blob> {
  const response = await send(path, {});
  if (!response.ok) throw toApiError(response.status, await response.json().catch(() => null));
  return response.blob();
}

async function send(path: string, options: { method?: string; body?: unknown }): Promise<Response> {
  const headers: Record<string, string> = {};
  const isForm = options.body instanceof FormData;
  // For FormData the browser sets Content-Type itself (it includes the part boundary).
  if (options.body !== undefined && !isForm) headers['Content-Type'] = 'application/json';
  const token = tokenStore.get();
  if (token) headers.Authorization = `Bearer ${token}`;

  try {
    return await fetch(`/api${path}`, {
      method: options.method ?? (options.body !== undefined ? 'POST' : 'GET'),
      headers,
      body: isForm ? (options.body as FormData) : options.body !== undefined ? JSON.stringify(options.body) : undefined,
    });
  } catch {
    throw new ApiError(0, 'NETWORK_ERROR', SERVER_DOWN);
  }
}

function toApiError(status: number, data: { code: string; message: string; fieldErrors?: Record<string, string> } | null) {
  // No JSON body usually means Vite couldn't reach Spring Boot at all.
  if (!data) return new ApiError(status, 'NETWORK_ERROR', SERVER_DOWN);
  return new ApiError(status, data.code, data.message, data.fieldErrors);
}

/** A message that is safe to show for any thrown value. */
export function errorMessage(error: unknown): string {
  return error instanceof ApiError ? error.message : 'Something went wrong. Please try again.';
}
