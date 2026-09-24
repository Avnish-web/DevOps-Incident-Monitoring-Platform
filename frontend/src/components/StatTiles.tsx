import type { MonitorStats } from '../api/history';

function ms(value: number | null) {
  return value == null ? '—' : `${value} ms`;
}

/** Headline numbers for the selected range. A tile, not a chart: each is a single value. */
export function StatTiles({ summary }: { summary: MonitorStats['summary'] }) {
  const uptime = summary.uptimePercent == null ? '—' : `${summary.uptimePercent.toFixed(2)}%`;
  return (
    <div className="stat-tiles">
      <div className="stat-tile">
        <span className="stat-label">Uptime</span>
        <span className="stat-value">{uptime}</span>
        <span className="stat-sub">{summary.totalChecks} checks</span>
      </div>
      <div className="stat-tile">
        <span className="stat-label">Avg response</span>
        <span className="stat-value">{ms(summary.avgLatencyMs)}</span>
        <span className="stat-sub">median {ms(summary.p50LatencyMs)}</span>
      </div>
      <div className="stat-tile">
        <span className="stat-label">p95 response</span>
        <span className="stat-value">{ms(summary.p95LatencyMs)}</span>
        <span className="stat-sub">max {ms(summary.maxLatencyMs)}</span>
      </div>
      <div className="stat-tile">
        <span className="stat-label">Failed checks</span>
        <span className="stat-value">{summary.failedChecks}</span>
        <span className="stat-sub">of {summary.totalChecks}</span>
      </div>
    </div>
  );
}
