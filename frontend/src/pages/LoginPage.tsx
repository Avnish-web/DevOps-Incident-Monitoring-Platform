import { useState, type FormEvent } from 'react';
import { login, type CurrentUser } from '../api/auth';
import { ApiError } from '../api/client';

export function LoginPage({ onLoggedIn }: { onLoggedIn: (user: CurrentUser) => void }) {
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);

  async function handleSubmit(event: FormEvent) {
    event.preventDefault();
    setError(null);
    setSubmitting(true);
    try {
      onLoggedIn(await login(email.trim(), password));
    } catch (e) {
      setError(e instanceof ApiError ? e.message : 'Login failed. Try again.');
      setPassword('');
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <main className="login">
      <form className="monitor-form login-form" onSubmit={handleSubmit} aria-label="Sign in">
        <h1>Monitoring Platform</h1>
        {error && <div className="error-banner" role="alert">{error}</div>}
        <div className="field">
          <label htmlFor="login-email">E-mail</label>
          <input id="login-email" type="email" autoComplete="username" required value={email}
            onChange={(e) => setEmail(e.target.value)} />
        </div>
        <div className="field">
          <label htmlFor="login-password">Password</label>
          <input id="login-password" type="password" autoComplete="current-password" required value={password}
            onChange={(e) => setPassword(e.target.value)} />
        </div>
        <button type="submit" className="primary" disabled={submitting || !email || !password}>
          {submitting ? 'Signing in…' : 'Sign in'}
        </button>
      </form>
    </main>
  );
}
