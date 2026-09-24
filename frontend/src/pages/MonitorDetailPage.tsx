import { useState } from 'react';
import { Link, useParams } from 'react-router';
import { keepPreviousData, useQuery } from '@tanstack/react-query';
import { getStats, listChecks, type StatsRange } from '../api/history';
import { listIncidents } from '../api/incidents';
import { getMonitor } from '../api/monitors';
import { ErrorBanner } from '../components/ErrorBanner';
import { IncidentTable } from '../components/IncidentTable';
import { LatencyCharts } from '../components/LatencyCharts';
import { RecentChecks } from '../components/RecentChecks';
import { StatTiles } from '../components/StatTiles';
import { StatusBadge } from '../components/StatusBadge';
import { dateTime, relativeTime } from '../lib/format';

const RANGES: StatsRange[] = ['1h', '24h', '7d', '30d'];
const REFRESH_MS = 15_000;

export function MonitorDetailPage() {
  const { id = '' } = useParams();
  const [range, setRange] = useState<StatsRange>('24h');
  const monitor = useQuery({ queryKey: ['monitor', id], queryFn: () => getMonitor(id), refetchInterval: REFRESH_MS });
  const stats = useQuery({
    queryKey: ['stats', id, range],
    queryFn: () => getStats(id, range),
    refetchInterval: REFRESH_MS,
    placeholderData: keepPreviousData,
  });
  const checks = useQuery({ queryKey: ['checks', id], queryFn: () => listChecks(id), refetchInterval: REFRESH_MS });
  const incidents = useQuery({
    queryKey: ['incidents', { monitorId: id }],
    queryFn: () => listIncidents({ monitorId: id, size: 10 }),
    refetchInterval: REFRESH_MS,
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

      <div className="section-header">
        <h2>History</h2>
        <div className="tabs" role="tablist" aria-label="Time range">
          {RANGES.map((r) => (
            <button key={r} type="button" role="tab" aria-selected={range === r}
              className={range === r ? 'active' : undefined} onClick={() => setRange(r)}>
              {r}
            </button>
          ))}
        </div>
      </div>
      <ErrorBanner error={stats.error} />
      {stats.data && (
        <>
          <StatTiles summary={stats.data.summary} />
          <LatencyCharts stats={stats.data} />
        </>
      )}

      <h2>Recent checks</h2>
      <ErrorBanner error={checks.error} />
      {checks.data && <RecentChecks checks={checks.data.items} />}

      <h2>Recent incidents</h2>
      <ErrorBanner error={incidents.error} />
      {incidents.data && <IncidentTable incidents={incidents.data.items} showMonitor={false} />}
    </section>
  );
}
