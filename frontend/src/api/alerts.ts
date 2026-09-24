import { request } from './client';
import type { Page } from './types';

export type AlertChannelType = 'WEBHOOK' | 'SLACK' | 'EMAIL';

export interface AlertChannel {
  id: string;
  name: string;
  type: AlertChannelType;
  targetPreview: string;
  enabled: boolean;
  createdAt: string;
  updatedAt: string;
  version: number;
  /** Only present in the response that created a webhook channel. */
  signingSecret?: string;
}

export interface AlertChannelRequest {
  name: string;
  type: AlertChannelType;
  target?: string;
  enabled?: boolean;
}

export interface AlertDelivery {
  id: number;
  incidentId: string | null;
  eventType: 'INCIDENT_OPENED' | 'INCIDENT_RESOLVED' | 'TEST';
  status: 'PENDING' | 'SENT' | 'FAILED' | 'CANCELLED';
  attempts: number;
  nextAttemptAt: string;
  lastError: string | null;
  createdAt: string;
  sentAt: string | null;
}

const BASE = '/api/v1/alert-channels';

export async function listChannels(): Promise<AlertChannel[]> {
  return (await request<AlertChannel[]>(BASE)).data;
}

export async function createChannel(body: AlertChannelRequest): Promise<AlertChannel> {
  return (await request<AlertChannel>(BASE, { method: 'POST', body })).data;
}

export async function updateChannel(id: string, body: AlertChannelRequest): Promise<AlertChannel> {
  return (await request<AlertChannel>(`${BASE}/${encodeURIComponent(id)}`, { method: 'PUT', body })).data;
}

export async function deleteChannel(id: string): Promise<void> {
  await request<void>(`${BASE}/${encodeURIComponent(id)}`, { method: 'DELETE' });
}

export async function testChannel(id: string): Promise<AlertDelivery> {
  return (await request<AlertDelivery>(`${BASE}/${encodeURIComponent(id)}/test`, { method: 'POST' })).data;
}

export async function listDeliveries(id: string): Promise<Page<AlertDelivery>> {
  return (await request<Page<AlertDelivery>>(`${BASE}/${encodeURIComponent(id)}/deliveries?size=10`)).data;
}
