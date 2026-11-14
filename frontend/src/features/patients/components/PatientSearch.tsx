import { useState, type ReactNode } from 'react';
import { useSearchPatients } from '../hooks';
import { MIN_SEARCH_LENGTH } from '../schemas';
import type { PatientResponse } from '@/shared/api/generated/model';
import { formatLocalDate, parseLocalDate } from '@/shared/lib/datetime';
import { Input } from '@/shared/ui/fields';
import { FormField } from '@/shared/ui/form';
import { QueryState } from '@/shared/ui/query-state';

interface PatientSearchProps {
  /** Akcje przy wyniku (np. "Wybierz", "Edytuj kontakt"). */
  renderActions: (patient: PatientResponse) => ReactNode;
}

/** Wyszukiwarka pacjentów po imieniu i nazwisku (min. 2 znaki, opóźnienie 300 ms), bez danych medycznych. */
export function PatientSearch({ renderActions }: PatientSearchProps) {
  const [text, setText] = useState('');
  const search = useSearchPatients(text);
  return (
    <div className="flex flex-col gap-3">
      <FormField
        label="Szukaj pacjenta"
        hint={`Wpisz co najmniej ${String(MIN_SEARCH_LENGTH)} znaki imienia lub nazwiska.`}
      >
        <Input
          type="search"
          autoComplete="off"
          value={text}
          onChange={(e) => {
            setText(e.target.value);
          }}
        />
      </FormField>
      {search.enabled ? (
        <QueryState
          query={search}
          isEmpty={(d) => d.items.length === 0}
          emptyTitle="Nie znaleziono pacjentów"
          emptyHint="Sprawdź pisownię albo dodaj nowego pacjenta."
        >
          {(data) => (
            <ul aria-label="Wyniki wyszukiwania" className="flex flex-col gap-2">
              {data.items.map((p) => (
                <li
                  key={p.id}
                  className="flex flex-wrap items-center justify-between gap-2 rounded-md border p-3"
                >
                  <div>
                    <p className="font-medium">
                      {p.firstName} {p.lastName}
                    </p>
                    <p className="text-sm text-muted-foreground">
                      ur. {formatLocalDate(parseLocalDate(p.birthDate))}
                      {p.contactEmail ? ` · ${p.contactEmail}` : ''}
                    </p>
                  </div>
                  <div className="flex gap-2">{renderActions(p)}</div>
                </li>
              ))}
            </ul>
          )}
        </QueryState>
      ) : null}
    </div>
  );
}
