import { Fragment, useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import {
  createChannel,
  deleteChannel,
  listChannels,
  listDeliveries,
  testChannel,
  updateChannel,
  type AlertChannel,
} from '../api/alerts';
import { AlertChannelForm } from '../components/AlertChannelForm';
import { ErrorBanner } from '../components/ErrorBanner';
import { dateTime } from '../lib/format';

const TYPE_LABELS = { EMAIL: 'E-mail', SLACK: 'Slack', WEBHOOK: 'Webhook' } as const;

function Deliveries({ channelId }: { channelId: string }) {
  const deliveries = useQuery({
    queryKey: ['deliveries', channelId],
    queryFn: () => listDeliveries(channelId),
    refetchInterval: 5_000,
  });
  if (deliveries.error) return <ErrorBanner error={deliveries.error} />;
  if (!deliveries.data) return <p>Loading…</p>;
  if (deliveries.data.items.length === 0) return <p className="empty">No deliveries yet.</p>;
  return (
    <table className="table">
      <thead>
        <tr><th>Created</th><th>Event</th><th>Status</th><th>Attempts</th><th>Last error</th></tr>
      </thead>
      <tbody>
        {deliveries.data.items.map((d) => (
          <tr key={d.id}>
            <td>{dateTime(d.createdAt)}</td>
            <td>{d.eventType}</td>
            <td>
              <span className={`badge ${d.status === 'SENT' ? 'badge-up' : d.status === 'PENDING' ? 'badge-unknown' : 'badge-down'}`}>
                {d.status}
              </span>
            </td>
            <td>{d.attempts}</td>
            <td className="cause">{d.lastError ?? ''}</td>
          </tr>
        ))}
      </tbody>
    </table>
  );
}

export function AlertChannelsPage() {
  const queryClient = useQueryClient();
  const [newSecret, setNewSecret] = useState<{ name: string; secret: string } | null>(null);
  const [expanded, setExpanded] = useState<string | null>(null);
  const channels = useQuery({ queryKey: ['alert-channels'], queryFn: listChannels });
  const invalidate = () => queryClient.invalidateQueries({ queryKey: ['alert-channels'] });

  const toggle = useMutation({
    mutationFn: (c: AlertChannel) => updateChannel(c.id, { name: c.name, type: c.type, enabled: !c.enabled }),
    onSettled: invalidate,
  });
  const remove = useMutation({ mutationFn: (c: AlertChannel) => deleteChannel(c.id), onSettled: invalidate });
  const test = useMutation({
    mutationFn: (c: AlertChannel) => testChannel(c.id),
    onSuccess: (_d, c) => {
      setExpanded(c.id);
      return queryClient.invalidateQueries({ queryKey: ['deliveries', c.id] });
    },
  });

  return (
    <section>
      <h1>Alert channels</h1>
      <p className="summary">
        Every enabled channel is notified when a monitor goes down and when it recovers.
      </p>

      {newSecret && (
        <div className="secret-box" role="status">
          <strong>Signing secret for “{newSecret.name}”. Copy it now: it will not be shown again.</strong>
          <code>{newSecret.secret}</code>
          <small>
            Verify requests with HMAC-SHA256 over <code>timestamp + "." + body</code> and compare with the{' '}
            <code>X-Monitoring-Signature</code> header.
          </small>
          <button type="button" onClick={() => setNewSecret(null)}>I have stored it</button>
        </div>
      )}

      <AlertChannelForm
        onSubmit={async (req) => {
          const created = await createChannel(req);
          if (created.signingSecret) setNewSecret({ name: created.name, secret: created.signingSecret });
          await invalidate();
        }}
      />

      <ErrorBanner error={channels.error ?? toggle.error ?? remove.error ?? test.error} />
      {channels.data && channels.data.length === 0 && <p className="empty">No alert channels yet.</p>}
      {channels.data && channels.data.length > 0 && (
        <table className="table">
          <thead>
            <tr><th>Name</th><th>Type</th><th>Target</th><th>Status</th><th aria-label="Actions" /></tr>
          </thead>
          <tbody>
            {channels.data.map((c) => (
              <Fragment key={c.id}>
                <tr>
                  <td>{c.name}</td>
                  <td>{TYPE_LABELS[c.type]}</td>
                  <td className="url">{c.targetPreview}</td>
                  <td>
                    <span className={`badge ${c.enabled ? 'badge-up' : 'badge-paused'}`}>
                      {c.enabled ? 'Enabled' : 'Disabled'}
                    </span>
                  </td>
                  <td className="row-actions">
                    <button type="button" onClick={() => test.mutate(c)} disabled={test.isPending || !c.enabled}>
                      Send test
                    </button>
                    <button type="button" onClick={() => setExpanded(expanded === c.id ? null : c.id)}>
                      {expanded === c.id ? 'Hide history' : 'History'}
                    </button>
                    <button type="button" onClick={() => toggle.mutate(c)} disabled={toggle.isPending}>
                      {c.enabled ? 'Disable' : 'Enable'}
                    </button>
                    <button type="button" className="danger" disabled={remove.isPending}
                      onClick={() => { if (window.confirm(`Delete channel "${c.name}"?`)) remove.mutate(c); }}>
                      Delete
                    </button>
                  </td>
                </tr>
                {expanded === c.id && (
                  <tr>
                    <td colSpan={5}><Deliveries channelId={c.id} /></td>
                  </tr>
                )}
              </Fragment>
            ))}
          </tbody>
        </table>
      )}
    </section>
  );
}
