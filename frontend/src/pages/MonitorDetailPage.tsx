import { Link, useParams } from 'react-router';
import { useQuery } from '@tanstack/react-query';
import { getMonitor } from '../api/monitors';
import { ErrorBanner } from '../components/ErrorBanner';
import { IncidentTable } from '../components/IncidentTable';
import { StatusBadge } from '../components/StatusBadge';
import { listIncidents } from '../api/incidents';
import { dateTime, relativeTime } from '../lib/format';

export function MonitorDetailPage() {
  const { id = '' } = useParams();
  const monitor = useQuery({ queryKey: ['monitor', id], queryFn: () => getMonitor(id), refetchInterval: 15_000 });
  const incidents = useQuery({
    queryKey: ['incidents', { monitorId: id }],
    queryFn: () => listIncidents({ monitorId: id, size: 10 }),
    refetchInterval: 15_000,
  });

  if (monitor.isPending) return <p>Loading…</p>;
  if (monitor.error) return <ErrorBanner error={monitor.error} />;
  const m = monitor.data;

  return (
    <section>
      <div className="page-header">
        <h1>
          {m.name} <StatusBadge status={m.status} enabled={m.enabled} />
        </h1>
        <Link className="button" to={`/monitors/${m.id}/edit`}>
          Edit
        </Link>
      </div>
      <dl className="details">
        <dt>URL</dt>
        <dd className="url">{m.url}</dd>
        <dt>Check</dt>
        <dd>
          {m.httpMethod} every {m.intervalSeconds}s, timeout {m.timeoutMs} ms, expects{' '}
          {m.expectedStatus ?? 'any 2xx/3xx'}
        </dd>
        <dt>Thresholds</dt>
        <dd>
          down after {m.failureThreshold} failure(s), up after {m.recoveryThreshold} success(es)
        </dd>
        <dt>Last check</dt>
        <dd>
          {relativeTime(m.lastCheckedAt)} ({dateTime(m.lastCheckedAt)})
        </dd>
      </dl>

      <h2>Recent incidents</h2>
      <ErrorBanner error={incidents.error} />
      {incidents.data && <IncidentTable incidents={incidents.data.items} showMonitor={false} />}
    </section>
  );
}
