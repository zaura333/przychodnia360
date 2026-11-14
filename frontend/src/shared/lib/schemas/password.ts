import { z } from 'zod';

/**
 * Polityka hasła zgodna z backendem (ADR-B07/B06): długość 12-128, bez wymogu klas znaków.
 * Listę popularnych haseł sprawdza wyłącznie backend, a jego błąd walidacji trafia do pola.
 */
export const PASSWORD_MIN = 12;
export const PASSWORD_MAX = 128;

export const passwordSchema = z
  .string()
  .min(PASSWORD_MIN, `Hasło musi mieć co najmniej ${String(PASSWORD_MIN)} znaków`)
  .max(PASSWORD_MAX, `Hasło może mieć najwyżej ${String(PASSWORD_MAX)} znaków`);
