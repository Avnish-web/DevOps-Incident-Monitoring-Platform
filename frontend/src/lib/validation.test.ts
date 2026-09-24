import { describe, expect, it } from 'vitest';
import { EMPTY_FORM, toMonitorRequest, validateMonitorForm, type MonitorForm } from './validation';

const valid: MonitorForm = { ...EMPTY_FORM, name: 'Site', url: 'https://example.com' };

describe('validateMonitorForm', () => {
  it('accepts a valid form', () => {
    expect(validateMonitorForm(valid)).toEqual({});
  });

  it.each([
    ['', 'URL is required'],
    ['not a url', 'URL is not valid'],
    ['ftp://example.com', 'Only http and https URLs are allowed'],
    ['javascript:alert(1)', 'Only http and https URLs are allowed'],
    ['https://user:pass@example.com', 'URL must not contain credentials'],
  ])('rejects url %s', (url, message) => {
    expect(validateMonitorForm({ ...valid, url }).url).toBe(message);
  });

  it('mirrors the API numeric ranges', () => {
    const errors = validateMonitorForm({
      ...valid,
      intervalSeconds: '5',
      timeoutMs: '500',
      expectedStatus: '700',
      failureThreshold: '0',
      recoveryThreshold: '11',
    });
    expect(Object.keys(errors).sort()).toEqual(
      ['expectedStatus', 'failureThreshold', 'intervalSeconds', 'recoveryThreshold', 'timeoutMs'],
    );
  });

  it('rejects non-integer numbers and blank names', () => {
    expect(validateMonitorForm({ ...valid, intervalSeconds: '60.5' }).intervalSeconds).toMatch(/whole number/);
    expect(validateMonitorForm({ ...valid, name: '   ' }).name).toBe('Name is required');
  });
});

describe('toMonitorRequest', () => {
  it('converts strings and treats empty expected status as null', () => {
    expect(toMonitorRequest({ ...valid, name: '  Site ', expectedStatus: '' })).toEqual({
      name: 'Site',
      url: 'https://example.com',
      httpMethod: 'GET',
      intervalSeconds: 60,
      timeoutMs: 5000,
      expectedStatus: null,
      failureThreshold: 3,
      recoveryThreshold: 2,
      enabled: true,
    });
  });
});
