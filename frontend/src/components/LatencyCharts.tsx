import {
  Bar,
  BarChart,
  CartesianGrid,
  Legend,
  Line,
  LineChart,
  ResponsiveContainer,
  Tooltip,
  XAxis,
  YAxis,
} from 'recharts';
import type { MonitorStats, StatsRange } from '../api/history';
import { useChartColors } from '../lib/chartColors';

interface Point {
  t: number;
  avg: number | null;
  p95: number | null;
  checks: number;
  failures: number;
}

function tickFormatter(range: StatsRange) {
  return (t: number) => {
    const d = new Date(t);
    return range === '1h' || range === '24h'
      ? d.toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })
      : d.toLocaleDateString([], { month: 'short', day: 'numeric' });
  };
}

/**
 * Response time (avg and p95 share one axis: same unit) and failed checks (separate chart,
 * never a second y-axis). Buckets without checks are gaps, not zeros.
 */
export function LatencyCharts({ stats }: { stats: MonitorStats }) {
  const colors = useChartColors();
  const data: Point[] = stats.series.map((b) => ({
    t: new Date(b.bucketStart).getTime(),
    avg: b.avgLatencyMs,
    p95: b.p95LatencyMs,
    checks: b.checks,
    failures: b.failures,
  }));
  const format = tickFormatter(stats.range);
  const axisProps = {
    stroke: colors.axis,
    tick: { fill: colors.axis, fontSize: 12 },
    tickLine: false,
  } as const;
  const tooltipStyle = {
    background: colors.surface,
    border: `1px solid ${colors.grid}`,
    color: colors.text,
    borderRadius: 6,
  };
  const hasFailures = data.some((d) => d.failures > 0);

  /** Direct label at the last point of a series, in text ink (never the series color). */
  function endLabel(key: 'avg' | 'p95', text: string, dy: number) {
    let last = -1;
    data.forEach((d, i) => {
      if (d[key] != null) last = i;
    });
    function EndLabel(props: { x?: number | string; y?: number | string; index?: number }) {
      if (props.index !== last || props.x == null || props.y == null) return null;
      return (
        <text x={Number(props.x) + 6} y={Number(props.y) + dy} fill={colors.text} fontSize={12}
          dominantBaseline="middle">
          {text}
        </text>
      );
    }
    return EndLabel;
  }

  return (
    <div className="charts">
      <figure className="chart">
        <figcaption>Response time (ms)</figcaption>
        <ResponsiveContainer width="100%" height={260}>
          <LineChart data={data} margin={{ top: 8, right: 64, bottom: 0, left: 0 }}>
            <CartesianGrid vertical={false} stroke={colors.grid} />
            <XAxis dataKey="t" type="number" scale="time" domain={['dataMin', 'dataMax']}
              tickFormatter={format} minTickGap={40} {...axisProps} />
            <YAxis width={56} allowDecimals={false} axisLine={false} {...axisProps} />
            <Tooltip
              contentStyle={tooltipStyle}
              labelFormatter={(t) => new Date(Number(t)).toLocaleString()}
              formatter={(value, name) => [value == null ? '—' : `${value} ms`, name]}
              cursor={{ stroke: colors.axis, strokeDasharray: '3 3' }}
            />
            <Legend verticalAlign="top" align="right" height={28}
              formatter={(value) => <span style={{ color: colors.text }}>{value}</span>} />
            <Line type="monotone" dataKey="avg" name="Average" stroke={colors.avg} strokeWidth={2}
              dot={false} activeDot={{ r: 4, strokeWidth: 2, stroke: colors.surface }}
              connectNulls={false} isAnimationActive={false} label={endLabel('avg', 'Average', 8)} />
            <Line type="monotone" dataKey="p95" name="p95" stroke={colors.p95} strokeWidth={2}
              dot={false} activeDot={{ r: 4, strokeWidth: 2, stroke: colors.surface }}
              connectNulls={false} isAnimationActive={false} label={endLabel('p95', 'p95', -8)} />
          </LineChart>
        </ResponsiveContainer>
      </figure>

      <figure className="chart">
        <figcaption>Failed checks {hasFailures ? '' : '(none in this range)'}</figcaption>
        <ResponsiveContainer width="100%" height={120}>
          <BarChart data={data} margin={{ top: 8, right: 64, bottom: 0, left: 0 }} barCategoryGap={2}>
            <CartesianGrid vertical={false} stroke={colors.grid} />
            <XAxis dataKey="t" type="number" scale="time" domain={['dataMin', 'dataMax']}
              tickFormatter={format} minTickGap={40} {...axisProps} />
            <YAxis width={56} allowDecimals={false} axisLine={false} {...axisProps} />
            <Tooltip
              contentStyle={tooltipStyle}
              labelFormatter={(t) => new Date(Number(t)).toLocaleString()}
              formatter={(value, _name, item) => [`${value} of ${(item.payload as Point).checks}`, 'Failed']}
              cursor={{ fill: colors.grid }}
            />
            <Bar dataKey="failures" name="Failed" fill={colors.failure} radius={[4, 4, 0, 0]}
              isAnimationActive={false} />
          </BarChart>
        </ResponsiveContainer>
      </figure>
    </div>
  );
}
