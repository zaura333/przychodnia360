import type {
  InputHTMLAttributes,
  LabelHTMLAttributes,
  Ref,
  SelectHTMLAttributes,
  TextareaHTMLAttributes,
} from 'react';
import { cn } from '@/shared/lib/cn';

const field =
  'touch-target w-full rounded-md border border-input bg-background px-3 text-sm text-foreground placeholder:text-muted-foreground disabled:opacity-50 aria-[invalid=true]:border-destructive';

export function Input({
  className,
  ref,
  ...props
}: InputHTMLAttributes<HTMLInputElement> & { ref?: Ref<HTMLInputElement> }) {
  return <input ref={ref} className={cn(field, className)} {...props} />;
}

export function Textarea({
  className,
  ref,
  ...props
}: TextareaHTMLAttributes<HTMLTextAreaElement> & { ref?: Ref<HTMLTextAreaElement> }) {
  return <textarea ref={ref} className={cn(field, 'min-h-24 py-2', className)} {...props} />;
}

/** Natywny select: komponenty bezgłowe (Radix Select) wstrzykują style i łamią CSP. */
export function Select({
  className,
  ref,
  ...props
}: SelectHTMLAttributes<HTMLSelectElement> & { ref?: Ref<HTMLSelectElement> }) {
  return <select ref={ref} className={cn(field, className)} {...props} />;
}

export function Label({ className, ...props }: LabelHTMLAttributes<HTMLLabelElement>) {
  // Etykieta zawsze jest powiązana z polem przez htmlFor lub zagnieżdżenie (przekazywane w props).
  // eslint-disable-next-line jsx-a11y/label-has-associated-control
  return <label className={cn('text-sm font-medium', className)} {...props} />;
}
