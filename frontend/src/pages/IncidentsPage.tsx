import { useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import { listIncidents } from '../api/incidents';
import type { IncidentStatus } from '../api/types';
import { ErrorBanner } from '../components/ErrorBanner';
import { IncidentTable } from '../components/IncidentTable';
import { Pagination } from '../components/Pagination';

const FILTERS: { label: string; value: IncidentStatus | undefined }[] = [
  { label: 'All', value: undefined },
  { label: 'Open', value: 'OPEN' },
  { label: 'Resolved', value: 'RESOLVED' },
];

export function IncidentsPage() {
  const [status, setStatus] = useState<IncidentStatus | undefined>(undefined);
  const [page, setPage] = useState(0);
  const incidents = useQuery({
    queryKey: ['incidents', { status, page }],
    queryFn: () => listIncidents({ status, page }),
    refetchInterval: 15_000,
  });

  return (
    <section>
      <h1>Incidents</h1>
      <div className="tabs" role="tablist">
        {FILTERS.map((f) => (
          <button
            key={f.label}
            type="button"
            role="tab"
            aria-selected={status === f.value}
            className={status === f.value ? 'active' : undefined}
            onClick={() => {
              setStatus(f.value);
              setPage(0);
            }}
          >
            {f.label}
          </button>
        ))}
      </div>
      <ErrorBanner error={incidents.error} />
      {incidents.isPending && <p>Loading…</p>}
      {incidents.data && (
        <>
          <IncidentTable incidents={incidents.data.items} />
          <Pagination page={page} totalPages={incidents.data.totalPages} onChange={setPage} />
        </>
      )}
    </section>
  );
}
