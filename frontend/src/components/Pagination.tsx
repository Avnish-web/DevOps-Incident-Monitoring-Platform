interface Props {
  page: number;
  totalPages: number;
  onChange: (page: number) => void;
}

export function Pagination({ page, totalPages, onChange }: Props) {
  if (totalPages <= 1) return null;
  return (
    <nav className="pagination" aria-label="Pagination">
      <button type="button" disabled={page === 0} onClick={() => onChange(page - 1)}>
        Previous
      </button>
      <span>
        Page {page + 1} of {totalPages}
      </span>
      <button type="button" disabled={page + 1 >= totalPages} onClick={() => onChange(page + 1)}>
        Next
      </button>
    </nav>
  );
}
