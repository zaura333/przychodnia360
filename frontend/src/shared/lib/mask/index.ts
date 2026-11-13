/** Maska stałej długości: nie zdradza ani cyfr, ani ich liczby (PESEL ma 11 znaków). */
export const MASKED_PESEL = '***********';

export function maskPesel(_pesel?: string | null): string {
  return MASKED_PESEL;
}

export function isMaskedPesel(value: string): boolean {
  return /^\*{11}$/.test(value);
}
