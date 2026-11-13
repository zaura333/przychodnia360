/** Proste zdarzenia warstwy API dla interfejsu (bez zależności od Reacta). */
type Listener<T> = (payload: T) => void;

function createEmitter<T>() {
  const listeners = new Set<Listener<T>>();
  return {
    emit(payload: T): void {
      listeners.forEach((l) => {
        l(payload);
      });
    },
    subscribe(listener: Listener<T>): () => void {
      listeners.add(listener);
      return () => {
        listeners.delete(listener);
      };
    },
  };
}

const expiredEmitter = createEmitter<null>();

/** Sesja wygasła i nie dało się jej odświeżyć (po tym zdarzeniu mutator odrzuca żądanie). */
export const sessionExpired = {
  emit: (): void => {
    expiredEmitter.emit(null);
  },
  subscribe: (listener: () => void): (() => void) =>
    expiredEmitter.subscribe(() => {
      listener();
    }),
};

let slowCount = 0;
const slowEmitter = createEmitter<number>();

/** Liczba żądań trwających dłużej niż próg (zimny start Lambdy); UI pokazuje komunikat, gdy > 0. */
export const slowRequests = {
  get: (): number => slowCount,
  subscribe: (listener: Listener<number>): (() => void) => slowEmitter.subscribe(listener),
  increment(): void {
    slowCount += 1;
    slowEmitter.emit(slowCount);
  },
  decrement(): void {
    slowCount = Math.max(0, slowCount - 1);
    slowEmitter.emit(slowCount);
  },
};
