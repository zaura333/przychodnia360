import type { ReactNode } from 'react';

/** Wspólna ramka ekranów publicznych (logowanie, rejestracja, reset hasła). */
export function AuthShell({ title, children }: { title: string; children: ReactNode }) {
  return (
    <main className="mx-auto flex max-w-md flex-col gap-4 p-6">
      <h1 className="text-2xl font-semibold">{title}</h1>
      {children}
    </main>
  );
}
