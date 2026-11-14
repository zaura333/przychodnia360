import { describe, expect, it } from 'vitest';
import { isMaskedPesel, maskPesel } from './index';

describe('mask', () => {
  it('always returns a fixed-length mask regardless of input', () => {
    expect(maskPesel('44051401359')).toBe('***********');
    expect(maskPesel('')).toBe('***********');
    expect(maskPesel(null)).toBe('***********');
  });

  it('recognises masked values', () => {
    expect(isMaskedPesel('***********')).toBe(true);
    expect(isMaskedPesel('44051401359')).toBe(false);
    expect(isMaskedPesel('**********')).toBe(false);
  });
});
