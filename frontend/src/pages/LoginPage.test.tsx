import { describe, expect, it, vi } from 'vitest';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { LoginPage } from './LoginPage';

describe('LoginPage', () => {
  it('shows the server message and clears the password on failure', async () => {
    document.cookie = 'XSRF-TOKEN=t; path=/';
    vi.spyOn(globalThis, 'fetch').mockResolvedValue(new Response(
      JSON.stringify({ status: 401, detail: 'Invalid e-mail or password' }),
      { status: 401, headers: { 'Content-Type': 'application/problem+json' } },
    ));
    const onLoggedIn = vi.fn();
    render(<LoginPage onLoggedIn={onLoggedIn} />);

    await userEvent.type(screen.getByLabelText('E-mail'), 'a@example.com');
    await userEvent.type(screen.getByLabelText('Password'), 'wrong-password');
    await userEvent.click(screen.getByRole('button', { name: 'Sign in' }));

    expect(await screen.findByRole('alert')).toHaveTextContent('Invalid e-mail or password');
    expect(screen.getByLabelText('Password')).toHaveValue('');
    expect(onLoggedIn).not.toHaveBeenCalled();
  });

  it('reports the signed-in user', async () => {
    document.cookie = 'XSRF-TOKEN=t; path=/';
    vi.spyOn(globalThis, 'fetch').mockImplementation(async (input) =>
      String(input).endsWith('/auth/login')
        ? new Response(JSON.stringify({ id: 'u1', email: 'a@example.com', role: 'USER' }),
          { status: 200, headers: { 'Content-Type': 'application/json' } })
        : new Response(null, { status: 204 }));
    const onLoggedIn = vi.fn();
    render(<LoginPage onLoggedIn={onLoggedIn} />);

    await userEvent.type(screen.getByLabelText('E-mail'), 'a@example.com');
    await userEvent.type(screen.getByLabelText('Password'), 'correct-password');
    await userEvent.click(screen.getByRole('button', { name: 'Sign in' }));

    await vi.waitFor(() => expect(onLoggedIn).toHaveBeenCalledWith({ id: 'u1', email: 'a@example.com', role: 'USER' }));
  });
});
