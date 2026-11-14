import { Navigate, Route, Routes } from 'react-router';
import { RoleGate } from '@/app/RoleGate';
import { NotFoundPage, SoonPage } from '@/app/pages/pages';
import { DoctorDayPage } from '@/features/appointments';
import { ReceptionPatientsPage } from '@/features/patients';
import { MySchedulePage } from '@/features/staff';
import { ServicesPage } from '@/features/catalog';
import { useAuth } from '@/shared/api/session';

/** Strona startowa obszaru pracownika zależna od roli. */
function StaffHome() {
  const { user } = useAuth();
  if (user?.role === 'RECEPTIONIST') return <Navigate to="terminarz" replace />;
  if (user?.role === 'DOCTOR') {
    return (
      <RoleGate path="/pracownik">
        <DoctorDayPage />
      </RoleGate>
    );
  }
  return (
    <RoleGate path="/pracownik">
      <SoonPage title="Funkcja będzie dostępna wkrótce" />
    </RoleGate>
  );
}

/** Obszar pracownika: osobny chunk ładowany leniwie. Dostęp do tras określa ROUTE_TABLE. */
export function Component() {
  return (
    <Routes>
      <Route index element={<StaffHome />} />
      <Route
        path="grafik"
        element={
          <RoleGate path="/pracownik/grafik">
            <MySchedulePage />
          </RoleGate>
        }
      />
      <Route
        path="terminarz"
        element={
          <RoleGate path="/pracownik/terminarz">
            <SoonPage title="Terminarz" />
          </RoleGate>
        }
      />
      <Route
        path="pacjenci"
        element={
          <RoleGate path="/pracownik/pacjenci">
            <ReceptionPatientsPage />
          </RoleGate>
        }
      />
      <Route
        path="uslugi"
        element={
          <RoleGate path="/pracownik/uslugi">
            <ServicesPage />
          </RoleGate>
        }
      />
      <Route path="*" element={<NotFoundPage />} />
    </Routes>
  );
}
