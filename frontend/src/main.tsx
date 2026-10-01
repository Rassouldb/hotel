import { CssBaseline, ThemeProvider } from '@mui/material'
import { QueryClientProvider } from '@tanstack/react-query'
import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import { RouterProvider } from 'react-router'

import './i18n'
import { createQueryClient } from './app/queryClient'
import { router } from './app/router'
import { theme } from './app/theme'
import { ActiveHotelProvider } from './hotel/ActiveHotelContext'

const queryClient = createQueryClient()

createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <ThemeProvider theme={theme}>
      <CssBaseline />
      <QueryClientProvider client={queryClient}>
        <ActiveHotelProvider>
          <RouterProvider router={router} />
        </ActiveHotelProvider>
      </QueryClientProvider>
    </ThemeProvider>
  </StrictMode>,
)
