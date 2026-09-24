import { describe, expect, it } from 'vitest';
import { duration, relativeTime } from './format';

describe('duration', () => {
  it.each([
    [0, '0s'],
    [45, '45s'],
    [90, '1m 30s'],
    [3725, '1h 2m 5s'],
    [90061, '1d 1h 1m'],
  ])('%i seconds -> %s', (seconds, expected) => {
    expect(duration(seconds)).toBe(expected);
  });
});

describe('relativeTime', () => {
  const now = Date.parse('2030-01-01T12:00:00Z');
  it('formats past and future times', () => {
    expect(relativeTime(null, now)).toBe('never');
    expect(relativeTime('2030-01-01T11:59:30Z', now)).toBe('30 s ago');
    expect(relativeTime('2030-01-01T11:55:00Z', now)).toBe('5 min ago');
    expect(relativeTime('2030-01-01T12:00:20Z', now)).toBe('in 20 s');
  });
});
