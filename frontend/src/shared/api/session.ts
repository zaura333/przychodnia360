import { createContext, useContext } from 'react';
import type { LoginResponse, SessionUser } from './authClient';

export type SessionState =
  | { status: 'loading' }
  | { status: 'anonymous'; backendUnavailable: boolean }
  | { status: 'authenticated'; user: SessionUser };

export interface AuthContextValue {
  status: SessionState['status'];
  user: SessionUser | null;
  /** Prawda, gdy start sesji nie powiódł się z powodu sieci lub 5xx (strona logowania pokazuje komunikat). */
  backendUnavailable: boolean;
  /** Przyjmuje odpowiedź logowania z ekranu logowania. */
  login: (response: LoginResponse) => Promise<void>;
  logout: () => Promise<void>;
  refreshSession: () => Promise<void>;
}

export const AuthContext = createContext<AuthContextValue | null>(null);

export function useAuth(): AuthContextValue {
  const value = useContext(AuthContext);
  if (!value) throw new Error('useAuth wymaga AuthProvider');
  return value;
}
