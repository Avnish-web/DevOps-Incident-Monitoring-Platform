import { useState, type FormEvent } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { createUser, deleteUser, listUsers, type Role } from '../api/auth';
import { ApiError } from '../api/client';
import { useAuth } from '../auth/AuthContext';
import { ErrorBanner } from '../components/ErrorBanner';
import { dateTime } from '../lib/format';

export function UsersPage() {
  const { user: me } = useAuth();
  const queryClient = useQueryClient();
  const users = useQuery({ queryKey: ['users'], queryFn: listUsers });
  const remove = useMutation({
    mutationFn: deleteUser,
    onSettled: () => queryClient.invalidateQueries({ queryKey: ['users'] }),
  });
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [role, setRole] = useState<Role>('USER');
  const [formError, setFormError] = useState<string | null>(null);

  async function handleSubmit(event: FormEvent) {
    event.preventDefault();
    setFormError(null);
    try {
      await createUser(email.trim(), password, role);
      setEmail('');
      setPassword('');
      await queryClient.invalidateQueries({ queryKey: ['users'] });
    } catch (e) {
      setFormError(e instanceof ApiError ? e.fieldErrors[0]?.message ?? e.message : 'Could not create user');
    }
  }

  return (
    <section>
      <h1>Users</h1>
      <p className="summary">Each user sees only their own monitors, incidents and alert channels.</p>
      <form className="monitor-form" onSubmit={handleSubmit} aria-label="Add user">
        {formError && <div className="error-banner" role="alert">{formError}</div>}
        <div className="field-row">
          <div className="field">
            <label htmlFor="user-email">E-mail</label>
            <input id="user-email" type="email" autoComplete="off" value={email} onChange={(e) => setEmail(e.target.value)} />
          </div>
          <div className="field">
            <label htmlFor="user-password">Initial password</label>
            <input id="user-password" type="password" autoComplete="new-password" value={password}
              onChange={(e) => setPassword(e.target.value)} />
          </div>
          <div className="field">
            <label htmlFor="user-role">Role</label>
            <select id="user-role" value={role} onChange={(e) => setRole(e.target.value as Role)}>
              <option value="USER">User</option>
              <option value="ADMIN">Administrator</option>
            </select>
          </div>
        </div>
        <button type="submit" className="primary" disabled={!email || !password}>Add user</button>
      </form>

      <ErrorBanner error={users.error ?? remove.error} />
      {users.data && (
        <table className="table">
          <thead><tr><th>E-mail</th><th>Role</th><th>Created</th><th>Last login</th><th aria-label="Actions" /></tr></thead>
          <tbody>
            {users.data.map((u) => (
              <tr key={u.id}>
                <td>{u.email}</td>
                <td>{u.role === 'ADMIN' ? 'Administrator' : 'User'}</td>
                <td>{dateTime(u.createdAt)}</td>
                <td>{dateTime(u.lastLoginAt)}</td>
                <td className="row-actions">
                  {u.id !== me.id && (
                    <button type="button" className="danger" disabled={remove.isPending}
                      onClick={() => {
                        if (window.confirm(`Delete ${u.email}? Their monitors and channels are deleted too.`)) remove.mutate(u.id);
                      }}>
                      Delete
                    </button>
                  )}
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      )}
    </section>
  );
}
