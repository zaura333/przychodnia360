// Skrypt motywu w osobnym pliku (zgodny z CSP script-src 'self'): ładowany synchronicznie z <head>,
// żeby uniknąć błysku jasnego motywu. Logika spójna z ThemeProvider (klucz p360_theme).
(function () {
  try {
    var stored = window.localStorage.getItem('p360_theme');
    var dark =
      stored === 'dark' ||
      ((stored === null || stored === 'system') &&
        window.matchMedia &&
        window.matchMedia('(prefers-color-scheme: dark)').matches);
    if (dark) document.documentElement.classList.add('dark');
  } catch (_e) {
    // magazyn niedostępny: zostaje motyw jasny
  }
})();
