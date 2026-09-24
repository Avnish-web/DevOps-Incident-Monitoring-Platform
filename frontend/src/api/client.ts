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

/**
 * Minimal fetch wrapper: same-origin JSON requests (the dev server and Nginx proxy /api),
 * Problem Details errors turned into {@link ApiError}.
 */
export async function request<T>(path: string, options: RequestOptions = {}): Promise<ApiResponse<T>> {
  const headers: Record<string, string> = { Accept: 'application/json', ...options.headers };
  let body: string | undefined;
  if (options.body !== undefined) {
    headers['Content-Type'] = 'application/json';
    body = JSON.stringify(options.body);
  }

  const response = await fetch(path, {
    method: options.method ?? 'GET',
    headers,
    body,
    credentials: 'same-origin',
    signal: options.signal,
  });

  if (!response.ok) {
    throw new ApiError(response.status, await readProblem(response));
  }
  const data = response.status === 204 ? (undefined as T) : ((await response.json()) as T);
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
