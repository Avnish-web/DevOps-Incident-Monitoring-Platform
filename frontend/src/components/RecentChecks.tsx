import type { CheckResult } from '../api/history';
import { dateTime } from '../lib/format';

/** Raw results as a table: the accessible, exact counterpart to the charts. */
export function RecentChecks({ checks }: { checks: CheckResult[] }) {
  if (checks.length === 0) return <p className="empty">No checks recorded yet.</p>;
  return (
    <table className="table">
      <thead>
        <tr>
          <th>Time</th>
          <th>Result</th>
          <th>Status</th>
          <th>Response time</th>
          <th>Error</th>
        </tr>
      </thead>
      <tbody>
        {checks.map((c) => (
          <tr key={`${c.checkedAt}-${c.errorType ?? 'ok'}`}>
            <td>{dateTime(c.checkedAt)}</td>
            <td>
              <span className={`badge ${c.success ? 'badge-up' : 'badge-down'}`}>{c.success ? 'OK' : 'Failed'}</span>
            </td>
            <td>{c.statusCode ?? '—'}</td>
            <td>{c.latencyMs == null ? '—' : `${c.latencyMs} ms`}</td>
            <td className="cause">{c.errorType ? `${c.errorType}: ${c.errorMessage ?? ''}` : ''}</td>
          </tr>
        ))}
      </tbody>
    </table>
  );
}
