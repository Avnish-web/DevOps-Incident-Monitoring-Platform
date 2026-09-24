import { useState, type FormEvent } from 'react';
import { ApiError } from '../api/client';
import type { AlertChannelRequest, AlertChannelType } from '../api/alerts';

const TARGET_HINTS: Record<AlertChannelType, { label: string; placeholder: string; hint: string }> = {
  EMAIL: { label: 'E-mail address', placeholder: 'oncall@example.com', hint: 'Sent via the SMTP server configured for the worker.' },
  SLACK: {
    label: 'Slack incoming webhook URL',
    placeholder: 'https://hooks.slack.com/services/…',
    hint: 'Create one in Slack: Apps → Incoming Webhooks.',
  },
  WEBHOOK: {
    label: 'Webhook URL (https)',
    placeholder: 'https://example.com/hooks/monitoring',
    hint: 'Receives signed JSON. The signing secret is shown once after saving.',
  },
};

export function AlertChannelForm({ onSubmit }: { onSubmit: (req: AlertChannelRequest) => Promise<void> }) {
  const [name, setName] = useState('');
  const [type, setType] = useState<AlertChannelType>('EMAIL');
  const [target, setTarget] = useState('');
  const [errors, setErrors] = useState<Record<string, string>>({});
  const [formError, setFormError] = useState<string | null>(null);
  const [saving, setSaving] = useState(false);
  const hint = TARGET_HINTS[type];

  async function handleSubmit(event: FormEvent) {
    event.preventDefault();
    const clientErrors: Record<string, string> = {};
    if (!name.trim()) clientErrors.name = 'Name is required';
    if (!target.trim()) clientErrors.target = 'Target is required';
    setErrors(clientErrors);
    setFormError(null);
    if (Object.keys(clientErrors).length) return;
    setSaving(true);
    try {
      await onSubmit({ name: name.trim(), type, target: target.trim(), enabled: true });
      setName('');
      setTarget('');
    } catch (error) {
      if (error instanceof ApiError && error.fieldErrors.length) {
        setErrors(Object.fromEntries(error.fieldErrors.map((v) => [v.field, v.message])));
      } else {
        setFormError(error instanceof Error ? error.message : 'Saving failed');
      }
    } finally {
      setSaving(false);
    }
  }

  return (
    <form className="monitor-form" onSubmit={handleSubmit} noValidate aria-label="Add alert channel">
      {formError && <div className="error-banner" role="alert">{formError}</div>}
      <div className="field-row">
        <div className="field">
          <label htmlFor="channel-name">Name</label>
          <input id="channel-name" value={name} onChange={(e) => setName(e.target.value)}
            aria-invalid={errors.name ? true : undefined} />
          {errors.name && <small className="field-error">{errors.name}</small>}
        </div>
        <div className="field">
          <label htmlFor="channel-type">Type</label>
          <select id="channel-type" value={type} onChange={(e) => setType(e.target.value as AlertChannelType)}>
            <option value="EMAIL">E-mail</option>
            <option value="SLACK">Slack</option>
            <option value="WEBHOOK">Webhook</option>
          </select>
        </div>
      </div>
      <div className="field">
        <label htmlFor="channel-target">{hint.label}</label>
        <input id="channel-target" value={target} placeholder={hint.placeholder}
          onChange={(e) => setTarget(e.target.value)} aria-invalid={errors.target ? true : undefined}
          autoComplete="off" />
        {errors.target ? <small className="field-error">{errors.target}</small> : <small className="hint">{hint.hint}</small>}
      </div>
      <div className="actions">
        <button type="submit" className="primary" disabled={saving}>{saving ? 'Saving…' : 'Add channel'}</button>
      </div>
    </form>
  );
}
