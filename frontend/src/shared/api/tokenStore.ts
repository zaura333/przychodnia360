/**
 * Token dostępu wyłącznie w pamięci (zmienna modułu z subskrypcją). Nigdy w localStorage ani sessionStorage:
 * odświeżanie sesji odbywa się ciasteczkiem HttpOnly, więc po przeładowaniu strony token odzyskuje się z refresh.
 */
type Listener = (token: string | null) => void;

let token: string | null = null;
const listeners = new Set<Listener>();

export const tokenStore = {
  get: (): string | null => token,
  set(next: string): void {
    token = next;
    listeners.forEach((l) => {
      l(token);
    });
  },
  clear(): void {
    token = null;
    listeners.forEach((l) => {
      l(token);
    });
  },
  subscribe(listener: Listener): () => void {
    listeners.add(listener);
    return () => {
      listeners.delete(listener);
    };
  },
};
