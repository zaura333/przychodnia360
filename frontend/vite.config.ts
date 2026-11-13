import { fileURLToPath, URL } from 'node:url';
import tailwindcss from '@tailwindcss/vite';
import react from '@vitejs/plugin-react';
import { defineConfig } from 'vite';

// Adres lokalnego backendu dla proxy deweloperskiego (konfigurowalny zmienną środowiskową)
const backend = process.env.VITE_DEV_BACKEND ?? 'http://localhost:8080';

export default defineConfig({
  plugins: [react(), tailwindcss()],
  resolve: {
    alias: { '@': fileURLToPath(new URL('./src', import.meta.url)) },
  },
  server: {
    port: 5173,
    // /api na tej samej domenie co SPA: bez CORS, ciasteczka bez przepisywania ścieżki
    proxy: { '/api': { target: backend, changeOrigin: false } },
  },
  build: { manifest: true, sourcemap: false },
});
