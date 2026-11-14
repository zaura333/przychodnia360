import { createBrowserRouter, createMemoryRouter, Navigate, type RouteObject } from 'react-router';
import {
  AppLayout,
  AreaLayout,
  PublicLayout,
  RequireRole,
  RootLayout,
} from '@/app/layouts/layouts';
import { ErrorPage, NotFoundPage, StartupScreen } from '@/app/pages/pages';
import {
  ForgotPasswordPage,
  LoginPage,
  PlatformLoginPage,
  RegisterPage,
  ResetPasswordPage,
} from '@/features/auth';
import { defaultAreaFor } from '@/app/areas';
import { useAuth } from '@/shared/api/session';

/** Strona główna kieruje według stanu sesji: obszar roli albo logowanie. */
function HomeRedirect() {
  const { status, user } = useAuth();
  if (status === 'loading') return <StartupScreen />;
  return <Navigate to={user ? defaultAreaFor(user.role) : '/logowanie'} replace />;
}

/**
 * Każdy obszar jest ładowany leniwie (osobny chunk), więc pacjent nie pobiera kodu paneli
 * administracyjnych. Test manifestu w check:bundle pilnuje, żeby wejście nie importowało ich statycznie.
 */
export const appRoutes: RouteObject[] = [
  {
    element: <RootLayout />,
    children: [
      {
        // errorElement na poziomie pod RootLayout: baner demo zostaje widoczny przy błędzie trasy
        errorElement: <ErrorPage />,
        children: [
          { path: '/', element: <HomeRedirect /> },
          {
            element: <PublicLayout />,
            children: [
              { path: '/logowanie', element: <LoginPage /> },
              { path: '/rejestracja', element: <RegisterPage /> },
              { path: '/nie-pamietam-hasla', element: <ForgotPasswordPage /> },
              { path: '/reset-hasla', element: <ResetPasswordPage /> },
              { path: '/platforma/logowanie', element: <PlatformLoginPage /> },
            ],
          },
          {
            element: <RequireRole />,
            children: [
              {
                element: <AppLayout />,
                children: [
                  {
                    element: <AreaLayout />,
                    children: [
                      { path: '/pacjent/*', lazy: () => import('@/app/areas/pacjent') },
                      { path: '/pracownik/*', lazy: () => import('@/app/areas/pracownik') },
                      { path: '/administracja/*', lazy: () => import('@/app/areas/administracja') },
                      { path: '/platforma/*', lazy: () => import('@/app/areas/platforma') },
                    ],
                  },
                ],
              },
            ],
          },
          // Strony deweloperskie i pomiarowe: tylko dev oraz build z VITE_BENCH=true (nie trafiają do produkcji)
          ...(import.meta.env.DEV || import.meta.env.VITE_BENCH === 'true'
            ? [
                { path: '/dev/kit', lazy: () => import('@/app/pages/DevKitPage') },
                { path: '/dev/schedule', lazy: () => import('@/app/pages/DevSchedulePage') },
              ]
            : []),
          { path: '*', element: <NotFoundPage /> },
        ],
      },
    ],
  },
];

export const createAppRouter = () => createBrowserRouter(appRoutes);
export const createTestRouter = (initialEntries: string[]) =>
  createMemoryRouter(appRoutes, { initialEntries });
