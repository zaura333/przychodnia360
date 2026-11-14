import { Navigate, Route, Routes } from 'react-router';
import { RoleGate } from '@/app/RoleGate';
import { NotFoundPage } from '@/app/pages/pages';
import { TenantsPage } from '@/features/platform';

/** Obszar administracji platformy: osobny chunk ładowany leniwie. Dostęp do tras określa ROUTE_TABLE. */
export function Component() {
  return (
    <Routes>
      <Route index element={<Navigate to="placowki" replace />} />
      <Route
        path="placowki"
        element={
          <RoleGate path="/platforma/placowki">
            <TenantsPage />
          </RoleGate>
        }
      />
      <Route path="*" element={<NotFoundPage />} />
    </Routes>
  );
}
