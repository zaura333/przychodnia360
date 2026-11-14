/**
 * Konta demo z seedu backendu (profil local/demo). Hasło seedu pochodzi ze zmiennej środowiskowej
 * (domyślnie wartość lokalna z README), a nie z kodu produkcyjnego.
 */
export const SEED_PASSWORD = process.env.SEED_PASSWORD ?? 'demo-przychodnia-2026';

export const account = (tenant: 'klinika-a' | 'klinika-b', login: string) => ({
  tenant,
  email: `${login}@${tenant}.example.test`,
  password: SEED_PASSWORD,
});

export const MAILPIT = process.env.MAILPIT_URL ?? 'http://127.0.0.1:8025';
