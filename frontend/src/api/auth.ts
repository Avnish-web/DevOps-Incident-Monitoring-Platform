import { refreshCsrfToken, request } from './client';

export type Role = 'ADMIN' | 'USER';

export interface CurrentUser {
  id: string;
  email: string;
  role: Role;
}

export interface UserSummary {
  id: string;
  email: string;
  role: Role;
  enabled: boolean;
  createdAt: string;
  lastLoginAt: string | null;
}

export async function getMe(): Promise<CurrentUser> {
  return (await request<CurrentUser>('/api/v1/auth/me')).data;
}

export async function login(email: string, password: string): Promise<CurrentUser> {
  const user = (await request<CurrentUser>('/api/v1/auth/login', { method: 'POST', body: { email, password } })).data;
  await refreshCsrfToken(); // the CSRF token is rotated at login
  return user;
}

export async function logout(): Promise<void> {
  await request<void>('/api/v1/auth/logout', { method: 'POST' });
}

export async function changePassword(currentPassword: string, newPassword: string): Promise<void> {
  await request<void>('/api/v1/auth/password', { method: 'POST', body: { currentPassword, newPassword } });
}

export async function listUsers(): Promise<UserSummary[]> {
  return (await request<UserSummary[]>('/api/v1/users')).data;
}

export async function createUser(email: string, password: string, role: Role): Promise<UserSummary> {
  return (await request<UserSummary>('/api/v1/users', { method: 'POST', body: { email, password, role } })).data;
}

export async function deleteUser(id: string): Promise<void> {
  await request<void>(`/api/v1/users/${encodeURIComponent(id)}`, { method: 'DELETE' });
}
