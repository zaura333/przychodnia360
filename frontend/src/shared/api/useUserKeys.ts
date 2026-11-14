import { qk } from './queryClient';
import { useAuth } from './session';

/**
 * Klucze zapytań zależne od zalogowanego użytkownika: dane jednego użytkownika nigdy nie są pod kluczem
 * innego (obok czyszczenia cache przy wylogowaniu i zmianie użytkownika).
 */
export function useUserKeys() {
  const { user } = useAuth();
  return qk.user(user?.userId ?? 'anonymous');
}
