import type { FieldViolation, ProblemDetail } from './types';

/** An HTTP error from the API, carrying the parsed Problem Details when available. */
export class ApiError extends Error {
  readonly status: number;
  readonly problem: ProblemDetail | null;

  constructor(status: number, problem: ProblemDetail | null) {
    super(problem?.detail ?? problem?.title ?? `Request failed with status ${status}`);
    this.name = 'ApiError';
    this.status = status;
    this.problem = problem;
  }

  get fieldErrors(): FieldViolation[] {
    return this.problem?.errors ?? [];
  }

  get requestId(): string | undefined {
    return this.problem?.requestId;
  }
}

export interface RequestOptions {
  method?: 'GET' | 'POST' | 'PUT' | 'DELETE';
  body?: unknown;
  headers?: Record<string, string>;
  signal?: AbortSignal;
}

export interface ApiResponse<T> {
  data: T;
  headers: Headers;
}

const CSRF_COOKIE = 'XSRF-TOKEN';
const CSRF_HEADER = 'X-XSRF-TOKEN';

function readCookie(name: string): string | undefined {
  const entry = document.cookie.split('; ').find((c) => c.startsWith(`${name}=`));
  return entry ? decodeURIComponent(entry.slice(name.length + 1)) : undefined;
}

/** Asks the API to issue a CSRF cookie (the token is rotated at login). */
export async function refreshCsrfToken(): Promise<string | undefined> {
  await fetch('/api/v1/auth/csrf', { credentials: 'same-origin' });
  return readCookie(CSRF_COOKIE);
}

async function csrfToken(): Promise<string | undefined> {
  return readCookie(CSRF_COOKIE) ?? (await refreshCsrfToken());
}

/**
 * Minimal fetch wrapper: same-origin JSON requests (the dev server and Nginx proxy /api) with
 * the session cookie, a CSRF header on state-changing requests, and Problem Details errors
 * turned into {@link ApiError}.
 */
export async function request<T>(path: string, options: RequestOptions = {}): Promise<ApiResponse<T>> {
  const method = options.method ?? 'GET';
  const send = async (token: string | undefined) => {
    const headers: Record<string, string> = { Accept: 'application/json', ...options.headers };
    let body: string | undefined;
    if (options.body !== undefined) {
      headers['Content-Type'] = 'application/json';
      body = JSON.stringify(options.body);
    }
    if (method !== 'GET' && token) {
      headers[CSRF_HEADER] = token;
    }
    return fetch(path, { method, headers, body, credentials: 'same-origin', signal: options.signal });
  };

  let response = await send(method === 'GET' ? undefined : await csrfToken());
  if (response.status === 403 && method !== 'GET') {
    // The token may have been rotated (e.g. after login): refresh once and retry.
    response = await send(await refreshCsrfToken());
  }

  if (!response.ok) {
    throw new ApiError(response.status, await readProblem(response));
  }
  const text = response.status === 204 ? '' : await response.text();
  const data = (text ? JSON.parse(text) : undefined) as T;
  return { data, headers: response.headers };
}

async function readProblem(response: Response): Promise<ProblemDetail | null> {
  const contentType = response.headers.get('Content-Type') ?? '';
  if (!contentType.includes('json')) {
    return null;
  }
  try {
    return (await response.json()) as ProblemDetail;
  } catch {
    return null;
  }
}

/** Human-readable message for any error thrown by a query or mutation. */
export function errorMessage(error: unknown): string {
  if (error instanceof ApiError) {
    const suffix = error.requestId ? ` (request ${error.requestId})` : '';
    return `${error.message}${suffix}`;
  }
  if (error instanceof Error) {
    return error.message;
  }
  return 'Unexpected error';
}
