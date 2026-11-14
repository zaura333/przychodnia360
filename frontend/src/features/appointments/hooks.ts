import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import {
  bookAppointment,
  cancelAppointment,
  listFreeSlots,
  listMyAppointments,
  listReceptionAppointments,
  rescheduleAppointment,
} from '@/shared/api/generated/endpoints/appointment-controller/appointment-controller';
import type { AppointmentViewStatus, BookRequest } from '@/shared/api/generated/model';
import { useUserKeys } from '@/shared/api/useUserKeys';
import { addDays, type LocalDate } from '@/shared/lib/datetime';

/** Wolne terminy wykonawcy dla usługi w tygodniu zaczynającym się w `weekStart` (7 dni włącznie). */
export function useSlots(
  employeeId: string | undefined,
  serviceId: string | undefined,
  weekStart: LocalDate,
) {
  const keys = useUserKeys();
  return useQuery({
    queryKey: keys.scoped('slots', employeeId, serviceId, weekStart),
    queryFn: async () =>
      (
        await listFreeSlots(employeeId ?? '', {
          serviceId: serviceId ?? '',
          from: weekStart,
          to: addDays(weekStart, 6),
        })
      ).data,
    enabled: employeeId !== undefined && serviceId !== undefined,
    // Lista terminów szybko się starzeje (inni rezerwują równolegle)
    staleTime: 0,
  });
}

/** Odświeża po zmianie wizyt: sloty, wizyty pacjenta, terminarz i grafiki. */
export function useInvalidateAppointments() {
  const keys = useUserKeys();
  const queryClient = useQueryClient();
  return () =>
    Promise.all([
      queryClient.invalidateQueries({ queryKey: keys.scoped('slots') }),
      queryClient.invalidateQueries({ queryKey: keys.scoped('appointments') }),
      queryClient.invalidateQueries({ queryKey: keys.scoped('my-schedule') }),
    ]);
}

export function useBook() {
  const invalidate = useInvalidateAppointments();
  return useMutation({
    mutationFn: async (body: BookRequest) => (await bookAppointment(body)).data,
    onSuccess: invalidate,
    // Po błędzie (np. 409) lista terminów jest nieaktualna
    onError: invalidate,
  });
}

export function useCancel() {
  const invalidate = useInvalidateAppointments();
  return useMutation({
    mutationFn: async (id: string) => {
      await cancelAppointment(id);
    },
    onSuccess: invalidate,
    onError: invalidate,
  });
}

export function useReschedule() {
  const invalidate = useInvalidateAppointments();
  return useMutation({
    mutationFn: async ({ id, startAt }: { id: string; startAt: string }) =>
      (await rescheduleAppointment(id, { startAt })).data,
    onSuccess: invalidate,
    onError: invalidate,
  });
}

export function useMyAppointments() {
  const keys = useUserKeys();
  return useQuery({
    queryKey: keys.scoped('appointments', 'mine'),
    queryFn: async () => (await listMyAppointments()).data,
  });
}

export interface ReceptionFilter {
  from: LocalDate;
  to: LocalDate;
  employeeId?: string | undefined;
  status?: AppointmentViewStatus | undefined;
}

export function useReceptionAppointments(filter: ReceptionFilter, refetchMs?: number) {
  const keys = useUserKeys();
  const { from, to, employeeId, status } = filter;
  return useQuery({
    queryKey: keys.scoped('appointments', 'reception', from, to, employeeId, status),
    queryFn: async () =>
      (
        await listReceptionAppointments({
          from,
          to,
          ...(employeeId ? { employeeId } : {}),
          ...(status ? { status: [status] } : {}),
        })
      ).data,
    ...(refetchMs ? { refetchInterval: refetchMs } : {}),
  });
}
