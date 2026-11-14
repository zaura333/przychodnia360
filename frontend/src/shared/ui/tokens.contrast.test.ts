import { readFileSync } from 'node:fs';
import { resolve } from 'node:path';
import { describe, expect, it } from 'vitest';

const css = readFileSync(resolve(import.meta.dirname, '../../../styles/globals.css'), 'utf8');

function block(selector: string): Record<string, string> {
  const match = new RegExp(`${selector.replace('.', '\\.')}\\s*\\{([^}]*)\\}`).exec(css);
  if (!match?.[1]) throw new Error(`Brak bloku ${selector}`);
  const tokens: Record<string, string> = {};
  for (const m of match[1].matchAll(/--([a-z-]+):\s*(#[0-9a-fA-F]{6})\s*;/g)) {
    tokens[m[1] as string] = m[2] as string;
  }
  return tokens;
}

function luminance(hex: string): number {
  const channels = [1, 3, 5].map((i) => parseInt(hex.slice(i, i + 2), 16) / 255);
  const [r, g, b] = channels.map((c) => (c <= 0.03928 ? c / 12.92 : ((c + 0.055) / 1.055) ** 2.4));
  return 0.2126 * (r ?? 0) + 0.7152 * (g ?? 0) + 0.0722 * (b ?? 0);
}

function contrast(a: string, b: string): number {
  const [hi, lo] = [luminance(a), luminance(b)].sort((x, y) => y - x);
  return ((hi ?? 0) + 0.05) / ((lo ?? 0) + 0.05);
}

/** Pary tekst/tło (AA: 4,5:1) i elementy graficzne/obramowania pól (3:1). */
const TEXT_PAIRS: [string, string][] = [
  ['foreground', 'background'],
  ['muted-foreground', 'background'],
  ['muted-foreground', 'muted'],
  ['foreground', 'muted'],
  ['primary-foreground', 'primary'],
  ['secondary-foreground', 'secondary'],
  ['accent-foreground', 'accent'],
  ['destructive-foreground', 'destructive'],
  ['success-foreground', 'success'],
  ['warning-foreground', 'warning'],
  ['info-foreground', 'info'],
  ['primary', 'background'], // tekst linków w kolorze primary
];
const GRAPHIC_PAIRS: [string, string][] = [
  ['input', 'background'],
  ['ring', 'background'],
];
/** Tokeny dekoracyjne bez wymogu kontrastu (jawnie, żeby nowy token nie zostawał niezauważony). */
const DECORATIVE = ['border'];

describe.each([
  [':root', 'jasny'],
  ['.dark', 'ciemny'],
])('kontrast tokenów (%s, motyw %s)', (selector) => {
  const tokens = block(selector);

  it.each(TEXT_PAIRS)('tekst %s na %s ma kontrast co najmniej 4,5:1', (fg, bg) => {
    expect(contrast(tokens[fg] as string, tokens[bg] as string)).toBeGreaterThanOrEqual(4.5);
  });

  it.each(GRAPHIC_PAIRS)('element %s na %s ma kontrast co najmniej 3:1', (fg, bg) => {
    expect(contrast(tokens[fg] as string, tokens[bg] as string)).toBeGreaterThanOrEqual(3);
  });

  it('każdy token ma parę w teście albo jest jawnie dekoracyjny', () => {
    const covered = new Set([...TEXT_PAIRS.flat(), ...GRAPHIC_PAIRS.flat(), ...DECORATIVE]);
    const uncovered = Object.keys(tokens).filter((t) => !covered.has(t));
    expect(uncovered).toEqual([]);
  });
});

describe('spójność motywów', () => {
  it('jasny i ciemny motyw definiują te same tokeny', () => {
    expect(Object.keys(block('.dark')).sort()).toEqual(Object.keys(block(':root')).sort());
  });
});
