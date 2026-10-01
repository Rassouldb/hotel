import { createBrowserRouter } from 'react-router'

import { AdministrationPage } from '../admin/AdministrationPage'
import { ForcedPasswordChangePage } from '../auth/ForcedPasswordChangePage'
import { ForgotPasswordPage } from '../auth/ForgotPasswordPage'
import { LoginPage } from '../auth/LoginPage'
import { RequireAuth } from '../auth/RequireAuth'
import { ResetPasswordPage } from '../auth/ResetPasswordPage'
import { ActiveHotelProvider } from '../hotel/ActiveHotelContext'
import { AppLayout } from '../layout/AppLayout'
import { allNavItems, isDelivered } from '../layout/navigation'
import { ComingSoonPage } from '../pages/ComingSoonPage'
import { DashboardPage } from '../pages/DashboardPage'
import { NotFoundPage } from '../pages/NotFoundPage'
import { ProfilePage } from '../profile/ProfilePage'

const comingSoonRoutes = allNavItems
  .filter((item) => !isDelivered(item))
  .map((item) => ({ path: item.path, element: <ComingSoonPage item={item} /> }))

export const routes = [
  { path: '/connexion', element: <LoginPage /> },
  { path: '/mot-de-passe-oublie', element: <ForgotPasswordPage /> },
  { path: '/reinitialisation-mot-de-passe', element: <ResetPasswordPage /> },
  { path: '/changer-mot-de-passe', element: <ForcedPasswordChangePage /> },
  {
    path: '/',
    element: (
      <RequireAuth>
        <ActiveHotelProvider>
          <AppLayout />
        </ActiveHotelProvider>
      </RequireAuth>
    ),
    children: [
      { index: true, element: <DashboardPage /> },
      { path: 'profil', element: <ProfilePage /> },
      { path: 'administration/:tab?', element: <AdministrationPage /> },
      ...comingSoonRoutes,
      { path: '*', element: <NotFoundPage /> },
    ],
  },
]

export const router = createBrowserRouter(routes)
