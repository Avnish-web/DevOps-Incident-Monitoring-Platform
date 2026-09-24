import { request } from './client';
import type { Monitor, MonitorRequest, Page } from './types';

const BASE = '/api/v1/monitors';

export async function listMonitors(page = 0, size = 50): Promise<Page<Monitor>> {
  const params = new URLSearchParams({ page: String(page), size: String(size), sort: 'name,asc' });
  return (await request<Page<Monitor>>(`${BASE}?${params}`)).data;
}

export async function getMonitor(id: string): Promise<Monitor> {
  return (await request<Monitor>(`${BASE}/${encodeURIComponent(id)}`)).data;
}

export async function createMonitor(body: MonitorRequest): Promise<Monitor> {
  return (await request<Monitor>(BASE, { method: 'POST', body })).data;
}

/** Sends If-Match so a concurrent edit is rejected (412) instead of silently overwritten. */
export async function updateMonitor(id: string, version: number, body: MonitorRequest): Promise<Monitor> {
  return (
    await request<Monitor>(`${BASE}/${encodeURIComponent(id)}`, {
      method: 'PUT',
      body,
      headers: { 'If-Match': `"${version}"` },
    })
  ).data;
}

export async function deleteMonitor(id: string): Promise<void> {
  await request<void>(`${BASE}/${encodeURIComponent(id)}`, { method: 'DELETE' });
}

/** Builds a full PUT body from an existing monitor, e.g. to toggle `enabled`. */
export function toRequest(monitor: Monitor, overrides: Partial<MonitorRequest> = {}): MonitorRequest {
  return {
    name: monitor.name,
    url: monitor.url,
    httpMethod: monitor.httpMethod,
    intervalSeconds: monitor.intervalSeconds,
    timeoutMs: monitor.timeoutMs,
    expectedStatus: monitor.expectedStatus,
    failureThreshold: monitor.failureThreshold,
    recoveryThreshold: monitor.recoveryThreshold,
    enabled: monitor.enabled,
    ...overrides,
  };
}
