import { describe, expect, it } from 'vitest';
import { render, screen } from '@testing-library/react';
import { StatusBadge } from './StatusBadge';

describe('StatusBadge', () => {
  it.each([
    ['UP', true, 'Up'],
    ['DOWN', true, 'Down'],
    ['UNKNOWN', true, 'Pending'],
    ['DOWN', false, 'Paused'],
  ] as const)('%s (enabled=%s) renders %s', (status, enabled, label) => {
    render(<StatusBadge status={status} enabled={enabled} />);
    expect(screen.getByRole('status')).toHaveTextContent(label);
  });
});
