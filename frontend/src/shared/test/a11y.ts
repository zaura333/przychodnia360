import { axe } from 'vitest-axe';
import { expect } from 'vitest';

/** Pomocnik dla testów stron i komponentów: brak naruszeń dostępności wykrywalnych przez axe. */
export async function expectNoA11yViolations(container: Element): Promise<void> {
  const results = await axe(container);
  const summary = results.violations.map((v) => `${v.id}: ${v.help} (${String(v.nodes.length)})`);
  expect(summary).toEqual([]);
}
