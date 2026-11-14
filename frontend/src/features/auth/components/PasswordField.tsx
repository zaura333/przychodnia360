import { useState, type InputHTMLAttributes, type Ref } from 'react';
import { Button } from '@/shared/ui/button';
import { Input } from '@/shared/ui/fields';

/** Pole hasła z podglądem na żądanie (do użycia przez FormField, które nadaje id i aria-*). */
export function PasswordField({
  ref,
  ...props
}: InputHTMLAttributes<HTMLInputElement> & { ref?: Ref<HTMLInputElement> | undefined }) {
  const [visible, setVisible] = useState(false);
  return (
    <div className="flex gap-2">
      <Input ref={ref} type={visible ? 'text' : 'password'} {...props} />
      <Button
        variant="outline"
        aria-pressed={visible}
        onClick={() => {
          setVisible((v) => !v);
        }}
      >
        {visible ? 'Ukryj' : 'Pokaż'}
      </Button>
    </div>
  );
}
