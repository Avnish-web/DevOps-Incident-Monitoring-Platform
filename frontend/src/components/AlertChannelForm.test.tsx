import { describe, expect, it, vi } from 'vitest';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { ApiError } from '../api/client';
import { AlertChannelForm } from './AlertChannelForm';

describe('AlertChannelForm', () => {
  it('requires name and target', async () => {
    const onSubmit = vi.fn();
    render(<AlertChannelForm onSubmit={onSubmit} />);

    await userEvent.click(screen.getByRole('button', { name: 'Add channel' }));

    expect(screen.getByText('Name is required')).toBeInTheDocument();
    expect(screen.getByText('Target is required')).toBeInTheDocument();
    expect(onSubmit).not.toHaveBeenCalled();
  });

  it('adapts the target field to the channel type and submits', async () => {
    const onSubmit = vi.fn().mockResolvedValue(undefined);
    render(<AlertChannelForm onSubmit={onSubmit} />);

    await userEvent.selectOptions(screen.getByLabelText('Type'), 'SLACK');
    expect(screen.getByLabelText('Slack incoming webhook URL')).toBeInTheDocument();
    await userEvent.type(screen.getByLabelText('Name'), 'Ops');
    await userEvent.type(screen.getByLabelText('Slack incoming webhook URL'), 'https://hooks.slack.com/services/T/B/X');
    await userEvent.click(screen.getByRole('button', { name: 'Add channel' }));

    expect(onSubmit).toHaveBeenCalledWith({
      name: 'Ops', type: 'SLACK', target: 'https://hooks.slack.com/services/T/B/X', enabled: true,
    });
  });

  it('shows the server reason next to the target', async () => {
    const onSubmit = vi.fn().mockRejectedValue(new ApiError(400, {
      errors: [{ field: 'target', message: 'Webhook URL must use https' }],
    }));
    render(<AlertChannelForm onSubmit={onSubmit} />);

    await userEvent.selectOptions(screen.getByLabelText('Type'), 'WEBHOOK');
    await userEvent.type(screen.getByLabelText('Name'), 'Hook');
    await userEvent.type(screen.getByLabelText('Webhook URL (https)'), 'http://example.com');
    await userEvent.click(screen.getByRole('button', { name: 'Add channel' }));

    expect(await screen.findByText('Webhook URL must use https')).toBeInTheDocument();
  });
});
