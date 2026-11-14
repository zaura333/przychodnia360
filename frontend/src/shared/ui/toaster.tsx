import { useCallback, useSyncExternalStore } from 'react';
import { cn } from '@/shared/lib/cn';

export type ToastKind = 'success' | 'error' | 'warning';
interface Toast {
  id: number;
  kind: ToastKind;
  message: string;
}

const DISMISS_AFTER_MS = 6000;
let toasts: readonly Toast[] = [];
let nextId = 1;
const listeners = new Set<() => void>();

const emit = () => {
  listeners.forEach((l) => {
    l();
  });
};

export function dismissToast(id: number): void {
  toasts = toasts.filter((t) => t.id !== id);
  emit();
}

export function pushToast(kind: ToastKind, message: string): void {
  const id = nextId++;
  toasts = [...toasts, { id, kind, message }];
  emit();
  setTimeout(() => {
    dismissToast(id);
  }, DISMISS_AFTER_MS);
}

/** Powiadomienia bez wstrzykiwania stylów (CSP): własny, lekki kontener zamiast bibliotek typu Sonner. */
export function useNotify() {
  return {
    success: useCallback((message: string) => {
      pushToast('success', message);
    }, []),
    error: useCallback((message: string) => {
      pushToast('error', message);
    }, []),
    warning: useCallback((message: string) => {
      pushToast('warning', message);
    }, []),
  };
}

const subscribe = (listener: () => void) => {
  listeners.add(listener);
  return () => {
    listeners.delete(listener);
  };
};

const styles: Record<ToastKind, string> = {
  success: 'bg-success text-success-foreground',
  error: 'bg-destructive text-destructive-foreground',
  warning: 'bg-warning text-warning-foreground',
};

/** Komunikaty dostępne dla czytników ekranu: błędy jako role=alert, reszta jako role=status. */
export function Toaster() {
  const items = useSyncExternalStore(subscribe, () => toasts);
  return (
    <div aria-live="polite" className="fixed right-4 bottom-4 z-50 flex max-w-sm flex-col gap-2">
      {items.map((t) => (
        <div
          key={t.id}
          role={t.kind === 'error' ? 'alert' : 'status'}
          className={cn(
            'flex items-start gap-3 rounded-md px-4 py-3 text-sm shadow-md',
            styles[t.kind],
          )}
        >
          <span className="flex-1">{t.message}</span>
          <button
            type="button"
            aria-label="Zamknij powiadomienie"
            className="touch-target -my-2 -mr-2"
            onClick={() => {
              dismissToast(t.id);
            }}
          >
            ×
          </button>
        </div>
      ))}
    </div>
  );
}
