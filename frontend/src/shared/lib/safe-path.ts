/** Dozwolone są wyłącznie ścieżki wewnętrzne ("/pracownik"), nigdy adresy zewnętrzne (open redirect). */
export function isSafeInternalPath(path: unknown): path is string {
  return (
    typeof path === 'string' &&
    path.startsWith('/') &&
    !path.startsWith('//') &&
    !path.includes('\\') &&
    !/^\/[a-z][a-z0-9+.-]*:/i.test(path)
  );
}
