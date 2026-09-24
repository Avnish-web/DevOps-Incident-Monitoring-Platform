import { describe, expect, it, vi } from 'vitest';
import { ApiError, errorMessage, request } from './client';

function mockFetch(status: number, body: unknown, contentType = 'application/json') {
  return vi.spyOn(globalThis, 'fetch').mockResolvedValue(
    new Response(body === undefined ? null : JSON.stringify(body), {
      status,
      headers: { 'Content-Type': contentType },
    }),
  );
}

describe('request', () => {
  it('sends JSON and returns parsed data', async () => {
    const fetchSpy = mockFetch(200, { ok: true });

    const { data } = await request<{ ok: boolean }>('/api/v1/x', { method: 'POST', body: { a: 1 } });

    expect(data).toEqual({ ok: true });
    const [, init] = fetchSpy.mock.calls[0]!;
    expect(init?.method).toBe('POST');
    expect(init?.body).toBe('{"a":1}');
    expect((init?.headers as Record<string, string>)['Content-Type']).toBe('application/json');
    expect(init?.credentials).toBe('same-origin');
  });

  it('turns problem details into ApiError with field errors', async () => {
    mockFetch(400, {
      status: 400,
      detail: 'Request validation failed',
      requestId: 'req-1',
      errors: [{ field: 'url', message: 'Only http and https URLs are allowed' }],
    }, 'application/problem+json');

    const error = await request('/api/v1/x').catch((e: unknown) => e);

    expect(error).toBeInstanceOf(ApiError);
    const apiError = error as ApiError;
    expect(apiError.status).toBe(400);
    expect(apiError.fieldErrors).toEqual([{ field: 'url', message: 'Only http and https URLs are allowed' }]);
    expect(errorMessage(apiError)).toBe('Request validation failed (request req-1)');
  });

  it('handles non-JSON errors', async () => {
    vi.spyOn(globalThis, 'fetch').mockResolvedValue(new Response('Bad gateway', { status: 502 }));

    const error = (await request('/api/v1/x').catch((e: unknown) => e)) as ApiError;

    expect(error.status).toBe(502);
    expect(error.problem).toBeNull();
    expect(error.message).toBe('Request failed with status 502');
  });

  it('returns undefined for 204', async () => {
    vi.spyOn(globalThis, 'fetch').mockResolvedValue(new Response(null, { status: 204 }));
    const { data } = await request<void>('/api/v1/x', { method: 'DELETE' });
    expect(data).toBeUndefined();
  });
});
