import { readFileSync, writeFileSync } from 'node:fs';
import { createRequire } from 'node:module';
import { expect, test, type Page } from '@playwright/test';

/**
 * Pomiary prototypu ScheduleGrid (F0.8) w Chromium na buildzie produkcyjnym z nagłówkami CSP.
 * Progi informacyjne: render 12 kolumn < 150 ms, 30 kolumn < 250 ms, reakcja na fokus i klik < 50 ms.
 */
const results: Record<string, unknown> = {};
const median = (xs: number[]) => [...xs].sort((a, b) => a - b)[Math.floor(xs.length / 2)] ?? 0;
const round = (n: number) => Math.round(n * 10) / 10;

async function throttle(page: Page, rate: number) {
  const cdp = await page.context().newCDPSession(page);
  await cdp.send('Emulation.setCPUThrottlingRate', { rate });
}

async function trackCsp(page: Page) {
  await page.addInitScript(() => {
    (window as unknown as { __csp: string[] }).__csp = [];
    document.addEventListener('securitypolicyviolation', (e) => {
      (window as unknown as { __csp: string[] }).__csp.push(
        `${e.violatedDirective} ${e.blockedURI}`,
      );
    });
  });
}

async function renderOnce(page: Page, columns: number): Promise<number> {
  await page.route('**/api/v1/auth/refresh', (r) => r.fulfill({ status: 401, body: '{}' }));
  await page.goto(`/dev/schedule?columns=${String(columns)}`);
  await page.getByRole('button', { name: 'Renderuj siatkę' }).click();
  await expect(page.getByTestId('render-ms')).not.toHaveText('');
  return Number(await page.getByTestId('render-ms').textContent());
}

for (const [name, rate] of [
  ['bez throttlingu', 1],
  ['CPU 4x wolniej', 4],
] as const) {
  test.describe(name, () => {
    for (const columns of [12, 30]) {
      test(`pierwszy render, ${String(columns)} kolumn`, async ({ page }) => {
        await trackCsp(page);
        await throttle(page, rate);
        const samples: number[] = [];
        for (let i = 0; i < 7; i++) samples.push(await renderOnce(page, columns));
        const m = round(median(samples));
        results[`render_${String(columns)}_x${String(rate)}`] = { median: m, samples };
        if (rate === 1)
          expect.soft(m, 'próg informacyjny').toBeLessThan(columns === 12 ? 150 : 250);
        const violations = await page.evaluate(
          () => (window as unknown as { __csp: string[] }).__csp,
        );
        expect(violations).toEqual([]);
      });
    }

    test(`interakcja i przewijanie, 30 kolumn`, async ({ page }) => {
      await trackCsp(page);
      await throttle(page, rate);
      await renderOnce(page, 30);
      const styleTagsBefore = await page.locator('style').count();

      const metrics = await page.evaluate(async () => {
        const frame = () =>
          new Promise<void>((r) => {
            requestAnimationFrame(() => {
              requestAnimationFrame(() => {
                r();
              });
            });
          });
        const grid = document.querySelector<HTMLElement>('[role=grid]');
        if (!grid) throw new Error('brak siatki');
        grid.querySelector<HTMLElement>('[role=gridcell][tabindex="0"]')?.focus();

        // opóźnienie ruchu fokusu: 10x w dół, 10x w prawo (przy 30 kolumnach przewija i renderuje kolumny)
        const focus: number[] = [];
        for (let i = 0; i < 20; i++) {
          const el = document.activeElement as HTMLElement;
          const t = performance.now();
          el.dispatchEvent(
            new KeyboardEvent('keydown', {
              key: i < 10 ? 'ArrowDown' : 'ArrowRight',
              bubbles: true,
            }),
          );
          await frame();
          focus.push(performance.now() - t);
        }

        // opóźnienie kliknięcia w wolny slot
        const free = [...grid.querySelectorAll<HTMLElement>('[role=gridcell]')]
          .filter((c) => c.getAttribute('aria-label')?.endsWith('wolny'))
          .slice(0, 10);
        const click: number[] = [];
        for (const c of free) {
          const t = performance.now();
          c.click();
          await frame();
          click.push(performance.now() - t);
        }

        // przewijanie poziome: 60 klatek, krok 40 px
        grid.scrollLeft = 0;
        const deltas: number[] = [];
        let last = performance.now();
        for (let i = 0; i < 60; i++) {
          grid.scrollLeft += 40;
          await new Promise<void>((r) => {
            requestAnimationFrame(() => {
              r();
            });
          });
          const now = performance.now();
          deltas.push(now - last);
          last = now;
        }
        return { focus, click, deltas };
      });

      const med = (xs: number[]) => [...xs].sort((a, b) => a - b)[Math.floor(xs.length / 2)] ?? 0;
      const out = {
        focusMedianMs: round(med(metrics.focus)),
        focusMaxMs: round(Math.max(...metrics.focus)),
        clickMedianMs: round(med(metrics.click)),
        clickMaxMs: round(Math.max(...metrics.click)),
        scrollFrameMedianMs: round(med(metrics.deltas)),
        scrollFrameMaxMs: round(Math.max(...metrics.deltas)),
        scrollFramesOver50ms: metrics.deltas.filter((d) => d > 50).length,
      };
      results[`interaction_30_x${String(rate)}`] = out;
      if (rate === 1) {
        expect.soft(out.focusMedianMs, 'fokus < 50 ms').toBeLessThan(50);
        expect.soft(out.clickMedianMs, 'klik < 50 ms').toBeLessThan(50);
      }
      // zgodność z CSP: brak naruszeń i brak wstrzykniętych znaczników <style> przez siatkę
      expect(await page.locator('style').count()).toBe(styleTagsBefore);
      expect(await page.evaluate(() => (window as unknown as { __csp: string[] }).__csp)).toEqual(
        [],
      );
    });
  });
}

test('dostępność (axe) siatki 30 kolumn w przeglądarce', async ({ browser }) => {
  // axe wstrzykiwane jako skrypt wymaga wyłączenia CSP tylko w tym teście (CSP sprawdzają testy wyżej)
  const context = await browser.newContext({ bypassCSP: true });
  const page = await context.newPage();
  await renderOnce(page, 30);
  const axeSource = readFileSync(
    createRequire(import.meta.url).resolve('axe-core/axe.min.js'),
    'utf8',
  );
  await page.addScriptTag({ content: axeSource });
  const violations = await page.evaluate(async () => {
    const axe = (
      window as unknown as {
        axe: { run: (el: Element) => Promise<{ violations: { id: string; nodes: unknown[] }[] }> };
      }
    ).axe;
    const grid = document.querySelector('[role=grid]');
    if (!grid) throw new Error('brak siatki');
    const r = await axe.run(grid);
    return r.violations.map((v) => `${v.id} (${String(v.nodes.length)})`);
  });
  results['axe_30'] = violations;
  expect(violations).toEqual([]);
  await context.close();
});

test.afterAll(() => {
  writeFileSync('bench/results.json', JSON.stringify(results, null, 2));
});
