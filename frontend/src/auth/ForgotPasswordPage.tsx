import { Alert, Button, Link, Stack, TextField } from '@mui/material'
import { useState } from 'react'
import { useForm } from 'react-hook-form'
import { useTranslation } from 'react-i18next'
import { Link as RouterLink } from 'react-router'

import * as authApi from '../api/auth'
import { ErrorAlert } from '../components/ErrorAlert'
import { AuthLayout } from './AuthLayout'
import { LOGIN_PATH } from './RequireAuth'

/** Demande d'un lien de réinitialisation (US-AUTH-05). La réponse est la même que le compte existe ou non. */
export function ForgotPasswordPage() {
  const { t } = useTranslation()
  const [message, setMessage] = useState<string | null>(null)
  const [error, setError] = useState<unknown>(null)
  const { register, handleSubmit, formState } = useForm<{ login: string }>({ defaultValues: { login: '' } })

  const onSubmit = async ({ login }: { login: string }) => {
    setError(null)
    try {
      setMessage(await authApi.requestPasswordReset(login.trim()))
    } catch (cause) {
      setError(cause)
    }
  }

  return (
    <AuthLayout title={t('auth.forgotTitle')} subtitle={t('auth.forgotSubtitle')}>
      <Stack component="form" spacing={2} noValidate onSubmit={handleSubmit(onSubmit)}>
        {message && <Alert severity="success">{message}</Alert>}
        {error ? <ErrorAlert error={error} /> : null}
        <TextField
          label={t('auth.forgotLogin')}
          autoComplete="username"
          autoFocus
          required
          error={!!formState.errors.login}
          {...register('login', { required: true })}
        />
        <Button type="submit" variant="contained" disabled={formState.isSubmitting}>{t('auth.forgotSubmit')}</Button>
        <Alert severity="info" variant="outlined">{t('auth.forgotNoEmail')}</Alert>
        <Link component={RouterLink} to={LOGIN_PATH} sx={{ alignSelf: 'center' }}>{t('auth.backToLogin')}</Link>
      </Stack>
    </AuthLayout>
  )
}
