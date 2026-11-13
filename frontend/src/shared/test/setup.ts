import '@testing-library/jest-dom/vitest';
import { cleanup } from '@testing-library/react';
import { afterAll, afterEach, beforeAll } from 'vitest';
import { server } from './server';
import { tokenStore } from '@/shared/api/tokenStore';

beforeAll(() => {
  // Nieobsłużone żądania sieciowe w testach są błędem (testy nie wychodzą do sieci)
  server.listen({ onUnhandledFrame: 'error' });
});

afterEach(() => {
  cleanup();
  server.resetHandlers();
  tokenStore.clear();
  document.documentElement.classList.remove('dark');
});

afterAll(() => {
  server.close();
});

// jsdom nie implementuje metod natywnego <dialog> (typy TS twierdzą inaczej); minimalny polyfill
type DialogProto = { showModal?: () => void; close?: () => void };
const dialogProto = HTMLDialogElement.prototype as unknown as DialogProto;
dialogProto.showModal ??= function showModal(this: HTMLDialogElement) {
  this.setAttribute('open', '');
};
dialogProto.close ??= function close(this: HTMLDialogElement) {
  this.removeAttribute('open');
  this.dispatchEvent(new Event('close'));
};
