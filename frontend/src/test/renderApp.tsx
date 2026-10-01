import { ThemeProvider } from '@mui/material'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render } from '@testing-library/react'
import { createMemoryRouter, RouterProvider } from 'react-router'

import { setAccessToken } from '../api/http'
import { routes } from '../app/router'
import { theme } from '../app/theme'
import { AuthProvider } from '../auth/AuthContext'

/** Monte l'application complète (routes réelles) à l'adresse donnée. */
export function renderApp(path: string) {
  setAccessToken(null)
  const router = createMemoryRouter(routes, { initialEntries: [path] })
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  const view = render(
    <ThemeProvider theme={theme}>
      <QueryClientProvider client={queryClient}>
        <AuthProvider>
          <RouterProvider router={router} />
        </AuthProvider>
      </QueryClientProvider>
    </ThemeProvider>,
  )
  return { ...view, router }
}
