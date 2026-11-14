import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import {
  addScheduleException,
  createEmployee,
  deactivateEmployee,
  deleteScheduleException,
  getEmployeeSchedule,
  listEmployees,
  replaceEmployeeSchedule,
  updateEmployee,
} from '@/shared/api/generated/endpoints/employee-admin-controller/employee-admin-controller';
import type {
  CreateEmployeeRequest,
  ExceptionRequest,
  UpdateEmployeeRequest,
  WeeklyEntryRequest,
} from '@/shared/api/generated/model';
import { useUserKeys } from '@/shared/api/useUserKeys';
import { addDays, type LocalDate } from '@/shared/lib/datetime';

export function useEmployees() {
  const keys = useUserKeys();
  return useQuery({
    queryKey: keys.scoped('employees', 'list'),
    queryFn: async () => (await listEmployees({})).data,
  });
}

function useInvalidateStaff() {
  const keys = useUserKeys();
  const queryClient = useQueryClient();
  return () =>
    Promise.all([
      queryClient.invalidateQueries({ queryKey: keys.scoped('employees') }),
      queryClient.invalidateQueries({ queryKey: keys.scoped('doctors') }),
    ]);
}

export function useCreateEmployee() {
  const invalidate = useInvalidateStaff();
  return useMutation({
    mutationFn: async (body: CreateEmployeeRequest) => (await createEmployee(body)).data,
    onSuccess: invalidate,
  });
}

export function useUpdateEmployee() {
  const invalidate = useInvalidateStaff();
  return useMutation({
    mutationFn: async ({ id, body }: { id: string; body: UpdateEmployeeRequest }) =>
      (await updateEmployee(id, body)).data,
    onSuccess: invalidate,
  });
}

export function useDeactivateEmployee() {
  const invalidate = useInvalidateStaff();
  return useMutation({
    mutationFn: async (id: string) => {
      await deactivateEmployee(id);
    },
    onSuccess: invalidate,
  });
}

/** Grafik administracyjny: wpisy tygodniowe oraz wyjątki w oknie od dziś na 31 dni. */
export function useEmployeeSchedule(id: string | undefined, from: LocalDate) {
  const keys = useUserKeys();
  return useQuery({
    queryKey: keys.scoped('employees', id, 'schedule', from),
    queryFn: async () =>
      (await getEmployeeSchedule(id ?? '', { from, to: addDays(from, 31) })).data,
    enabled: id !== undefined,
    // Edytor zastępuje całość: zawsze świeże dane przed edycją
    staleTime: 0,
  });
}

export function useReplaceSchedule(id: string) {
  const invalidate = useInvalidateStaff();
  return useMutation({
    mutationFn: async (entries: WeeklyEntryRequest[]) =>
      (await replaceEmployeeSchedule(id, { entries })).data,
    onSuccess: invalidate,
  });
}

export function useAddException(id: string) {
  const invalidate = useInvalidateStaff();
  return useMutation({
    mutationFn: async (body: ExceptionRequest) => (await addScheduleException(id, body)).data,
    onSuccess: invalidate,
  });
}

export function useDeleteException(id: string) {
  const invalidate = useInvalidateStaff();
  return useMutation({
    mutationFn: async (exceptionId: string) => {
      await deleteScheduleException(id, exceptionId);
    },
    onSuccess: invalidate,
  });
}
