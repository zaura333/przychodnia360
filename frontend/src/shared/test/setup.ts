import '@testing-library/jest-dom/vitest';
import { cleanup } from '@testing-library/react';
import { afterEach } from 'vitest';

afterEach(() => {
  cleanup();
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
