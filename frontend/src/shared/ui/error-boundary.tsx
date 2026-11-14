import { Component, type ErrorInfo, type ReactNode } from 'react';
import { Button } from '@/shared/ui/button';

interface Props {
  children: ReactNode;
  /** Poziom używany tylko w komunikacie: aplikacja lub obszar. */
  level?: 'app' | 'area';
}

interface State {
  errorId: string | null;
}

/**
 * Komunikat po polsku z identyfikatorem do zgłoszenia, bez stosu wywołań i bez danych użytkownika.
 * Treść błędu celowo nie jest wyświetlana ani logowana na konsolę.
 */
export class ErrorBoundary extends Component<Props, State> {
  override state: State = { errorId: null };

  static getDerivedStateFromError(): State {
    return { errorId: crypto.randomUUID().slice(0, 8) };
  }

  override componentDidCatch(_error: Error, _info: ErrorInfo): void {
    // Miejsce na przyszłe raportowanie (bez danych użytkownika); na razie nic nie wypisujemy.
  }

  override render(): ReactNode {
    const { errorId } = this.state;
    if (errorId === null) return this.props.children;
    return (
      <div role="alert" className="mx-auto max-w-lg p-8">
        <h1 className="text-xl font-semibold">Coś poszło nie tak</h1>
        <p className="mt-2 text-sm">
          {this.props.level === 'area'
            ? 'Ten obszar aplikacji napotkał błąd.'
            : 'Aplikacja napotkała nieoczekiwany błąd.'}{' '}
          Podaj identyfikator, jeśli zgłaszasz problem.
        </p>
        <p className="mt-2 text-sm text-muted-foreground">Identyfikator: {errorId}</p>
        <Button asChild className="mt-4">
          <a href="/">Wróć do strony głównej</a>
        </Button>
      </div>
    );
  }
}
