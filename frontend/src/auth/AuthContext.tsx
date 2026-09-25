import { createContext, useContext, type ReactNode } from 'react';
import { useQuery, useQueryClient } from '@tanstack/react-query';
import { getMe, logout as apiLogout, type CurrentUser } from '../api/auth';
import { ApiError } from '../api/client';
import { LoginPage } from '../pages/LoginPage';

interface AuthState {
  user: CurrentUser;
  logout: () => Promise<void>;
}

const AuthContext = createContext<AuthState | null>(null);

export const ME_QUERY_KEY = ['auth', 'me'] as const;

/** Renders the login page until the session is valid, then provides the current user. */
export function AuthGate({ children }: { children: ReactNode }) {
  const queryClient = useQueryClient();
  const me = useQuery({
    queryKey: ME_QUERY_KEY,
    queryFn: getMe,
    retry: false,
    staleTime: Infinity,
  });

  if (me.isPending) return <p className="app-main">Loading…</p>;
  if (me.error) {
    if (me.error instanceof ApiError && me.error.status === 401) {
      return <LoginPage onLoggedIn={(user) => queryClient.setQueryData(ME_QUERY_KEY, user)} />;
    }
    return <p className="app-main error-banner" role="alert">Cannot reach the API. Try again later.</p>;
  }

  const logout = async () => {
    try {
      await apiLogout();
    } finally {
      queryClient.clear();
      queryClient.setQueryData(ME_QUERY_KEY, undefined);
      await queryClient.invalidateQueries({ queryKey: ME_QUERY_KEY });
    }
  };

  return <AuthContext.Provider value={{ user: me.data, logout }}>{children}</AuthContext.Provider>;
}

export function useAuth(): AuthState {
  const state = useContext(AuthContext);
  if (!state) throw new Error('useAuth must be used inside <AuthGate>');
  return state;
}
