import { zodResolver } from '@hookform/resolvers/zod';
import { useState } from 'react';
import { useForm } from 'react-hook-form';
import { useCategories, useDeleteCategory, useSaveCategory } from '../hooks';
import { categorySchema } from '../schemas';
import type { CategoryResponse } from '@/shared/api/generated/model';
import { Button } from '@/shared/ui/button';
import { AlertDialog } from '@/shared/ui/dialog';
import { Input } from '@/shared/ui/fields';
import { FormField } from '@/shared/ui/form';
import { ErrorMessage, QueryState } from '@/shared/ui/query-state';
import { useNotify } from '@/shared/ui/toaster';

function CategoryForm({
  category,
  onDone,
}: {
  category?: CategoryResponse | undefined;
  onDone?: () => void;
}) {
  const save = useSaveCategory();
  const notify = useNotify();
  const { register, handleSubmit, formState, reset } = useForm<{ name: string }>({
    resolver: zodResolver(categorySchema),
    defaultValues: { name: category?.name ?? '' },
  });
  return (
    <form
      className="flex flex-wrap items-end gap-2"
      noValidate
      onSubmit={(e) => {
        void handleSubmit((v) => {
          save.mutate(
            { id: category?.id, name: v.name },
            {
              onSuccess: () => {
                notify.success(category ? 'Zmieniono nazwę kategorii' : 'Dodano kategorię');
                reset({ name: category ? v.name : '' });
                onDone?.();
              },
            },
          );
        })(e);
      }}
    >
      <FormField
        label={category ? `Nazwa kategorii „${category.name}”` : 'Nowa kategoria'}
        error={formState.errors.name?.message}
      >
        <Input {...register('name')} />
      </FormField>
      <Button type="submit" disabled={save.isPending}>
        {category ? 'Zmień nazwę' : 'Dodaj kategorię'}
      </Button>
      {onDone ? (
        <Button variant="outline" onClick={onDone}>
          Anuluj
        </Button>
      ) : null}
      {save.isError ? <ErrorMessage error={save.error} /> : null}
    </form>
  );
}

/** Dodawanie, zmiana nazwy i usuwanie kategorii usług (FR-57). Usunięcie kategorii w użyciu odrzuca backend. */
export function CategoryManager() {
  const categories = useCategories();
  const remove = useDeleteCategory();
  const notify = useNotify();
  const [editing, setEditing] = useState<string | null>(null);
  const [deleting, setDeleting] = useState<CategoryResponse | null>(null);

  return (
    <section aria-labelledby="categories-heading" className="mt-8">
      <h2 id="categories-heading" className="mb-3 text-xl font-semibold">
        Kategorie
      </h2>
      <QueryState
        query={categories}
        emptyTitle="Brak kategorii"
        emptyHint="Dodaj pierwszą kategorię, żeby móc dodawać usługi."
      >
        {(data) => (
          <ul className="mb-4 flex flex-col gap-2">
            {data.map((c) => (
              <li key={c.id} className="flex flex-wrap items-center gap-2 rounded-md border p-2">
                {editing === c.id ? (
                  <CategoryForm
                    category={c}
                    onDone={() => {
                      setEditing(null);
                    }}
                  />
                ) : (
                  <>
                    <span className="flex-1">{c.name}</span>
                    <Button
                      variant="outline"
                      aria-label={`Zmień nazwę kategorii ${c.name}`}
                      onClick={() => {
                        setEditing(c.id);
                      }}
                    >
                      Zmień nazwę
                    </Button>
                    <Button
                      variant="outline"
                      aria-label={`Usuń kategorię ${c.name}`}
                      onClick={() => {
                        remove.reset();
                        setDeleting(c);
                      }}
                    >
                      Usuń
                    </Button>
                  </>
                )}
              </li>
            ))}
          </ul>
        )}
      </QueryState>
      <CategoryForm />
      {remove.isError && !deleting ? <ErrorMessage error={remove.error} /> : null}
      <AlertDialog
        open={deleting !== null}
        title="Usunąć kategorię?"
        description={`Kategoria „${deleting?.name ?? ''}” zostanie usunięta. Kategorii z usługami nie da się usunąć.`}
        confirmLabel="Usuń kategorię"
        cancelLabel="Zostaw"
        destructive
        onCancel={() => {
          setDeleting(null);
        }}
        onConfirm={() => {
          if (!deleting) return;
          remove.mutate(deleting.id, {
            onSuccess: () => {
              notify.success('Usunięto kategorię');
            },
          });
          setDeleting(null);
        }}
      />
    </section>
  );
}
