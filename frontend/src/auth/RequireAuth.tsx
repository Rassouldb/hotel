import { Box, CircularProgress } from '@mui/material'
import type { ReactNode } from 'react'
import { Navigate, useLocation } from 'react-router'

import { useAuth } from './AuthContext'

export const LOGIN_PATH = '/connexion'
export const FORCED_PASSWORD_CHANGE_PATH = '/changer-mot-de-passe'

export function FullPageLoader() {
  return (
    <Box sx={{ display: 'grid', placeItems: 'center', minHeight: '100vh' }}>
      <CircularProgress aria-label="Chargement" />
    </Box>
  )
}

/**
 * Protège les écrans du PMS : redirige vers la connexion si besoin, et vers le changement de mot de
 * passe tant qu'un mot de passe temporaire n'a pas été remplacé.
 */
export function RequireAuth({ children }: { children: ReactNode }) {
  const { status, profile } = useAuth()
  const location = useLocation()

  if (status === 'loading') {
    return <FullPageLoader />
  }
  if (status === 'anonymous' || !profile) {
    return <Navigate to={LOGIN_PATH} replace state={{ from: location.pathname }} />
  }
  if (profile.mustChangePassword) {
    return <Navigate to={FORCED_PASSWORD_CHANGE_PATH} replace />
  }
  return children
}
