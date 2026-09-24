import { describe, expect, it } from 'vitest';
import { render, screen } from '@testing-library/react';
import { StatTiles } from './StatTiles';

describe('StatTiles', () => {
  it('shows uptime and latency figures', () => {
    render(<StatTiles summary={{
      totalChecks: 12, failedChecks: 2, uptimePercent: 83.333,
      avgLatencyMs: 550, p50LatencyMs: 550, p95LatencyMs: 955, maxLatencyMs: 1000,
    }} />);

    expect(screen.getByText('83.33%')).toBeInTheDocument();
    expect(screen.getByText('955 ms')).toBeInTheDocument();
    expect(screen.getByText('of 12')).toBeInTheDocument();
  });

  it('shows dashes when there is no data', () => {
    render(<StatTiles summary={{
      totalChecks: 0, failedChecks: 0, uptimePercent: null,
      avgLatencyMs: null, p50LatencyMs: null, p95LatencyMs: null, maxLatencyMs: null,
    }} />);

    expect(screen.getAllByText('—').length).toBeGreaterThanOrEqual(3);
  });
});
