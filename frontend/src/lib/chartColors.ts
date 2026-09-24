import { useSyncExternalStore } from 'react';

/**
 * Chart colors, validated with the dataviz palette validator for both surfaces
 * (categorical slots 1-2 of the reference palette; status "critical" for failures).
 * SVG presentation attributes cannot use CSS variables reliably, so the mode is resolved here.
 */
export interface ChartColors {
  avg: string;
  p95: string;
  failure: string;
  grid: string;
  axis: string;
  text: string;
  surface: string;
}

const LIGHT: ChartColors = {
  avg: '#2a78d6',
  p95: '#eb6834',
  failure: '#d03b3b',
  grid: '#e8ebef',
  axis: '#5f6b7a',
  text: '#1c2330',
  surface: '#ffffff',
};

const DARK: ChartColors = {
  avg: '#3987e5',
  p95: '#d95926',
  failure: '#d03b3b',
  grid: '#2c3542',
  axis: '#9aa5b3',
  text: '#e6e9ee',
  surface: '#1a2029',
};

const QUERY = '(prefers-color-scheme: dark)';

function subscribe(onChange: () => void) {
  const media = window.matchMedia?.(QUERY);
  media?.addEventListener('change', onChange);
  return () => media?.removeEventListener('change', onChange);
}

export function useChartColors(): ChartColors {
  const dark = useSyncExternalStore(subscribe, () => window.matchMedia?.(QUERY).matches ?? false, () => false);
  return dark ? DARK : LIGHT;
}
