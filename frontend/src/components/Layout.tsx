import { NavLink, Outlet } from 'react-router';
import { useAuth } from '../auth/AuthContext';

export function Layout() {
  const { user, logout } = useAuth();
  return (
    <div className="app">
      <header className="app-header">
        <span className="brand">Monitoring Platform</span>
        <nav>
          <NavLink to="/" end>
            Monitors
          </NavLink>
          <NavLink to="/incidents">Incidents</NavLink>
          <NavLink to="/alerts">Alerts</NavLink>
          {user.role === 'ADMIN' && <NavLink to="/users">Users</NavLink>}
        </nav>
        <div className="user-menu">
          <NavLink to="/account">{user.email}</NavLink>
          <button type="button" onClick={() => void logout()}>Sign out</button>
        </div>
      </header>
      <main className="app-main">
        <Outlet />
      </main>
    </div>
  );
}
