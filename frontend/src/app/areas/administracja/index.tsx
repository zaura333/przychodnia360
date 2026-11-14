import { Navigate, Route, Routes } from 'react-router';
import { RoleGate } from '@/app/RoleGate';
import { NotFoundPage } from '@/app/pages/pages';
import { AdminAppointmentsPage } from '@/features/appointments';
import { AdminServicesPage } from '@/features/catalog';
import { AdminEmployeesPage, EmployeeSchedulePage } from '@/features/staff';

/** Obszar administracji placówki: osobny chunk ładowany leniwie. Dostęp do tras określa ROUTE_TABLE. */
export function Component() {
  return (
    <Routes>
      <Route index element={<Navigate to="uslugi" replace />} />
      <Route
        path="uslugi"
        element={
          <RoleGate path="/administracja/uslugi">
            <AdminServicesPage />
          </RoleGate>
        }
      />
      <Route
        path="pracownicy"
        element={
          <RoleGate path="/administracja/pracownicy">
            <AdminEmployeesPage />
          </RoleGate>
        }
      />
      <Route
        path="pracownicy/:id/grafik"
        element={
          <RoleGate path="/administracja/pracownicy/:id/grafik">
            <EmployeeSchedulePage />
          </RoleGate>
        }
      />
      <Route
        path="wizyty"
        element={
          <RoleGate path="/administracja/wizyty">
            <AdminAppointmentsPage />
          </RoleGate>
        }
      />
      <Route path="*" element={<NotFoundPage />} />
    </Routes>
  );
}
