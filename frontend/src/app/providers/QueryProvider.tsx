import { QueryClientProvider } from '@tanstack/react-query';
import { useState, type ReactNode } from 'react';
import { createQueryClient } from '@/shared/api/queryClient';

/**
 * Brak trwałego cache (bez persistera): dane z pamięci podręcznej nie trafiają na dysk.
 * Reguła zapisana w README.
 */
export function QueryProvider({ children }: { children: ReactNode }) {
  const [client] = useState(createQueryClient);
  return <QueryClientProvider client={client}>{children}</QueryClientProvider>;
}
