import { useEffect, useId, useRef, type ReactNode } from 'react';
import { Button } from '@/shared/ui/button';

interface DialogProps {
  open: boolean;
  onClose: () => void;
  title: string;
  description?: string;
  children?: ReactNode;
  /** `alertdialog` dla potwierdzeń operacji nieodwracalnych. */
  role?: 'dialog' | 'alertdialog';
}

/**
 * Okno modalne na natywnym <dialog>: pułapka fokusu, Escape i tło dostarcza przeglądarka,
 * bez wstrzykiwania stylów w czasie działania (zgodne z CSP style-src 'self').
 */
export function Dialog({
  open,
  onClose,
  title,
  description,
  children,
  role = 'dialog',
}: DialogProps) {
  const ref = useRef<HTMLDialogElement>(null);
  const titleId = useId();
  const descId = useId();

  useEffect(() => {
    const el = ref.current;
    if (!el) return;
    if (open && !el.open) el.showModal();
    if (!open && el.open) el.close();
  }, [open]);

  return (
    <dialog
      ref={ref}
      role={role}
      aria-labelledby={titleId}
      aria-describedby={description ? descId : undefined}
      onClose={onClose}
      className="m-auto w-full max-w-md rounded-lg border border-border bg-background p-6 text-foreground backdrop:bg-black/50"
    >
      <h2 id={titleId} className="text-lg font-semibold">
        {title}
      </h2>
      {description ? (
        <p id={descId} className="mt-2 text-sm text-muted-foreground">
          {description}
        </p>
      ) : null}
      <div className="mt-4">{children}</div>
    </dialog>
  );
}

interface AlertDialogProps {
  open: boolean;
  title: string;
  description: string;
  confirmLabel?: string;
  cancelLabel?: string;
  destructive?: boolean;
  onConfirm: () => void;
  onCancel: () => void;
}

export function AlertDialog({
  open,
  title,
  description,
  confirmLabel = 'Potwierdź',
  cancelLabel = 'Anuluj',
  destructive = false,
  onConfirm,
  onCancel,
}: AlertDialogProps) {
  return (
    <Dialog
      open={open}
      onClose={onCancel}
      title={title}
      description={description}
      role="alertdialog"
    >
      <div className="flex justify-end gap-2">
        <Button variant="outline" onClick={onCancel}>
          {cancelLabel}
        </Button>
        <Button variant={destructive ? 'destructive' : 'default'} onClick={onConfirm}>
          {confirmLabel}
        </Button>
      </div>
    </Dialog>
  );
}
