import { useState } from 'react';
import { ContactForm } from '../components/ContactForm';
import { PatientForm } from '../components/PatientForm';
import { PatientSearch } from '../components/PatientSearch';
import { useUpdatePatientContact } from '../hooks';
import type { PatientResponse } from '@/shared/api/generated/model';
import { Button } from '@/shared/ui/button';
import { Dialog } from '@/shared/ui/dialog';
import { useNotify } from '@/shared/ui/toaster';

/** Pacjenci dla rejestratora (FR-50): wyszukiwanie, zakładanie i edycja danych kontaktowych. Bez danych medycznych. */
export function ReceptionPatientsPage() {
  const [creating, setCreating] = useState(false);
  const [editing, setEditing] = useState<PatientResponse | null>(null);
  const update = useUpdatePatientContact();
  const notify = useNotify();
  return (
    <main className="mx-auto max-w-3xl p-4">
      <div className="mb-4 flex items-center justify-between">
        <h1 className="text-2xl font-semibold">Pacjenci</h1>
        <Button
          onClick={() => {
            setCreating(true);
          }}
        >
          Nowy pacjent
        </Button>
      </div>
      <PatientSearch
        renderActions={(p) => (
          <Button
            variant="outline"
            aria-label={`Edytuj kontakt: ${p.firstName} ${p.lastName}`}
            onClick={() => {
              update.reset();
              setEditing(p);
            }}
          >
            Edytuj kontakt
          </Button>
        )}
      />
      <Dialog
        open={creating}
        onClose={() => {
          setCreating(false);
        }}
        title="Nowy pacjent"
      >
        {creating ? (
          <PatientForm
            onCreated={() => {
              notify.success('Dodano pacjenta');
              setCreating(false);
            }}
            onCancel={() => {
              setCreating(false);
            }}
          />
        ) : null}
      </Dialog>
      <Dialog
        open={editing !== null}
        onClose={() => {
          setEditing(null);
        }}
        title="Dane kontaktowe pacjenta"
      >
        {editing ? (
          <ContactForm
            initialEmail={editing.contactEmail ?? ''}
            pending={update.isPending}
            error={update.error}
            onCancel={() => {
              setEditing(null);
            }}
            onSubmit={(email) => {
              update.mutate(
                { id: editing.id, contactEmail: email },
                {
                  onSuccess: () => {
                    notify.success('Zapisano dane kontaktowe');
                    setEditing(null);
                  },
                },
              );
            }}
          />
        ) : null}
      </Dialog>
    </main>
  );
}
