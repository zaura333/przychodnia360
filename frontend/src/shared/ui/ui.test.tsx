import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it, vi } from 'vitest';
import { Button } from '@/shared/ui/button';
import { AlertDialog } from '@/shared/ui/dialog';
import { Badge, Skeleton } from '@/shared/ui/display';
import { Input } from '@/shared/ui/fields';
import { FormField } from '@/shared/ui/form';

describe('shared/ui', () => {
  it('Button defaults to type=button and fires onClick', async () => {
    const onClick = vi.fn();
    render(<Button onClick={onClick}>Zapisz</Button>);
    const button = screen.getByRole('button', { name: 'Zapisz' });
    expect(button).toHaveAttribute('type', 'button');
    await userEvent.click(button);
    expect(onClick).toHaveBeenCalledOnce();
  });

  it('FormField links label, error and control for assistive technology', () => {
    render(
      <FormField label="E-mail" error="Niepoprawny adres">
        <Input />
      </FormField>,
    );
    const input = screen.getByLabelText('E-mail');
    expect(input).toHaveAttribute('aria-invalid', 'true');
    expect(input).toHaveAccessibleDescription('Niepoprawny adres');
    expect(screen.getByRole('alert')).toHaveTextContent('Niepoprawny adres');
  });

  it('AlertDialog opens as a modal and reports the choice', async () => {
    const onConfirm = vi.fn();
    const onCancel = vi.fn();
    render(
      <AlertDialog
        open
        title="Usunąć wizytę?"
        description="Tej operacji nie można cofnąć."
        destructive
        onConfirm={onConfirm}
        onCancel={onCancel}
      />,
    );
    const dialog = screen.getByRole('alertdialog', { name: 'Usunąć wizytę?' });
    expect(dialog).toHaveAttribute('open');
    await userEvent.click(screen.getByRole('button', { name: 'Potwierdź' }));
    expect(onConfirm).toHaveBeenCalledOnce();
  });

  it('Badge and Skeleton render', () => {
    render(
      <>
        <Badge variant="success">Aktywny</Badge>
        <Skeleton data-testid="skeleton" />
      </>,
    );
    expect(screen.getByText('Aktywny')).toBeInTheDocument();
    expect(screen.getByTestId('skeleton')).toHaveAttribute('aria-hidden', 'true');
  });
});
