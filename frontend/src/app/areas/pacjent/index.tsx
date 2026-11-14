import { Navigate, Route, Routes } from 'react-router';
import { RoleGate } from '@/app/RoleGate';
import { NotFoundPage } from '@/app/pages/pages';
import { BookAppointmentPage, MyAppointmentsPage } from '@/features/appointments';
import { MyProfilePage } from '@/features/patients';
import { DoctorsPage } from '@/features/staff';
import { ServiceDetailsPage, ServicesPage } from '@/features/catalog';

/** Obszar pacjenta: osobny chunk ładowany leniwie. Dostęp do tras określa ROUTE_TABLE. */
export function Component() {
  return (
    <Routes>
      <Route index element={<Navigate to="wizyty" replace />} />
      <Route
        path="wizyty"
        element={
          <RoleGate path="/pacjent/wizyty">
            <MyAppointmentsPage />
          </RoleGate>
        }
      />
      <Route
        path="rezerwacja"
        element={
          <RoleGate path="/pacjent/rezerwacja">
            <BookAppointmentPage />
          </RoleGate>
        }
      />
      <Route
        path="uslugi"
        element={
          <RoleGate path="/pacjent/uslugi">
            <ServicesPage />
          </RoleGate>
        }
      />
      <Route
        path="uslugi/:id"
        element={
          <RoleGate path="/pacjent/uslugi/:id">
            <ServiceDetailsPage />
          </RoleGate>
        }
      />
      <Route
        path="lekarze"
        element={
          <RoleGate path="/pacjent/lekarze">
            <DoctorsPage />
          </RoleGate>
        }
      />
      <Route
        path="moje-dane"
        element={
          <RoleGate path="/pacjent/moje-dane">
            <MyProfilePage />
          </RoleGate>
        }
      />
      <Route path="*" element={<NotFoundPage />} />
    </Routes>
  );
}
