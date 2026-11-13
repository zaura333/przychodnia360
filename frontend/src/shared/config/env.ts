import { z } from 'zod';

/** Zmienne VITE_* są publiczne (trafiają do paczki), więc nie zawierają sekretów. */
const flag = z
  .enum(['true', 'false'])
  .default('false')
  .transform((v) => v === 'true');

const schema = z.object({
  VITE_API_BASE: z.string().min(1).default('/api/v1'),
  VITE_FEATURE_MEDICAL_STAFF: flag,
  VITE_SHOW_DEMO_ACCOUNTS: flag,
});

export function parseEnv(source: Record<string, unknown>) {
  const parsed = schema.parse(source);
  return {
    apiBase: parsed.VITE_API_BASE.replace(/\/+$/, ''),
    featureMedicalStaff: parsed.VITE_FEATURE_MEDICAL_STAFF,
    showDemoAccounts: parsed.VITE_SHOW_DEMO_ACCOUNTS,
  };
}

/** Walidowane przy starcie: błędna konfiguracja kończy się czytelnym błędem, nie cichą awarią. */
export const env = parseEnv(import.meta.env);
