import { ThemeProvider } from '@mui/material'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { createMemoryRouter, RouterProvider } from 'react-router'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

import { theme } from '../app/theme'
import { ActiveHotelProvider } from '../hotel/ActiveHotelContext'
import { ComingSoonPage } from '../pages/ComingSoonPage'
import { DashboardPage } from '../pages/DashboardPage'
import { AppLayout } from './AppLayout'
import { allNavItems } from './navigation'

const demoHotel = {
  id: 'h1',
  organizationId: 'o1',
  code: 'DKR01',
  name: 'Hôtel Démo Dakar',
  legalName: null,
  address: 'Corniche Ouest',
  phone: null,
  email: null,
  currencyCode: 'XOF',
  timezone: 'Africa/Dakar',
  language: 'fr',
  businessDate: '2026-10-01',
  status: 'ACTIVE',
}

function renderAt(path: string) {
  const router = createMemoryRouter(
    [
      {
        path: '/',
        element: <AppLayout />,
        children: [
          { index: true, element: <DashboardPage /> },
          ...allNavItems.filter((item) => item.availableIn !== 0)
            .map((item) => ({ path: item.path, element: <ComingSoonPage item={item} /> })),
        ],
      },
    ],
    { initialEntries: [path] },
  )
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  return render(
    <ThemeProvider theme={theme}>
      <QueryClientProvider client={queryClient}>
        <ActiveHotelProvider>
          <RouterProvider router={router} />
        </ActiveHotelProvider>
      </QueryClientProvider>
    </ThemeProvider>,
  )
}

beforeEach(() => {
  vi.stubGlobal('fetch', vi.fn((url: string) => {
    if (url.startsWith('/api/v1/hotels')) {
      return Promise.resolve(new Response(JSON.stringify({ success: true, data: [demoHotel] }), { status: 200 }))
    }
    return Promise.resolve(new Response(JSON.stringify({ status: 'UP' }), { status: 200 }))
  }))
})

afterEach(() => {
  vi.unstubAllGlobals()
})

describe('AppLayout', () => {
  it('shows the active hotel and its business date in the permanent banner', async () => {
    renderAt('/')

    expect(await screen.findByRole('heading', { name: 'Tableau de bord' })).toBeInTheDocument()
    expect(screen.getAllByText(/jeudi 1 octobre 2026/).length).toBeGreaterThan(0)
    expect(screen.getAllByText('Hôtel Démo Dakar').length).toBeGreaterThan(0)
  })

  it('lists the main menu entries of the specification', async () => {
    renderAt('/')

    // jsdom n'a pas de largeur d'écran : la mise en page mobile s'applique, le menu s'ouvre par le bouton.
    await userEvent.click(await screen.findByRole('button', { name: 'Ouvrir le menu' }))
    const menu = await screen.findByRole('navigation', { name: 'Menu principal' })
    for (const label of ['Réservations', 'Planning', 'Clients', 'Chambres', 'Facturation', 'Caisse', 'Clôture journalière']) {
      expect(menu).toHaveTextContent(label)
    }
  })

  it('tells which iteration delivers a module that is not built yet', async () => {
    renderAt('/planning')

    expect(await screen.findByText('Ce module sera livré à l’itération 5 du MVP.')).toBeInTheDocument()
  })
})
