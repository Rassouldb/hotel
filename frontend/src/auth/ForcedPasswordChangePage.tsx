import { Button } from '@mui/material'
import { useTranslation } from 'react-i18next'
import { Navigate, useNavigate } from 'react-router'

import { useAuth } from './AuthContext'
import { AuthLayout } from './AuthLayout'
import { ChangePasswordForm } from './ChangePasswordForm'
import { FullPageLoader, LOGIN_PATH } from './RequireAuth'

/** Remplacement obligatoire du mot de passe temporaire à la première connexion. */
export function ForcedPasswordChangePage() {
  const { t } = useTranslation()
  const { status, profile, logout } = useAuth()
  const navigate = useNavigate()

  if (status === 'loading') {
    return <FullPageLoader />
  }
  if (status === 'anonymous' || !profile) {
    return <Navigate to={LOGIN_PATH} replace />
  }
  if (!profile.mustChangePassword) {
    return <Navigate to="/" replace />
  }

  return (
    <AuthLayout title={t('auth.forcedTitle')} subtitle={t('auth.forcedSubtitle')}>
      <ChangePasswordForm onChanged={() => navigate('/', { replace: true })} />
      <Button onClick={() => void logout()} sx={{ mt: 2 }}>{t('userMenu.logout')}</Button>
    </AuthLayout>
  )
}
