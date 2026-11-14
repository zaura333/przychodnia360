import { useTheme } from '@/app/providers/ThemeProvider';
import { Button } from '@/shared/ui/button';
import { Badge, Skeleton } from '@/shared/ui/display';
import { Input, Label, Select, Textarea } from '@/shared/ui/fields';
import { EmptyState } from '@/shared/ui/query-state';
import { useNotify } from '@/shared/ui/toaster';

/** Strona próbna (tylko import.meta.env.DEV): przegląd komponentów w obu motywach. */
export function Component() {
  const { preference, setPreference } = useTheme();
  const notify = useNotify();
  return (
    <main className="mx-auto flex max-w-3xl flex-col gap-6 p-8">
      <h1 className="text-2xl font-semibold">Przegląd komponentów (dev)</h1>
      <section aria-label="Motyw" className="flex gap-2">
        {(['light', 'dark', 'system'] as const).map((p) => (
          <Button
            key={p}
            variant={preference === p ? 'default' : 'outline'}
            onClick={() => {
              setPreference(p);
            }}
          >
            {p}
          </Button>
        ))}
      </section>
      <section aria-label="Przyciski" className="flex flex-wrap gap-2">
        <Button>Domyślny</Button>
        <Button variant="secondary">Drugorzędny</Button>
        <Button variant="destructive">Usuń</Button>
        <Button variant="outline">Obrys</Button>
        <Button variant="ghost">Duch</Button>
        <Button variant="link">Link</Button>
      </section>
      <section aria-label="Pola" className="flex flex-col gap-2">
        <Label htmlFor="kit-input">Pole tekstowe</Label>
        <Input id="kit-input" placeholder="Wpisz tekst" />
        <Label htmlFor="kit-select">Lista</Label>
        <Select id="kit-select">
          <option>Pierwsza</option>
          <option>Druga</option>
        </Select>
        <Label htmlFor="kit-text">Wielolinijkowe</Label>
        <Textarea id="kit-text" />
      </section>
      <section aria-label="Znaczniki" className="flex flex-wrap gap-2">
        <Badge>Domyślny</Badge>
        <Badge variant="success">Sukces</Badge>
        <Badge variant="warning">Ostrzeżenie</Badge>
        <Badge variant="info">Informacja</Badge>
        <Badge variant="destructive">Błąd</Badge>
      </section>
      <section aria-label="Stany" className="flex flex-col gap-2">
        <Skeleton className="h-6 w-1/2" />
        <EmptyState title="Brak danych" hint="Tak wygląda stan pusty." />
        <Button
          onClick={() => {
            notify.success('Zapisano zmiany');
          }}
        >
          Pokaż powiadomienie
        </Button>
      </section>
    </main>
  );
}
