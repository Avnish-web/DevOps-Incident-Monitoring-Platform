import type { MonitorStatus } from '../api/types';

const LABELS: Record<MonitorStatus | 'PAUSED', string> = {
  UP: 'Up',
  DOWN: 'Down',
  UNKNOWN: 'Pending',
  PAUSED: 'Paused',
};

export function StatusBadge({ status, enabled = true }: { status: MonitorStatus; enabled?: boolean }) {
  const key = enabled ? status : 'PAUSED';
  return (
    <span className={`badge badge-${key.toLowerCase()}`} role="status">
      {LABELS[key]}
    </span>
  );
}
