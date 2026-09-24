import { request } from './client';
import type { Page } from './types';

export type StatsRange = '1h' | '24h' | '7d' | '30d';

export interface CheckResult {
  checkedAt: string;
  success: boolean;
  statusCode: number | null;
  latencyMs: number | null;
  errorType: string | null;
  errorMessage: string | null;
}

export interface StatsBucket {
  bucketStart: string;
  checks: number;
  failures: number;
  avgLatencyMs: number | null;
  p95LatencyMs: number | null;
}

export interface MonitorStats {
  range: StatsRange;
  from: string;
  to: string;
  bucketSeconds: number;
  summary: {
    totalChecks: number;
    failedChecks: number;
    uptimePercent: number | null;
    avgLatencyMs: number | null;
    p50LatencyMs: number | null;
    p95LatencyMs: number | null;
    maxLatencyMs: number | null;
  };
  series: StatsBucket[];
}

export async function getStats(monitorId: string, range: StatsRange): Promise<MonitorStats> {
  const params = new URLSearchParams({ range });
  return (await request<MonitorStats>(`/api/v1/monitors/${encodeURIComponent(monitorId)}/stats?${params}`)).data;
}

export async function listChecks(monitorId: string, page = 0, size = 20): Promise<Page<CheckResult>> {
  const params = new URLSearchParams({ page: String(page), size: String(size) });
  return (await request<Page<CheckResult>>(`/api/v1/monitors/${encodeURIComponent(monitorId)}/checks?${params}`)).data;
}
