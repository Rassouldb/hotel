import { zodResolver } from '@hookform/resolvers/zod'
import { Alert, Button, Link, Stack, TextField } from '@mui/material'
import { useState } from 'react'
import { useForm } from 'react-hook-form'
import { useTranslation } from 'react-i18next'
import { Navigate, Link as RouterLink, useLocation, useNavigate } from 'react-router'
import { z } from 'zod'

import { ApiError } from '../api/http'
import { ErrorAlert } from '../components/ErrorAlert'
import { PasswordField } from '../components/PasswordField'
import { useAuth } from './AuthContext'
import { AuthLayout } from './AuthLayout'
import { FullPageLoader } from './RequireAuth'

const schema = z.object({
  username: z.string().trim().min(1),
  password: z.string().min(1),
})

type LoginForm = z.infer<typeof schema>

function lockedUntil(error: unknown): string | null {
  if (!(error instanceof ApiError) || error.code !== 'ACCOUNT_LOCKED') {
    return null
  }
  const value = error.details.find((detail) => typeof detail.lockedUntil === 'string')?.lockedUntil as string | undefined
  return value ? new Intl.DateTimeFormat('fr-FR', { timeStyle: 'short' }).format(new Date(value)) : null
}

export function LoginPage() {
  const { t } = useTranslation()
  const { status, sessionEnd, login } = useAuth()
  const navigate = useNavigate()
  const location = useLocation()
  const [error, setError] = useState<unknown>(null)
  const { register, handleSubmit, formState } = useForm<LoginForm>({
    resolver: zodResolver(schema),
    defaultValues: { username: '', password: '' },
  })

  const requested = (location.state as { from?: string } | null)?.from
  const destination = requested && requested !== location.pathname ? requested : '/'

  if (status === 'loading') {
    return <FullPageLoader />
  }
  if (status === 'authenticated') {
    return <Navigate to={destination} replace />
  }

  const onSubmit = async (values: LoginForm) => {
    setError(null)
    try {
      await login(values.username, values.password)
      navigate(destination, { replace: true })
    } catch (cause) {
      setError(cause)
    }
  }

  const lockTime = lockedUntil(error)

  return (
    <AuthLayout title={t('auth.loginTitle')} subtitle={t('auth.loginSubtitle')}>
      <Stack component="form" spacing={2} noValidate onSubmit={handleSubmit(onSubmit)}>
        {sessionEnd === 'expired' && !error && <Alert severity="warning">{t('auth.sessionExpired')}</Alert>}
        {sessionEnd === 'logout' && !error && <Alert severity="info">{t('auth.loggedOut')}</Alert>}
        {lockTime ? <Alert severity="error">{t('auth.lockedUntil', { time: lockTime })}</Alert> : error ? <ErrorAlert error={error} /> : null}
        <TextField
          label={t('auth.username')}
          autoComplete="username"
          autoFocus
          required
          error={!!formState.errors.username}
          {...register('username')}
        />
        <PasswordField
          label={t('auth.password')}
          autoComplete="current-password"
          required
          error={!!formState.errors.password}
          {...register('password')}
        />
        <Button type="submit" variant="contained" size="large" disabled={formState.isSubmitting}>
          {t('auth.submit')}
        </Button>
        <Link component={RouterLink} to="/mot-de-passe-oublie" sx={{ alignSelf: 'center' }}>
          {t('auth.forgotLink')}
        </Link>
      </Stack>
    </AuthLayout>
  )
}
