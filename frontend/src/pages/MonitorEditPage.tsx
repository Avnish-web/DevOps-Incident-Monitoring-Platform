import { useNavigate, useParams } from 'react-router';
import { useQuery, useQueryClient } from '@tanstack/react-query';
import { createMonitor, getMonitor, updateMonitor } from '../api/monitors';
import { ErrorBanner } from '../components/ErrorBanner';
import { MonitorForm } from '../components/MonitorForm';
import { EMPTY_FORM, fromMonitor } from '../lib/validation';

/** Create (no :id) or edit (with :id) a monitor. */
export function MonitorEditPage() {
  const { id } = useParams();
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const existing = useQuery({
    queryKey: ['monitor', id],
    queryFn: () => getMonitor(id!),
    enabled: Boolean(id),
  });

  if (id && existing.isPending) return <p>Loading…</p>;
  if (id && existing.error) return <ErrorBanner error={existing.error} />;

  const monitor = existing.data;
  return (
    <section>
      <h1>{monitor ? `Edit ${monitor.name}` : 'Add monitor'}</h1>
      <MonitorForm
        key={monitor ? `${monitor.id}:${monitor.version}` : 'new'}
        initial={monitor ? fromMonitor(monitor) : EMPTY_FORM}
        submitLabel={monitor ? 'Save changes' : 'Create monitor'}
        onCancel={() => navigate(-1)}
        onSubmit={async (request) => {
          const saved = monitor
            ? await updateMonitor(monitor.id, monitor.version, request)
            : await createMonitor(request);
          await queryClient.invalidateQueries({ queryKey: ['monitors'] });
          queryClient.setQueryData(['monitor', saved.id], saved);
          navigate(`/monitors/${saved.id}`);
        }}
      />
    </section>
  );
}
