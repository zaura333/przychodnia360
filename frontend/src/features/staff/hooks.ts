import { useQuery } from '@tanstack/react-query';
import {
  getDoctorSchedule,
  listDoctors,
} from '@/shared/api/generated/endpoints/doctor-controller/doctor-controller';
import { getMySchedule } from '@/shared/api/generated/endpoints/my-schedule-controller/my-schedule-controller';
import { useUserKeys } from '@/shared/api/useUserKeys';
import { addDays, type LocalDate } from '@/shared/lib/datetime';

export function useDoctors() {
  const keys = useUserKeys();
  return useQuery({
    queryKey: keys.scoped('doctors'),
    queryFn: async () => (await listDoctors()).data,
  });
}

/** Grafik lekarza dla zakresu dat (od-do włącznie, okno do 32 dni po stronie backendu). */
export function useDoctorSchedule(id: string | undefined, from: LocalDate, to: LocalDate) {
  const keys = useUserKeys();
  return useQuery({
    queryKey: keys.scoped('doctors', id, 'schedule', from, to),
    queryFn: async () => (await getDoctorSchedule(id ?? '', { from, to })).data,
    enabled: id !== undefined,
  });
}

/** Tydzień od poniedziałku `start` (7 dni włącznie). */
export const weekEnd = (start: LocalDate): LocalDate => addDays(start, 6);

export function useMySchedule(from: LocalDate, to: LocalDate, refetchMs?: number) {
  const keys = useUserKeys();
  return useQuery({
    queryKey: keys.scoped('my-schedule', from, to),
    queryFn: async () => (await getMySchedule({ from, to })).data,
    ...(refetchMs ? { refetchInterval: refetchMs } : {}),
  });
}
