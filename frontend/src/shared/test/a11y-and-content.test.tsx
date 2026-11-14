import { render, screen } from '@testing-library/react';
import { describe, expect, it } from 'vitest';
import { expectNoA11yViolations } from './a11y';
import { renderApp } from './render';
import { createTestRouter } from '@/app/router';
import { Button } from '@/shared/ui/button';
import { DemoBanner } from '@/shared/ui/demo-banner';
import { Input } from '@/shared/ui/fields';
import { FormField } from '@/shared/ui/form';

describe('dostępność (axe)', () => {
  it('login page has no violations', async () => {
    const { container } = renderApp(createTestRouter(['/logowanie']), 'anonymous');
    await screen.findByRole('heading', { name: 'Logowanie' });
    await expectNoA11yViolations(container);
  });

  it('404 page has no violations', async () => {
    const { container } = renderApp(createTestRouter(['/nie-ma']), 'anonymous');
    await screen.findByRole('heading', { name: 'Nie znaleziono strony' });
    await expectNoA11yViolations(container);
  });

  it('area page for a logged-in user has no violations', async () => {
    const { container } = renderApp(createTestRouter(['/pracownik']), 'RECEPTIONIST');
    await screen.findByRole('heading', { name: 'Panel pracownika' });
    await expectNoA11yViolations(container);
  });

  it('banner and form components have no violations', async () => {
    const { container } = render(
      <main>
        <DemoBanner />
        <FormField label="E-mail" error="Niepoprawny adres">
          <Input />
        </FormField>
        <Button>Zapisz</Button>
      </main>,
    );
    await expectNoA11yViolations(container);
  });
});

describe('bezpieczeństwo treści (wzorzec dla wpisów medycznych w 1b)', () => {
  const payload = '<script>window.__pwned = 1</script><img src=x onerror="window.__pwned = 2">';

  it('renders user text as text, never as markup', () => {
    const { container } = render(<p data-testid="entry">{payload}</p>);
    expect(screen.getByTestId('entry').textContent).toBe(payload);
    expect(container.querySelector('script')).toBeNull();
    expect(container.querySelector('img')).toBeNull();
    expect((window as unknown as { __pwned?: number }).__pwned).toBeUndefined();
  });
});
