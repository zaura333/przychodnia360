import { Navigate, Route, Routes } from 'react-router';
import { RoleGate } from '@/app/RoleGate';
import { NotFoundPage, SoonPage } from '@/app/pages/pages';

/** Obszar administracji placówki: osobny chunk ładowany leniwie. Dostęp do tras określa ROUTE_TABLE. */
export function Component() {
  return (
    <Routes>
      <Route index element={<Navigate to="uslugi" replace />} />
      <Route
        path="uslugi"
        element={
          <RoleGate path="/administracja/uslugi">
            <SoonPage title="Usługi" />
          </RoleGate>
        }
      />
      <Route
        path="pracownicy"
        element={
          <RoleGate path="/administracja/pracownicy">
            <SoonPage title="Pracownicy" />
          </RoleGate>
        }
      />
      <Route
        path="pracownicy/:id/grafik"
        element={
          <RoleGate path="/administracja/pracownicy/:id/grafik">
            <SoonPage title="Grafik pracownika" />
          </RoleGate>
        }
      />
      <Route
        path="wizyty"
        element={
          <RoleGate path="/administracja/wizyty">
            <SoonPage title="Wizyty" />
          </RoleGate>
        }
      />
      <Route path="*" element={<NotFoundPage />} />
    </Routes>
  );
}
