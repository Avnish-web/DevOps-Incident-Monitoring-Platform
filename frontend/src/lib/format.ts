/** "3 min ago", "in 20 s", or "never". */
export function relativeTime(iso: string | null, now: number = Date.now()): string {
  if (!iso) return 'never';
  const diffSeconds = Math.round((new Date(iso).getTime() - now) / 1000);
  const abs = Math.abs(diffSeconds);
  const text = abs < 60 ? `${abs} s` : abs < 3600 ? `${Math.round(abs / 60)} min` : abs < 86400
    ? `${Math.round(abs / 3600)} h` : `${Math.round(abs / 86400)} d`;
  return diffSeconds <= 0 ? `${text} ago` : `in ${text}`;
}

/** 45 → "45s", 3725 → "1h 2m 5s". */
export function duration(totalSeconds: number): string {
  const s = Math.max(0, Math.floor(totalSeconds));
  const days = Math.floor(s / 86400);
  const hours = Math.floor((s % 86400) / 3600);
  const minutes = Math.floor((s % 3600) / 60);
  const seconds = s % 60;
  const parts = [
    days ? `${days}d` : '',
    hours ? `${hours}h` : '',
    minutes ? `${minutes}m` : '',
    seconds || s === 0 ? `${seconds}s` : '',
  ].filter(Boolean);
  return parts.slice(0, 3).join(' ');
}

export function dateTime(iso: string | null): string {
  return iso ? new Date(iso).toLocaleString() : '—';
}
