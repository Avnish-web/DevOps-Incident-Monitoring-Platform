import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { ApiError, errorMessage, request } from './client';

function jsonResponse(status: number, body: unknown, contentType = 'application/json') {
  return new Response(body === undefined ? null : JSON.stringify(body), {
    status,
    headers: { 'Content-Type': contentType },
  });
}

function clearCookies() {
  document.cookie.split('; ').filter(Boolean).forEach((c) => {
    document.cookie = `${c.split('=')[0]}=; expires=Thu, 01 Jan 1970 00:00:00 GMT; path=/`;
  });
}

describe('request', () => {
  beforeEach(clearCookies);
  afterEach(clearCookies);

  it('sends JSON with the session cookie policy and the CSRF header', async () => {
    document.cookie = 'XSRF-TOKEN=token-123; path=/';
    const fetchSpy = vi.spyOn(globalThis, 'fetch').mockResolvedValue(jsonResponse(200, { ok: true }));

    const { data } = await request<{ ok: boolean }>('/api/v1/x', { method: 'POST', body: { a: 1 } });

    expect(data).toEqual({ ok: true });
    const [, init] = fetchSpy.mock.calls[0]!;
    const headers = init?.headers as Record<string, string>;
    expect(init?.method).toBe('POST');
    expect(init?.body).toBe('{"a":1}');
    expect(headers['Content-Type']).toBe('application/json');
    expect(headers['X-XSRF-TOKEN']).toBe('token-123');
    expect(init?.credentials).toBe('same-origin');
  });

  it('never sends the CSRF header on reads', async () => {
    document.cookie = 'XSRF-TOKEN=token-123; path=/';
    const fetchSpy = vi.spyOn(globalThis, 'fetch').mockResolvedValue(jsonResponse(200, []));

    await request('/api/v1/monitors');

    expect((fetchSpy.mock.calls[0]![1]?.headers as Record<string, string>)['X-XSRF-TOKEN']).toBeUndefined();
  });

  it('fetches a CSRF token first when none exists', async () => {
    const fetchSpy = vi.spyOn(globalThis, 'fetch').mockImplementation(async (input) => {
      if (String(input).endsWith('/auth/csrf')) {
        document.cookie = 'XSRF-TOKEN=fresh; path=/';
        return new Response(null, { status: 204 });
      }
      return new Response(null, { status: 204 });
    });

    await request('/api/v1/monitors/1', { method: 'DELETE' });

    expect(fetchSpy.mock.calls.map((c) => String(c[0]))).toEqual(['/api/v1/auth/csrf', '/api/v1/monitors/1']);
    expect((fetchSpy.mock.calls[1]![1]?.headers as Record<string, string>)['X-XSRF-TOKEN']).toBe('fresh');
  });

  it('retries once with a refreshed token after a CSRF rejection', async () => {
    document.cookie = 'XSRF-TOKEN=stale; path=/';
    const fetchSpy = vi.spyOn(globalThis, 'fetch')
      .mockResolvedValueOnce(jsonResponse(403, { detail: 'csrf' }, 'application/problem+json'))
      .mockImplementationOnce(async () => {
        document.cookie = 'XSRF-TOKEN=rotated; path=/';
        return new Response(null, { status: 204 });
      })
      .mockResolvedValueOnce(jsonResponse(201, { id: 'm1' }));

    const { data } = await request<{ id: string }>('/api/v1/monitors', { method: 'POST', body: {} });

    expect(data).toEqual({ id: 'm1' });
    expect((fetchSpy.mock.calls[2]![1]?.headers as Record<string, string>)['X-XSRF-TOKEN']).toBe('rotated');
  });

  it('turns problem details into ApiError with field errors', async () => {
    vi.spyOn(globalThis, 'fetch').mockResolvedValue(jsonResponse(400, {
      status: 400,
      detail: 'Request validation failed',
      requestId: 'req-1',
      errors: [{ field: 'url', message: 'Only http and https URLs are allowed' }],
    }, 'application/problem+json'));

    const error = await request('/api/v1/x').catch((e: unknown) => e);

    expect(error).toBeInstanceOf(ApiError);
    const apiError = error as ApiError;
    expect(apiError.status).toBe(400);
    expect(apiError.fieldErrors).toEqual([{ field: 'url', message: 'Only http and https URLs are allowed' }]);
    expect(errorMessage(apiError)).toBe('Request validation failed (request req-1)');
  });

  it('handles non-JSON errors and empty bodies', async () => {
    vi.spyOn(globalThis, 'fetch').mockResolvedValueOnce(new Response('Bad gateway', { status: 502 }));
    const error = (await request('/api/v1/x').catch((e: unknown) => e)) as ApiError;
    expect(error.status).toBe(502);
    expect(error.problem).toBeNull();

    vi.spyOn(globalThis, 'fetch').mockResolvedValueOnce(new Response(null, { status: 204 }));
    expect((await request<void>('/api/v1/x')).data).toBeUndefined();
  });
});
