import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it, vi } from 'vitest';
import { ApiError } from '@/shared/api/apiError';
import { ErrorBoundary } from '@/shared/ui/error-boundary';
import { QueryState } from '@/shared/ui/query-state';

const base = { isPending: false, isError: false, error: null, data: undefined, refetch: vi.fn() };

describe('QueryState', () => {
  it('shows a skeleton while loading', () => {
    render(<QueryState query={{ ...base, isPending: true }}>{() => <p>treść</p>}</QueryState>);
    expect(screen.getByRole('status', { name: 'Ładowanie' })).toBeInTheDocument();
    expect(screen.queryByText('treść')).not.toBeInTheDocument();
  });

  it('shows the empty state for an empty list', () => {
    render(
      <QueryState query={{ ...base, data: [] }} emptyTitle="Brak wizyt">
        {() => <p>treść</p>}
      </QueryState>,
    );
    expect(screen.getByText('Brak wizyt')).toBeInTheDocument();
  });

  it('shows a described error with retry and request id, never the server detail', async () => {
    const refetch = vi.fn();
    const error = new ApiError({
      status: 500,
      detail: 'ERROR: relation "patients" does not exist',
      requestId: 'req-9999',
    });
    render(
      <QueryState query={{ ...base, isError: true, error, refetch }}>{() => null}</QueryState>,
    );
    expect(screen.getByRole('alert')).toHaveTextContent('Błąd serwera');
    expect(screen.getByRole('alert')).toHaveTextContent('req-9999');
    expect(screen.getByRole('alert')).not.toHaveTextContent('relation');
    await userEvent.click(screen.getByRole('button', { name: 'Spróbuj ponownie' }));
    expect(refetch).toHaveBeenCalledOnce();
  });

  it('renders children with data and an optional counter', () => {
    render(
      <QueryState query={{ ...base, data: ['a', 'b'] }} count={(d) => d.length}>
        {(items) => (
          <ul>
            {items.map((i) => (
              <li key={i}>{i}</li>
            ))}
          </ul>
        )}
      </QueryState>,
    );
    expect(screen.getByText('Liczba wyników: 2')).toBeInTheDocument();
    expect(screen.getAllByRole('listitem')).toHaveLength(2);
  });
});

describe('ErrorBoundary', () => {
  it('shows a Polish message with an id and hides the error text', () => {
    function Boom(): never {
      throw new Error('tajny komunikat z danymi');
    }
    const spy = vi.spyOn(console, 'error').mockImplementation(() => undefined);
    render(
      <ErrorBoundary>
        <Boom />
      </ErrorBoundary>,
    );
    spy.mockRestore();
    expect(screen.getByRole('alert')).toHaveTextContent('Coś poszło nie tak');
    expect(screen.getByRole('alert')).toHaveTextContent(/Identyfikator: [0-9a-f]{8}/);
    expect(screen.queryByText(/tajny komunikat/)).not.toBeInTheDocument();
  });
});
