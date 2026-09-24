import type { HttpCheckMethod, MonitorRequest } from '../api/types';

/** Raw form state: every input is a string until validated. */
export interface MonitorForm {
  name: string;
  url: string;
  httpMethod: HttpCheckMethod;
  intervalSeconds: string;
  timeoutMs: string;
  expectedStatus: string;
  failureThreshold: string;
  recoveryThreshold: string;
  enabled: boolean;
}

export type FormErrors = Partial<Record<keyof MonitorForm, string>>;

export const EMPTY_FORM: MonitorForm = {
  name: '',
  url: '',
  httpMethod: 'GET',
  intervalSeconds: '60',
  timeoutMs: '5000',
  expectedStatus: '',
  failureThreshold: '3',
  recoveryThreshold: '2',
  enabled: true,
};

// eslint-disable-next-line no-control-regex
const CONTROL_CHARS = /[\u0000-\u001f\u007f]/;

function integerInRange(value: string, min: number, max: number, label: string): string | undefined {
  if (!/^\d+$/.test(value.trim())) return `${label} must be a whole number`;
  const n = Number(value);
  if (n < min || n > max) return `${label} must be between ${min} and ${max}`;
  return undefined;
}

/**
 * Client-side checks that mirror the API's rules, for fast feedback. The API remains the
 * authority (including the private-address check, which needs DNS).
 */
export function validateMonitorForm(form: MonitorForm): FormErrors {
  const errors: FormErrors = {};

  const name = form.name.trim();
  if (!name) errors.name = 'Name is required';
  else if (name.length > 100) errors.name = 'Name must be at most 100 characters';
  else if (CONTROL_CHARS.test(name)) errors.name = 'Name must not contain control characters';

  const url = form.url.trim();
  if (!url) {
    errors.url = 'URL is required';
  } else if (url.length > 2048) {
    errors.url = 'URL must be at most 2048 characters';
  } else {
    let parsed: URL | null = null;
    try {
      parsed = new URL(url);
    } catch {
      errors.url = 'URL is not valid';
    }
    if (parsed) {
      if (parsed.protocol !== 'http:' && parsed.protocol !== 'https:') {
        errors.url = 'Only http and https URLs are allowed';
      } else if (parsed.username || parsed.password) {
        errors.url = 'URL must not contain credentials';
      }
    }
  }

  const interval = integerInRange(form.intervalSeconds, 30, 86400, 'Interval');
  if (interval) errors.intervalSeconds = interval;
  const timeout = integerInRange(form.timeoutMs, 1000, 30000, 'Timeout');
  if (timeout) errors.timeoutMs = timeout;
  if (form.expectedStatus.trim()) {
    const expected = integerInRange(form.expectedStatus, 100, 599, 'Expected status');
    if (expected) errors.expectedStatus = expected;
  }
  const failure = integerInRange(form.failureThreshold, 1, 10, 'Failure threshold');
  if (failure) errors.failureThreshold = failure;
  const recovery = integerInRange(form.recoveryThreshold, 1, 10, 'Recovery threshold');
  if (recovery) errors.recoveryThreshold = recovery;

  return errors;
}

export function toMonitorRequest(form: MonitorForm): MonitorRequest {
  return {
    name: form.name.trim(),
    url: form.url.trim(),
    httpMethod: form.httpMethod,
    intervalSeconds: Number(form.intervalSeconds),
    timeoutMs: Number(form.timeoutMs),
    expectedStatus: form.expectedStatus.trim() ? Number(form.expectedStatus) : null,
    failureThreshold: Number(form.failureThreshold),
    recoveryThreshold: Number(form.recoveryThreshold),
    enabled: form.enabled,
  };
}

export function fromMonitor(m: {
  name: string;
  url: string;
  httpMethod: HttpCheckMethod;
  intervalSeconds: number;
  timeoutMs: number;
  expectedStatus: number | null;
  failureThreshold: number;
  recoveryThreshold: number;
  enabled: boolean;
}): MonitorForm {
  return {
    name: m.name,
    url: m.url,
    httpMethod: m.httpMethod,
    intervalSeconds: String(m.intervalSeconds),
    timeoutMs: String(m.timeoutMs),
    expectedStatus: m.expectedStatus == null ? '' : String(m.expectedStatus),
    failureThreshold: String(m.failureThreshold),
    recoveryThreshold: String(m.recoveryThreshold),
    enabled: m.enabled,
  };
}
