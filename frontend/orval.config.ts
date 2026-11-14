import { defineConfig } from 'orval';

// Klient generowany z kontraktu backendu. Wynik (src/shared/api/generated) nie jest commitowany.
export default defineConfig({
  api: {
    input: '../api/openapi.json',
    output: {
      mode: 'tags-split',
      target: 'src/shared/api/generated/endpoints',
      schemas: 'src/shared/api/generated/model',
      client: 'react-query',
      httpClient: 'fetch',
      mock: true, // handlery MSW do testów
      clean: true,
      override: {
        mutator: { path: 'src/shared/api/http.ts', name: 'customFetch' },
      },
    },
  },
});
