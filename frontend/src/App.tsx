import { lazy, Suspense } from 'react';
import { BrowserRouter, Route, Routes } from 'react-router';
import { QueryCache, QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { ApiError } from './api/client';
import { AuthGate, ME_QUERY_KEY } from './auth/AuthContext';
import { Layout } from './components/Layout';
import { AccountPage } from './pages/AccountPage';
import { AlertChannelsPage } from './pages/AlertChannelsPage';
import { IncidentsPage } from './pages/IncidentsPage';
import { MonitorEditPage } from './pages/MonitorEditPage';
import { MonitorsPage } from './pages/MonitorsPage';
import { UsersPage } from './pages/UsersPage';

// The detail page carries the charting library; load it only when a monitor is opened.
const MonitorDetailPage = lazy(() =>
  import('./pages/MonitorDetailPage').then((m) => ({ default: m.MonitorDetailPage })),
);

export function createQueryClient() {
  const client: QueryClient = new QueryClient({
    // An expired session anywhere sends the user back to the login page.
    queryCache: new QueryCache({
      onError: (error, query) => {
        if (error instanceof ApiError && error.status === 401 && query.queryKey !== ME_QUERY_KEY) {
          void client.invalidateQueries({ queryKey: ME_QUERY_KEY });
        }
      },
    }),
    defaultOptions: {
      queries: {
        // Client errors (404, 400, ...) will not fix themselves; only retry server/network errors.
        retry: (failureCount, error) =>
          failureCount < 2 && !(error instanceof ApiError && error.status < 500),
        staleTime: 5_000,
      },
    },
  });
  return client;
}

const queryClient = createQueryClient();

export function App() {
  return (
    <QueryClientProvider client={queryClient}>
      <AuthGate>
      <BrowserRouter>
        <Routes>
          <Route element={<Layout />}>
            <Route index element={<MonitorsPage />} />
            <Route path="monitors/new" element={<MonitorEditPage />} />
            <Route
              path="monitors/:id"
              element={
                <Suspense fallback={<p>Loading…</p>}>
                  <MonitorDetailPage />
                </Suspense>
              }
            />
            <Route path="monitors/:id/edit" element={<MonitorEditPage />} />
            <Route path="incidents" element={<IncidentsPage />} />
            <Route path="alerts" element={<AlertChannelsPage />} />
            <Route path="account" element={<AccountPage />} />
            <Route path="users" element={<UsersPage />} />
            <Route path="*" element={<p>Page not found.</p>} />
          </Route>
        </Routes>
      </BrowserRouter>
      </AuthGate>
    </QueryClientProvider>
  );
}
