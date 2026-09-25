import { useState, type FormEvent } from 'react';
import { changePassword } from '../api/auth';
import { ApiError } from '../api/client';
import { useAuth } from '../auth/AuthContext';

export function AccountPage() {
  const { user } = useAuth();
  const [current, setCurrent] = useState('');
  const [next, setNext] = useState('');
  const [message, setMessage] = useState<{ ok: boolean; text: string } | null>(null);

  async function handleSubmit(event: FormEvent) {
    event.preventDefault();
    setMessage(null);
    try {
      await changePassword(current, next);
      setCurrent('');
      setNext('');
      setMessage({ ok: true, text: 'Password changed.' });
    } catch (e) {
      const text = e instanceof ApiError
        ? e.fieldErrors[0]?.message ?? (e.status === 401 ? 'Current password is incorrect' : e.message)
        : 'Could not change password';
      setMessage({ ok: false, text });
    }
  }

  return (
    <section>
      <h1>Account</h1>
      <p className="summary">Signed in as {user.email} ({user.role.toLowerCase()})</p>
      <form className="monitor-form" onSubmit={handleSubmit} aria-label="Change password">
        <h2>Change password</h2>
        {message && (
          <div className={message.ok ? 'success-banner' : 'error-banner'} role="alert">{message.text}</div>
        )}
        <div className="field">
          <label htmlFor="pw-current">Current password</label>
          <input id="pw-current" type="password" autoComplete="current-password" value={current}
            onChange={(e) => setCurrent(e.target.value)} />
        </div>
        <div className="field">
          <label htmlFor="pw-new">New password</label>
          <input id="pw-new" type="password" autoComplete="new-password" value={next}
            onChange={(e) => setNext(e.target.value)} />
          <small className="hint">12–64 characters. A long passphrase is best.</small>
        </div>
        <button type="submit" className="primary" disabled={!current || !next}>Change password</button>
      </form>
    </section>
  );
}
