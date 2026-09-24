import { request } from './client';
import type { Incident, IncidentStatus, Page } from './types';

export interface IncidentFilter {
  status?: IncidentStatus;
  monitorId?: string;
  page?: number;
  size?: number;
}

export async function listIncidents(filter: IncidentFilter = {}): Promise<Page<Incident>> {
  const params = new URLSearchParams({
    page: String(filter.page ?? 0),
    size: String(filter.size ?? 20),
  });
  if (filter.status) params.set('status', filter.status);
  if (filter.monitorId) params.set('monitorId', filter.monitorId);
  return (await request<Page<Incident>>(`/api/v1/incidents?${params}`)).data;
}
