import { useMutation } from '@tanstack/react-query';
import { rememberTenant } from './components/TenantPicker';
import type { LoginForm, PlatformLoginForm, RegisterForm } from './schemas';
import {
  confirmPasswordReset,
  registerPatient,
  requestPasswordReset,
} from '@/shared/api/generated/endpoints/account-controller/account-controller';
import {
  login,
  loginPlatformAdmin,
} from '@/shared/api/generated/endpoints/auth-controller/auth-controller';
import { useAuth } from '@/shared/api/session';

/** Logowanie do placówki: token do pamięci, profil z /me (AuthProvider), zapamiętanie kodu placówki. */
export function useLogin() {
  const auth = useAuth();
  return useMutation({
    mutationFn: async (form: LoginForm) => {
      const { data } = await login(form);
      await auth.login({ accessToken: data.accessToken });
      rememberTenant(form.tenantSlug);
    },
  });
}

export function usePlatformLogin() {
  const auth = useAuth();
  return useMutation({
    mutationFn: async (form: PlatformLoginForm) => {
      const { data } = await loginPlatformAdmin(form);
      await auth.login({ accessToken: data.accessToken });
    },
  });
}

export function useRegisterPatient() {
  return useMutation({
    mutationFn: async ({ passwordConfirm: _confirm, ...body }: RegisterForm) => {
      await registerPatient(body);
    },
  });
}

export function useRequestPasswordReset() {
  return useMutation({
    mutationFn: async (body: { tenantSlug: string; email: string }) => {
      await requestPasswordReset(body);
    },
  });
}

export function useConfirmPasswordReset() {
  return useMutation({
    mutationFn: async (body: { token: string; newPassword: string }) => {
      await confirmPasswordReset(body);
    },
  });
}
