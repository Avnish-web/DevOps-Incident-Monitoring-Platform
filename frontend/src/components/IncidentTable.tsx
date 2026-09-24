import { Link } from 'react-router';
import type { Incident, IncidentResolution } from '../api/types';
import { dateTime, duration } from '../lib/format';

const RESOLUTION_LABELS: Record<IncidentResolution, string> = {
  RECOVERED: 'Recovered',
  MONITOR_PAUSED: 'Monitor paused',
  MONITOR_CHANGED: 'Monitor changed',
};

export function IncidentTable({ incidents, showMonitor = true }: { incidents: Incident[]; showMonitor?: boolean }) {
  if (incidents.length === 0) return <p className="empty">No incidents.</p>;
  return (
    <table className="table">
      <thead>
        <tr>
          <th>Status</th>
          {showMonitor && <th>Monitor</th>}
          <th>Started</th>
          <th>Duration</th>
          <th>Cause</th>
          <th>Resolution</th>
        </tr>
      </thead>
      <tbody>
        {incidents.map((i) => (
          <tr key={i.id}>
            <td>
              <span className={`badge ${i.status === 'OPEN' ? 'badge-down' : 'badge-resolved'}`}>
                {i.status === 'OPEN' ? 'Open' : 'Resolved'}
              </span>
            </td>
            {showMonitor && (
              <td>
                <Link to={`/monitors/${i.monitorId}`}>{i.monitorName ?? 'deleted monitor'}</Link>
              </td>
            )}
            <td>{dateTime(i.startedAt)}</td>
            <td>{duration(i.durationSeconds)}</td>
            <td className="cause">{i.cause ?? '—'}</td>
            <td>{i.resolution ? RESOLUTION_LABELS[i.resolution] : '—'}</td>
          </tr>
        ))}
      </tbody>
    </table>
  );
}
