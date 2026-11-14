import { useSyncExternalStore } from 'react';
import { slowRequests } from '@/shared/api/events';

/** Widoczny, gdy jakieś żądanie trwa dłużej niż 5 s (zimny start Lambdy). */
export function SlowBackendNotice() {
  const slow = useSyncExternalStore(slowRequests.subscribe, slowRequests.get);
  if (slow === 0) return null;
  return (
    <p role="status" className="bg-info px-4 py-2 text-center text-sm text-info-foreground">
      Serwer się uruchamia, to może potrwać chwilę.
    </p>
  );
}
