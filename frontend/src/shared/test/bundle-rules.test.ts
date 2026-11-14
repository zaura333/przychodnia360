import { describe, expect, it } from 'vitest';
import { BUDGET, evaluate } from '../../../scripts/check-bundle.mjs';

type Manifest = Record<string, { file: string; isEntry?: boolean; imports?: string[] }>;

const healthy = (): Manifest => ({
  'index.html': { file: 'assets/index.js', isEntry: true, imports: ['_vendor.js'] },
  '_vendor.js': { file: 'assets/vendor.js' },
  'src/app/areas/pacjent/index.tsx': { file: 'assets/pacjent.js', imports: ['_vendor.js'] },
  'src/app/areas/pracownik/index.tsx': { file: 'assets/pracownik.js', imports: ['_vendor.js'] },
  'src/app/areas/administracja/index.tsx': { file: 'assets/administracja.js' },
  'src/app/areas/platforma/index.tsx': { file: 'assets/platforma.js' },
});

const small = () => 10 * 1024;

describe('check-bundle: reguły manifestu i budżet', () => {
  it('accepts a healthy build', () => {
    expect(evaluate(healthy(), small)).toEqual([]);
  });

  it('catches a static import of an area from the entry', () => {
    const m = healthy();
    m['index.html'] = {
      file: 'assets/index.js',
      isEntry: true,
      imports: ['_vendor.js', 'src/app/areas/administracja/index.tsx'],
    };
    expect(evaluate(m, small).join('\n')).toContain(
      'Wejście importuje statycznie obszar "administracja"',
    );
  });

  it('catches an area importing another area', () => {
    const m = healthy();
    m['src/app/areas/pacjent/index.tsx'] = {
      file: 'assets/pacjent.js',
      imports: ['src/app/areas/platforma/index.tsx'],
    };
    expect(evaluate(m, small).join('\n')).toContain(
      'Obszar "pacjent" importuje statycznie obszar "platforma"',
    );
  });

  it('catches a missing area chunk', () => {
    const m = healthy();
    delete m['src/app/areas/platforma/index.tsx'];
    expect(evaluate(m, small).join('\n')).toContain('Brak osobnego chunku obszaru: platforma');
  });

  it('catches an exceeded size budget', () => {
    const big = (file: string) => (file.includes('index') ? BUDGET.initial + 1 : 1024);
    expect(evaluate(healthy(), big).join('\n')).toContain('Chunk początkowy');
    const bigArea = (file: string) => (file.includes('pacjent') ? BUDGET.area + 1 : 1024);
    expect(evaluate(healthy(), bigArea).join('\n')).toContain('Chunk obszaru "pacjent"');
  });
});
