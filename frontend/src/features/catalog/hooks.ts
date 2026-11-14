import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import {
  createService,
  createServiceCategory,
  deactivateService,
  deleteServiceCategory,
  renameServiceCategory,
  updateService,
} from '@/shared/api/generated/endpoints/service-admin-controller/service-admin-controller';
import {
  getService,
  listServiceCategories,
  listServices,
} from '@/shared/api/generated/endpoints/service-controller/service-controller';
import type { ListServicesParams, ServiceRequest } from '@/shared/api/generated/model';
import { useUserKeys } from '@/shared/api/useUserKeys';

export function useServices(params: ListServicesParams) {
  const keys = useUserKeys();
  return useQuery({
    queryKey: keys.scoped('services', 'list', params),
    queryFn: async () => (await listServices(params)).data,
    placeholderData: keepPreviousData,
  });
}

export function useService(id: string | undefined) {
  const keys = useUserKeys();
  return useQuery({
    queryKey: keys.scoped('services', 'one', id),
    queryFn: async () => (await getService(id ?? '')).data,
    enabled: id !== undefined,
  });
}

export function useCategories() {
  const keys = useUserKeys();
  return useQuery({
    queryKey: keys.scoped('services', 'categories'),
    queryFn: async () => (await listServiceCategories()).data,
  });
}

/** Po każdej zmianie katalogu odświeżamy wszystkie zapytania o usługi i kategorie. */
function useInvalidateCatalog() {
  const keys = useUserKeys();
  const queryClient = useQueryClient();
  return () => queryClient.invalidateQueries({ queryKey: keys.scoped('services') });
}

export function useSaveService() {
  const invalidate = useInvalidateCatalog();
  return useMutation({
    mutationFn: async ({ id, body }: { id?: string | undefined; body: ServiceRequest }) =>
      (id ? await updateService(id, body) : await createService(body)).data,
    onSuccess: invalidate,
  });
}

export function useDeactivateService() {
  const invalidate = useInvalidateCatalog();
  return useMutation({
    mutationFn: async (id: string) => {
      await deactivateService(id);
    },
    onSuccess: invalidate,
  });
}

export function useSaveCategory() {
  const invalidate = useInvalidateCatalog();
  return useMutation({
    mutationFn: async ({ id, name }: { id?: string | undefined; name: string }) =>
      (id ? await renameServiceCategory(id, { name }) : await createServiceCategory({ name })).data,
    onSuccess: invalidate,
  });
}

export function useDeleteCategory() {
  const invalidate = useInvalidateCatalog();
  return useMutation({
    mutationFn: async (id: string) => {
      await deleteServiceCategory(id);
    },
    onSuccess: invalidate,
  });
}
