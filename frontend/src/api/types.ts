// Mirrors the API's JSON contracts (backend/api/.../MonitorResponse, IncidentResponse, PageResponse).

export type MonitorStatus = 'UNKNOWN' | 'UP' | 'DOWN';
export type HttpCheckMethod = 'GET' | 'HEAD';

export interface Monitor {
  id: string;
  name: string;
  type: 'HTTP';
  url: string;
  httpMethod: HttpCheckMethod;
  intervalSeconds: number;
  timeoutMs: number;
  expectedStatus: number | null;
  failureThreshold: number;
  recoveryThreshold: number;
  enabled: boolean;
  status: MonitorStatus;
  lastCheckedAt: string | null;
  nextCheckAt: string;
  createdAt: string;
  updatedAt: string;
  version: number;
}

/** Body for POST/PUT /api/v1/monitors. Optional fields fall back to server defaults. */
export interface MonitorRequest {
  name: string;
  url: string;
  httpMethod?: HttpCheckMethod;
  intervalSeconds?: number;
  timeoutMs?: number;
  expectedStatus?: number | null;
  failureThreshold?: number;
  recoveryThreshold?: number;
  enabled?: boolean;
}

export type IncidentStatus = 'OPEN' | 'RESOLVED';
export type IncidentResolution = 'RECOVERED' | 'MONITOR_PAUSED' | 'MONITOR_CHANGED';

export interface Incident {
  id: string;
  monitorId: string;
  monitorName: string | null;
  status: IncidentStatus;
  startedAt: string;
  resolvedAt: string | null;
  resolution: IncidentResolution | null;
  cause: string | null;
  durationSeconds: number;
}

export interface Page<T> {
  items: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

export interface FieldViolation {
  field: string;
  message: string;
}

/** RFC 9457 Problem Details as produced by the API's GlobalExceptionHandler. */
export interface ProblemDetail {
  type?: string;
  title?: string;
  status?: number;
  detail?: string;
  instance?: string;
  requestId?: string;
  errors?: FieldViolation[];
}
