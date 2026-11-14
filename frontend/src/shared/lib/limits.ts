/**
 * Stałe limitów wspólne dla schematów formularzy. Docelowym źródłem prawdy jest OpenAPI backendu;
 * do czasu przeniesienia limitów do kontraktu muszą być zgodne z walidacją po stronie serwera.
 */
export const PASSWORD_MIN_LENGTH = 12;
export const PASSWORD_MAX_LENGTH = 128;
export const FACILITY_CODE_MAX_LENGTH = 63;
