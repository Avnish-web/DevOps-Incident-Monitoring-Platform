import { useState } from 'react';
import { Link } from 'react-router';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { deleteMonitor, listMonitors, toRequest, updateMonitor } from '../api/monitors';
import type { Monitor } from '../api/types';
import { ErrorBanner } from '../components/ErrorBanner';
import { Pagination } from '../components/Pagination';
import { StatusBadge } from '../components/StatusBadge';
import { relativeTime } from '../lib/format';

export function MonitorsPage() {
  const [page, setPage] = useState(0);
  const queryClient = useQueryClient();
  const monitors = useQuery({
    queryKey: ['monitors', page],
    queryFn: () => listMonitors(page),
    refetchInterval: 15_000,
  });

  const invalidate = () => queryClient.invalidateQueries({ queryKey: ['monitors'] });
  const toggle = useMutation({
    mutationFn: (m: Monitor) => updateMonitor(m.id, m.version, toRequest(m, { enabled: !m.enabled })),
    onSettled: invalidate,
  });
  const remove = useMutation({ mutationFn: (m: Monitor) => deleteMonitor(m.id), onSettled: invalidate });

  const items = monitors.data?.items ?? [];
  const down = items.filter((m) => m.enabled && m.status === 'DOWN').length;

  return (
    <section>
      <div className="page-header">
        <h1>Monitors</h1>
        <Link className="button primary" to="/monitors/new">
          Add monitor
        </Link>
      </div>

      {monitors.data && (
        <p className="summary">
          {monitors.data.totalElements} monitor(s)
          {down > 0 && <strong className="summary-down"> · {down} down</strong>}
        </p>
      )}
      <ErrorBanner error={monitors.error ?? toggle.error ?? remove.error} />
      {monitors.isPending && <p>Loading…</p>}

      {monitors.data && items.length === 0 && (
        <p className="empty">No monitors yet. Add one to start checking a website or API.</p>
      )}

      {items.length > 0 && (
        <table className="table">
          <thead>
            <tr>
              <th>Status</th>
              <th>Name</th>
              <th>URL</th>
              <th>Interval</th>
              <th>Last check</th>
              <th aria-label="Actions" />
            </tr>
          </thead>
          <tbody>
            {items.map((m) => (
              <tr key={m.id}>
                <td>
                  <StatusBadge status={m.status} enabled={m.enabled} />
                </td>
                <td>
                  <Link to={`/monitors/${m.id}`}>{m.name}</Link>
                </td>
                <td className="url" title={m.url}>
                  {m.url}
                </td>
                <td>{m.intervalSeconds}s</td>
                <td>{relativeTime(m.lastCheckedAt)}</td>
                <td className="row-actions">
                  <Link to={`/monitors/${m.id}/edit`}>Edit</Link>
                  <button type="button" onClick={() => toggle.mutate(m)} disabled={toggle.isPending}>
                    {m.enabled ? 'Pause' : 'Resume'}
                  </button>
                  <button
                    type="button"
                    className="danger"
                    disabled={remove.isPending}
                    onClick={() => {
                      if (window.confirm(`Delete "${m.name}" and its history?`)) remove.mutate(m);
                    }}
                  >
                    Delete
                  </button>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      )}
      {monitors.data && (
        <Pagination page={page} totalPages={monitors.data.totalPages} onChange={setPage} />
      )}
    </section>
  );
}
