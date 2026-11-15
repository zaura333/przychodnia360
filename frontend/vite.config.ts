import { readFileSync } from 'node:fs';
import { fileURLToPath, URL } from 'node:url';
import tailwindcss from '@tailwindcss/vite';
import react from '@vitejs/plugin-react';
import { defineConfig, type Plugin } from 'vite';

// Adres krawędzi (Traefik) dla proxy /api (konfigurowalny zmienną środowiskową). Katalog i pacjenci są osobnymi
// usługami, więc clinic-core (port 8080) sam nie obsłuży całego API: frontend musi iść przez krawędź (docker compose --profile core).
const backend = process.env.VITE_DEV_BACKEND ?? 'http://localhost:8090';

interface SecurityHeaders {
  headers: Record<string, string>;
  cache: { noStore: string[]; immutablePrefix: string; immutable: string; noStoreValue: string };
}
const security = JSON.parse(
  readFileSync(new URL('./security-headers.json', import.meta.url), 'utf8'),
) as SecurityHeaders;

/** Cache-Control jak w CloudFront: no-store dla index.html i theme-init.js, immutable dla plików z hashem. */
function previewCacheHeaders(): Plugin {
  return {
    name: 'p360-preview-cache-headers',
    configurePreviewServer(server) {
      server.middlewares.use((req, res, next) => {
        const path = (req.url ?? '/').split('?')[0] ?? '/';
        let value: string | undefined;
        if (path.startsWith(security.cache.immutablePrefix)) {
          value = security.cache.immutable;
        } else if (security.cache.noStore.includes(path) || !path.includes('.')) {
          // trasy SPA (bez rozszerzenia) serwują index.html
          value = security.cache.noStoreValue;
        }
        if (value !== undefined) {
          // Serwer plików statycznych ustawia własny Cache-Control, więc nadpisujemy tuż przed wysłaniem
          const cacheValue = value;
          const writeHead = res.writeHead.bind(res) as (...args: unknown[]) => typeof res;
          res.writeHead = (...args: unknown[]) => {
            res.setHeader('Cache-Control', cacheValue);
            return writeHead(...args);
          };
        }
        next();
      });
    },
  };
}

export default defineConfig({
  plugins: [react(), tailwindcss(), previewCacheHeaders()],
  resolve: {
    alias: { '@': fileURLToPath(new URL('./src', import.meta.url)) },
  },
  server: {
    port: 5173,
    // /api na tej samej domenie co SPA: bez CORS, ciasteczka bez przepisywania ścieżki
    proxy: { '/api': { target: backend, changeOrigin: false } },
  },
  preview: {
    port: 4173,
    // Te same nagłówki bezpieczeństwa co docelowo CloudFront: naruszenia CSP wychodzą w CI
    headers: security.headers,
    proxy: { '/api': { target: backend, changeOrigin: false } },
  },
  build: { manifest: true, sourcemap: false },
});
