import { NavLink, Outlet } from 'react-router';

export function Layout() {
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
        </nav>
      </header>
      <main className="app-main">
        <Outlet />
      </main>
    </div>
  );
}
