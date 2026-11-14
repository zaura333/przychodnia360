import { useId, type ReactElement, type ReactNode } from 'react';
import { cloneElement } from 'react';
import { Label } from '@/shared/ui/fields';

interface FormFieldProps {
  label: string;
  /** Komunikat błędu pola (np. z React Hook Form `errors.x?.message`). */
  error?: string | undefined;
  hint?: string;
  /** Jedno pole formularza; zostaje mu nadane id oraz aria-describedby/aria-invalid. */
  children: ReactElement<{ id?: string; 'aria-describedby'?: string; 'aria-invalid'?: boolean }>;
}

/** Pole z etykietą i komunikatem błędu powiązanymi z kontrolką (dostępność), do użycia z React Hook Form. */
export function FormField({ label, error, hint, children }: FormFieldProps): ReactNode {
  const id = useId();
  const errorId = `${id}-error`;
  const hintId = `${id}-hint`;
  const describedBy = [error ? errorId : null, hint ? hintId : null].filter(Boolean).join(' ');
  return (
    <div className="flex flex-col gap-1">
      <Label htmlFor={id}>{label}</Label>
      {cloneElement(children, {
        id,
        'aria-invalid': Boolean(error),
        ...(describedBy ? { 'aria-describedby': describedBy } : {}),
      })}
      {hint ? (
        <p id={hintId} className="text-xs text-muted-foreground">
          {hint}
        </p>
      ) : null}
      {error ? (
        <p id={errorId} role="alert" className="text-xs text-destructive">
          {error}
        </p>
      ) : null}
    </div>
  );
}
