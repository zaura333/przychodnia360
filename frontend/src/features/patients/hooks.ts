import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { MIN_SEARCH_LENGTH } from './schemas';
import {
  getMyPatient,
  registerPatientByReceptionist,
  searchPatients,
  updateMyPatient,
  updatePatientContact,
} from '@/shared/api/generated/endpoints/patient-controller/patient-controller';
import type { CreatePatientRequest } from '@/shared/api/generated/model';
import { useUserKeys } from '@/shared/api/useUserKeys';
import { useDebounced } from '@/shared/lib/useDebounced';

export function useMyPatient() {
  const keys = useUserKeys();
  return useQuery({
    queryKey: keys.scoped('patients', 'me'),
    queryFn: async () => (await getMyPatient()).data,
  });
}

export function useUpdateMyContact() {
  const keys = useUserKeys();
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: async (contactEmail: string) =>
      (await updateMyPatient(contactEmail === '' ? {} : { contactEmail })).data,
    onSuccess: (patient) => {
      queryClient.setQueryData(keys.scoped('patients', 'me'), patient);
    },
  });
}

/** Wyszukiwanie po imieniu i nazwisku: fraza w ciele POST (nie w adresie), min. 2 znaki, opóźnienie 300 ms. */
export function useSearchPatients(rawQuery: string, size = 20) {
  const keys = useUserKeys();
  const query = useDebounced(rawQuery.trim(), 300);
  const enabled = query.length >= MIN_SEARCH_LENGTH;
  const result = useQuery({
    queryKey: keys.scoped('patients', 'search', query, size),
    queryFn: async () => (await searchPatients({ query, size })).data,
    enabled,
  });
  return { ...result, enabled, debouncedQuery: query };
}

export function useCreatePatient() {
  const keys = useUserKeys();
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: async (body: CreatePatientRequest) =>
      (await registerPatientByReceptionist(body)).data,
    onSuccess: () => queryClient.invalidateQueries({ queryKey: keys.scoped('patients', 'search') }),
  });
}

export function useUpdatePatientContact() {
  const keys = useUserKeys();
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: async ({ id, contactEmail }: { id: string; contactEmail: string }) =>
      (await updatePatientContact(id, contactEmail === '' ? {} : { contactEmail })).data,
    onSuccess: () => queryClient.invalidateQueries({ queryKey: keys.scoped('patients', 'search') }),
  });
}
