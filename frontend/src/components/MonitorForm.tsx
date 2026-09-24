import { useState, type FormEvent } from 'react';
import { ApiError } from '../api/client';
import type { MonitorRequest } from '../api/types';
import {
  toMonitorRequest,
  validateMonitorForm,
  type FormErrors,
  type MonitorForm as FormState,
} from '../lib/validation';

interface Props {
  initial: FormState;
  submitLabel: string;
  onSubmit: (request: MonitorRequest) => Promise<void>;
  onCancel: () => void;
}

type TextField = Exclude<keyof FormState, 'httpMethod' | 'enabled'>;

export function MonitorForm({ initial, submitLabel, onSubmit, onCancel }: Props) {
  const [form, setForm] = useState<FormState>(initial);
  const [errors, setErrors] = useState<FormErrors>({});
  const [formError, setFormError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);

  function set<K extends keyof FormState>(key: K, value: FormState[K]) {
    setForm((f) => ({ ...f, [key]: value }));
    setErrors((e) => ({ ...e, [key]: undefined }));
  }

  async function handleSubmit(event: FormEvent) {
    event.preventDefault();
    setFormError(null);
    const clientErrors = validateMonitorForm(form);
    setErrors(clientErrors);
    if (Object.keys(clientErrors).length > 0) return;

    setSubmitting(true);
    try {
      await onSubmit(toMonitorRequest(form));
    } catch (error) {
      if (error instanceof ApiError && error.fieldErrors.length > 0) {
        const serverErrors: FormErrors = {};
        for (const v of error.fieldErrors) {
          if (v.field in form) serverErrors[v.field as keyof FormState] = v.message;
        }
        setErrors(serverErrors);
      }
      setFormError(error instanceof ApiError && error.status === 412
        ? 'This monitor was changed by someone else. Reload the page and try again.'
        : error instanceof Error ? error.message : 'Saving failed');
    } finally {
      setSubmitting(false);
    }
  }

  function textInput(key: TextField, label: string, props: { type?: string; placeholder?: string; hint?: string } = {}) {
    const id = `monitor-${key}`;
    return (
      <div className="field">
        <label htmlFor={id}>{label}</label>
        <input
          id={id}
          type={props.type ?? 'text'}
          value={form[key]}
          placeholder={props.placeholder}
          aria-invalid={errors[key] ? true : undefined}
          aria-describedby={errors[key] ? `${id}-error` : undefined}
          onChange={(e) => set(key, e.target.value)}
        />
        {props.hint && !errors[key] && <small className="hint">{props.hint}</small>}
        {errors[key] && (
          <small id={`${id}-error`} className="field-error">
            {errors[key]}
          </small>
        )}
      </div>
    );
  }

  return (
    <form className="monitor-form" onSubmit={handleSubmit} noValidate>
      {formError && (
        <div className="error-banner" role="alert">
          {formError}
        </div>
      )}
      {textInput('name', 'Name', { placeholder: 'Public website' })}
      {textInput('url', 'URL', { type: 'url', placeholder: 'https://example.com/health' })}
      <div className="field">
        <label htmlFor="monitor-httpMethod">Method</label>
        <select
          id="monitor-httpMethod"
          value={form.httpMethod}
          onChange={(e) => set('httpMethod', e.target.value as FormState['httpMethod'])}
        >
          <option value="GET">GET</option>
          <option value="HEAD">HEAD</option>
        </select>
      </div>
      <div className="field-row">
        {textInput('intervalSeconds', 'Interval (s)', { type: 'number', hint: '30 – 86400' })}
        {textInput('timeoutMs', 'Timeout (ms)', { type: 'number', hint: '1000 – 30000' })}
        {textInput('expectedStatus', 'Expected status', { type: 'number', hint: 'Empty = any 2xx/3xx' })}
      </div>
      <div className="field-row">
        {textInput('failureThreshold', 'Failures before down', { type: 'number', hint: '1 – 10' })}
        {textInput('recoveryThreshold', 'Successes before up', { type: 'number', hint: '1 – 10' })}
      </div>
      <div className="field checkbox">
        <input
          id="monitor-enabled"
          type="checkbox"
          checked={form.enabled}
          onChange={(e) => set('enabled', e.target.checked)}
        />
        <label htmlFor="monitor-enabled">Enabled</label>
      </div>
      <div className="actions">
        <button type="submit" className="primary" disabled={submitting}>
          {submitting ? 'Saving…' : submitLabel}
        </button>
        <button type="button" onClick={onCancel} disabled={submitting}>
          Cancel
        </button>
      </div>
    </form>
  );
}
