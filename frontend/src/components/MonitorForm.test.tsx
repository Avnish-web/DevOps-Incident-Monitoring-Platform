import { describe, expect, it, vi } from 'vitest';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { ApiError } from '../api/client';
import { EMPTY_FORM } from '../lib/validation';
import { MonitorForm } from './MonitorForm';

function renderForm(onSubmit = vi.fn().mockResolvedValue(undefined)) {
  render(<MonitorForm initial={EMPTY_FORM} submitLabel="Create" onSubmit={onSubmit} onCancel={vi.fn()} />);
  return onSubmit;
}

describe('MonitorForm', () => {
  it('shows client-side errors and does not submit invalid input', async () => {
    const onSubmit = renderForm();

    await userEvent.click(screen.getByRole('button', { name: 'Create' }));

    expect(screen.getByText('Name is required')).toBeInTheDocument();
    expect(screen.getByText('URL is required')).toBeInTheDocument();
    expect(screen.getByLabelText('URL')).toHaveAttribute('aria-invalid', 'true');
    expect(onSubmit).not.toHaveBeenCalled();
  });

  it('submits a typed request', async () => {
    const onSubmit = renderForm();

    await userEvent.type(screen.getByLabelText('Name'), 'Website');
    await userEvent.type(screen.getByLabelText('URL'), 'https://example.com');
    await userEvent.selectOptions(screen.getByLabelText('Method'), 'HEAD');
    await userEvent.click(screen.getByRole('button', { name: 'Create' }));

    expect(onSubmit).toHaveBeenCalledWith(expect.objectContaining({
      name: 'Website',
      url: 'https://example.com',
      httpMethod: 'HEAD',
      intervalSeconds: 60,
      expectedStatus: null,
    }));
  });

  it('shows server field errors next to the field', async () => {
    const onSubmit = vi.fn().mockRejectedValue(new ApiError(400, {
      detail: 'Request validation failed',
      errors: [{ field: 'url', message: 'URL points to a private, loopback or reserved address, which is not allowed' }],
    }));
    renderForm(onSubmit);

    await userEvent.type(screen.getByLabelText('Name'), 'Internal');
    await userEvent.type(screen.getByLabelText('URL'), 'http://169.254.169.254/');
    await userEvent.click(screen.getByRole('button', { name: 'Create' }));

    expect(await screen.findByText(/private, loopback or reserved/)).toBeInTheDocument();
    expect(screen.getByLabelText('URL')).toHaveAttribute('aria-invalid', 'true');
  });

  it('explains a concurrent edit conflict', async () => {
    const onSubmit = vi.fn().mockRejectedValue(new ApiError(412, { detail: 'stale' }));
    renderForm(onSubmit);

    await userEvent.type(screen.getByLabelText('Name'), 'x');
    await userEvent.type(screen.getByLabelText('URL'), 'https://example.com');
    await userEvent.click(screen.getByRole('button', { name: 'Create' }));

    expect(await screen.findByRole('alert')).toHaveTextContent(/changed by someone else/);
  });
});
