import { StrictMode } from 'react';
import { createRoot } from 'react-dom/client';
import '../styles/globals.css';
import { App } from '@/app/App';
import { createAppRouter } from '@/app/router';

const container = document.getElementById('root');
if (!container) {
  throw new Error('Brak elementu #root');
}

createRoot(container).render(
  <StrictMode>
    <App router={createAppRouter()} />
  </StrictMode>,
);
