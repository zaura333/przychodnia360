import { useState } from 'react';
import { PERFORMER_LABELS } from '../labels';
import type { CategoryResponse } from '@/shared/api/generated/model';
import { env } from '@/shared/config/env';
import { Button } from '@/shared/ui/button';
import { Input, Select } from '@/shared/ui/fields';
import { FormField } from '@/shared/ui/form';

export interface ServiceFilterValues {
  categoryId: string;
  minPrice: string;
  maxPrice: string;
  performerType: string;
}

interface ServiceFiltersProps {
  categories: readonly CategoryResponse[];
  value: ServiceFilterValues;
  onApply: (value: ServiceFilterValues) => void;
}

const EMPTY: ServiceFilterValues = {
  categoryId: '',
  minPrice: '',
  maxPrice: '',
  performerType: '',
};

/** Filtry listy usług; wartości trafiają do adresu dopiero po "Filtruj", co oszczędza zapytania przy pisaniu. */
export function ServiceFilters({ categories, value, onApply }: ServiceFiltersProps) {
  const [draft, setDraft] = useState(value);
  const set = (key: keyof ServiceFilterValues) => (e: { target: { value: string } }) => {
    setDraft((d) => ({ ...d, [key]: e.target.value }));
  };
  return (
    <form
      aria-label="Filtry usług"
      className="mb-4 grid gap-3 sm:grid-cols-2 lg:grid-cols-4"
      onSubmit={(e) => {
        e.preventDefault();
        onApply(draft);
      }}
    >
      <FormField label="Kategoria">
        <Select value={draft.categoryId} onChange={set('categoryId')}>
          <option value="">Wszystkie</option>
          {categories.map((c) => (
            <option key={c.id} value={c.id}>
              {c.name}
            </option>
          ))}
        </Select>
      </FormField>
      <FormField label="Cena od (zł)">
        <Input
          type="number"
          min={0}
          step="0.01"
          value={draft.minPrice}
          onChange={set('minPrice')}
        />
      </FormField>
      <FormField label="Cena do (zł)">
        <Input
          type="number"
          min={0}
          step="0.01"
          value={draft.maxPrice}
          onChange={set('maxPrice')}
        />
      </FormField>
      {env.featureMedicalStaff ? (
        <FormField label="Wykonawca">
          <Select value={draft.performerType} onChange={set('performerType')}>
            <option value="">Wszyscy</option>
            {Object.entries(PERFORMER_LABELS).map(([type, label]) => (
              <option key={type} value={type}>
                {label}
              </option>
            ))}
          </Select>
        </FormField>
      ) : null}
      <div className="flex items-end gap-2">
        <Button type="submit">Filtruj</Button>
        <Button
          variant="outline"
          onClick={() => {
            setDraft(EMPTY);
            onApply(EMPTY);
          }}
        >
          Wyczyść
        </Button>
      </div>
    </form>
  );
}
