import { useQuery } from '@tanstack/react-query';
import type { Ref } from 'react';
import type { SelectHTMLAttributes } from 'react';
import { listFacilities } from '@/shared/api/generated/endpoints/facility-controller/facility-controller';
import { Select } from '@/shared/ui/fields';

const STORAGE_KEY = 'p360.tenantSlug';

/** Ostatnio wybrany kod placówki (jedyna dana w localStorage; nie jest daną osobową). */
export function rememberedTenant(): string {
  try {
    return localStorage.getItem(STORAGE_KEY) ?? '';
  } catch {
    return '';
  }
}

export function rememberTenant(slug: string): void {
  try {
    localStorage.setItem(STORAGE_KEY, slug);
  } catch {
    // pamięć przeglądarki może być zablokowana: wybór po prostu nie zostanie zapamiętany
  }
}

/** Lista wyboru aktywnych placówek z publicznego GET /facilities (do użycia przez FormField). */
export function TenantPicker({
  ref,
  ...props
}: SelectHTMLAttributes<HTMLSelectElement> & { ref?: Ref<HTMLSelectElement> | undefined }) {
  const query = useQuery({
    queryKey: ['public', 'facilities'],
    queryFn: async () => (await listFacilities()).data,
    staleTime: 5 * 60_000,
  });
  return (
    <Select ref={ref} disabled={query.isPending} {...props}>
      <option value="">{query.isPending ? 'Ładowanie…' : 'Wybierz placówkę'}</option>
      {(query.data ?? []).map((f) => (
        <option key={f.slug} value={f.slug}>
          {f.name}
        </option>
      ))}
    </Select>
  );
}
