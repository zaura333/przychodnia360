import { ContactForm } from '../components/ContactForm';
import { useMyPatient, useUpdateMyContact } from '../hooks';
import { formatLocalDate, parseLocalDate } from '@/shared/lib/datetime';
import { QueryState } from '@/shared/ui/query-state';
import { useNotify } from '@/shared/ui/toaster';

/** "Moje dane" pacjenta (FR-17): w 1a edytowalny jest tylko kontaktowy adres e-mail. */
export function MyProfilePage() {
  const patient = useMyPatient();
  const update = useUpdateMyContact();
  const notify = useNotify();
  return (
    <main className="mx-auto max-w-xl p-4">
      <h1 className="mb-4 text-2xl font-semibold">Moje dane</h1>
      <QueryState query={patient} isEmpty={() => false}>
        {(p) => (
          <div className="flex flex-col gap-4">
            <dl className="grid grid-cols-[auto_1fr] gap-x-4 gap-y-1 text-sm">
              <dt className="text-muted-foreground">Imię</dt>
              <dd>{p.firstName}</dd>
              <dt className="text-muted-foreground">Nazwisko</dt>
              <dd>{p.lastName}</dd>
              <dt className="text-muted-foreground">Data urodzenia</dt>
              <dd>{formatLocalDate(parseLocalDate(p.birthDate))}</dd>
            </dl>
            <p className="text-sm text-muted-foreground">Zmiana tych danych przez rejestrację.</p>
            <ContactForm
              initialEmail={p.contactEmail ?? ''}
              pending={update.isPending}
              error={update.error}
              onSubmit={(email) => {
                update.mutate(email, {
                  onSuccess: () => {
                    notify.success('Zapisano zmiany');
                  },
                });
              }}
            />
          </div>
        )}
      </QueryState>
    </main>
  );
}
