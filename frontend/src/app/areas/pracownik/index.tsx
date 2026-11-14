import { Route, Routes } from 'react-router';
import { subpathsFor } from '@/app/navigation';
import { NotFoundPage, SoonPage } from '@/app/pages/pages';

/** Obszar "Panel pracownika": osobny chunk ładowany leniwie. W etapie 0 wszystkie ekrany to SoonPage. */
export function Component() {
  return (
    <Routes>
      <Route index element={<SoonPage title="Panel pracownika" />} />
      {subpathsFor('/pracownik').map((sub) => (
        <Route key={sub} path={sub} element={<SoonPage title="Panel pracownika" />} />
      ))}
      <Route path="*" element={<NotFoundPage />} />
    </Routes>
  );
}
