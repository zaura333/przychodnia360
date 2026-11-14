import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { createTenantAdmin } from '@/shared/api/generated/endpoints/platform-admin-controller/platform-admin-controller';
import {
  createTenant,
  listTenants,
  updateTenant,
} from '@/shared/api/generated/endpoints/tenant-admin-controller/tenant-admin-controller';
import { useUserKeys } from '@/shared/api/useUserKeys';

export function useTenants() {
  const keys = useUserKeys();
  return useQuery({
    queryKey: keys.scoped('platform', 'tenants'),
    queryFn: async () => (await listTenants()).data,
  });
}

function useInvalidateTenants() {
  const keys = useUserKeys();
  const queryClient = useQueryClient();
  return () => queryClient.invalidateQueries({ queryKey: keys.scoped('platform', 'tenants') });
}

export function useCreateTenant() {
  const invalidate = useInvalidateTenants();
  return useMutation({
    mutationFn: async (body: { name: string; slug: string }) => (await createTenant(body)).data,
    onSuccess: invalidate,
  });
}

export function useSetTenantActive() {
  const invalidate = useInvalidateTenants();
  return useMutation({
    mutationFn: async ({ id, active }: { id: string; active: boolean }) =>
      (await updateTenant(id, { active })).data,
    onSuccess: invalidate,
  });
}

export function useCreateTenantAdmin() {
  return useMutation({
    mutationFn: async ({ tenantId, email }: { tenantId: string; email: string }) =>
      (await createTenantAdmin(tenantId, { email })).data,
  });
}
