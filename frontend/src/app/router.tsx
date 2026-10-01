import { createBrowserRouter } from 'react-router'

import { AppLayout } from '../layout/AppLayout'
import { allNavItems } from '../layout/navigation'
import { ComingSoonPage } from '../pages/ComingSoonPage'
import { DashboardPage } from '../pages/DashboardPage'
import { NotFoundPage } from '../pages/NotFoundPage'

const comingSoonRoutes = allNavItems
  .filter((item) => item.availableIn !== 0)
  .map((item) => ({ path: item.path, element: <ComingSoonPage item={item} /> }))

export const router = createBrowserRouter([
  {
    path: '/',
    element: <AppLayout />,
    children: [
      { index: true, element: <DashboardPage /> },
      ...comingSoonRoutes,
      { path: '*', element: <NotFoundPage /> },
    ],
  },
])
