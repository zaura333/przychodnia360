import { useQueryClient } from '@tanstack/react-query';
import { useCallback, useEffect, useMemo, useRef, useState, type ReactNode } from 'react';
import { ApiError } from '@/shared/api/apiError';
import { fetchMe, logout as apiLogout, refresh, type LoginResponse } from '@/shared/api/authClient';
import { sessionExpired } from '@/shared/api/events';
import { AuthContext, type AuthContextValue, type SessionState } from '@/shared/api/session';
import { tokenStore } from '@/shared/api/tokenStore';

interface Props {
  children: ReactNode;
  /** Zadany stan (testy): pomija ciche odświeżenie przy starcie. */
  initialState?: SessionState;
}

/**
 * Maszyna stanów sesji: loading -> (authenticated | anonymous). Przy starcie ciche refresh():
 * sukces -> fetchMe -> authenticated; 401 -> anonymous; błąd sieci lub 5xx -> anonymous z flagą
 * backendUnavailable. Cache zapytań jest czyszczony przy wylogowaniu, wygaśnięciu sesji i zmianie użytkownika.
 */
export function AuthProvider({ children, initialState }: Props) {
  const queryClient = useQueryClient();
  const [state, setState] = useState<SessionState>(initialState ?? { status: 'loading' });
  const started = useRef(initialState !== undefined);
  const lastUserId = useRef<string | null>(
    initialState?.status === 'authenticated' ? initialState.user.userId : null,
  );

  const establishSession = useCallback(async (token: string, knownUser?: LoginResponse['user']) => {
    tokenStore.set(token);
    const user = knownUser ?? (await fetchMe());
    setState({ status: 'authenticated', user });
  }, []);

  const clearSession = useCallback(
    (backendUnavailable = false) => {
      tokenStore.clear();
      queryClient.clear();
      setState({ status: 'anonymous', backendUnavailable });
    },
    [queryClient],
  );

  const refreshSession = useCallback(async () => {
    try {
      await establishSession(await refresh());
    } catch (error) {
      const unavailable = !(error instanceof ApiError) || error.isNetwork || error.status >= 500;
      clearSession(unavailable && !(error instanceof ApiError && error.status === 401));
    }
  }, [establishSession, clearSession]);

  // Ciche odświeżenie przy starcie (raz, także w StrictMode, bo odświeżenie obraca ciasteczko)
  useEffect(() => {
    if (started.current) return;
    started.current = true;
    void refreshSession();
  }, [refreshSession]);

  // Wygaśnięcie sesji zgłoszone przez mutator
  useEffect(
    () =>
      sessionExpired.subscribe(() => {
        clearSession();
      }),
    [clearSession],
  );

  // Zmiana użytkownika: czyszczenie pamięci podręcznej
  const userId = state.status === 'authenticated' ? state.user.userId : null;
  useEffect(() => {
    if (lastUserId.current !== null && lastUserId.current !== userId) {
      queryClient.clear();
    }
    lastUserId.current = userId;
  }, [userId, queryClient]);

  const value = useMemo<AuthContextValue>(
    () => ({
      status: state.status,
      user: state.status === 'authenticated' ? state.user : null,
      backendUnavailable: state.status === 'anonymous' && state.backendUnavailable,
      login: (response) => establishSession(response.accessToken, response.user),
      logout: async () => {
        try {
          await apiLogout();
        } finally {
          clearSession();
        }
      },
      refreshSession,
    }),
    [state, establishSession, clearSession, refreshSession],
  );

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}
