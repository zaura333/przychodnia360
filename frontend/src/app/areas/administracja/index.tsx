import { Route, Routes } from 'react-router';
import { subpathsFor } from '@/app/navigation';
import { NotFoundPage, SoonPage } from '@/app/pages/pages';

/** Obszar "Administracja placówki": osobny chunk ładowany leniwie. W etapie 0 wszystkie ekrany to SoonPage. */
export function Component() {
  return (
    <Routes>
      <Route index element={<SoonPage title="Administracja placówki" />} />
      {subpathsFor('/administracja').map((sub) => (
        <Route key={sub} path={sub} element={<SoonPage title="Administracja placówki" />} />
      ))}
      <Route path="*" element={<NotFoundPage />} />
    </Routes>
  );
}
