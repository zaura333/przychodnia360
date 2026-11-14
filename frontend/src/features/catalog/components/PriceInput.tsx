import { useState, type Ref } from 'react';
import { Input } from '@/shared/ui/fields';

interface PriceInputProps {
  /** Wartość w groszach (liczba całkowita). */
  value: number;
  onChange: (grosze: number) => void;
  id?: string;
  'aria-describedby'?: string;
  'aria-invalid'?: boolean;
  ref?: Ref<HTMLInputElement> | undefined;
}

const PATTERN = /^\d{0,7}([.,]\d{0,2})?$/;

function toGrosze(text: string): number {
  const [whole = '0', frac = ''] = text.replace(',', '.').split('.');
  return Number(whole || '0') * 100 + Number(frac.padEnd(2, '0'));
}

function toText(grosze: number): string {
  if (grosze === 0) return '';
  const whole = Math.floor(grosze / 100);
  const frac = String(grosze % 100).padStart(2, '0');
  return `${String(whole)},${frac}`;
}

/** Cena w PLN: użytkownik pisze "12,50", stan trzymany w groszach, formatowanie tylko w prezentacji. */
export function PriceInput({ value, onChange, ...rest }: PriceInputProps) {
  const [text, setText] = useState(() => toText(value));
  return (
    <div className="flex items-center gap-2">
      <Input
        {...rest}
        inputMode="decimal"
        autoComplete="off"
        value={text}
        placeholder="0,00"
        onChange={(e) => {
          const next = e.target.value;
          if (!PATTERN.test(next)) return;
          setText(next);
          onChange(toGrosze(next));
        }}
        onBlur={() => {
          setText(toText(value));
        }}
      />
      <span aria-hidden="true">zł</span>
    </div>
  );
}
