import { zodResolver } from '@hookform/resolvers/zod'
import { Alert, Button, Link, Stack } from '@mui/material'
import { useState } from 'react'
import { useForm } from 'react-hook-form'
import { useTranslation } from 'react-i18next'
import { Link as RouterLink, useSearchParams } from 'react-router'
import { z } from 'zod'

import * as authApi from '../api/auth'
import { fieldErrors } from '../api/http'
import { ErrorAlert } from '../components/ErrorAlert'
import { PasswordField } from '../components/PasswordField'
import { AuthLayout } from './AuthLayout'
import { LOGIN_PATH } from './RequireAuth'

/** Choix d'un nouveau mot de passe depuis le lien reçu par email (US-AUTH-05). */
export function ResetPasswordPage() {
  const { t } = useTranslation()
  const [params] = useSearchParams()
  const token = params.get('token')
  const [done, setDone] = useState(false)
  const [error, setError] = useState<unknown>(null)

  const schema = z.object({
    newPassword: z.string().min(10, t('password.tooShort')),
    confirmation: z.string(),
  }).refine((values) => values.newPassword === values.confirmation, { path: ['confirmation'], message: t('password.mismatch') })
  type Form = z.infer<typeof schema>
  const { register, handleSubmit, formState } = useForm<Form>({
    resolver: zodResolver(schema),
    defaultValues: { newPassword: '', confirmation: '' },
  })

  const onSubmit = async (values: Form) => {
    setError(null)
    try {
      await authApi.resetPassword(token ?? '', values.newPassword)
      setDone(true)
    } catch (cause) {
      setError(cause)
    }
  }

  const serverErrors = fieldErrors(error)

  return (
    <AuthLayout title={t('auth.resetTitle')}>
      {!token ? (
        <Alert severity="error">{t('auth.resetMissingToken')}</Alert>
      ) : done ? (
        <Alert severity="success">{t('auth.resetDone')}</Alert>
      ) : (
        <Stack component="form" spacing={2} noValidate onSubmit={handleSubmit(onSubmit)}>
          {error && !serverErrors.newPassword ? <ErrorAlert error={error} /> : null}
          <PasswordField
            label={t('password.new')}
            autoComplete="new-password"
            autoFocus
            required
            error={!!formState.errors.newPassword || !!serverErrors.newPassword}
            helperText={formState.errors.newPassword?.message ?? serverErrors.newPassword ?? t('password.rules')}
            {...register('newPassword')}
          />
          <PasswordField
            label={t('password.confirm')}
            autoComplete="new-password"
            required
            error={!!formState.errors.confirmation}
            helperText={formState.errors.confirmation?.message}
            {...register('confirmation')}
          />
          <Button type="submit" variant="contained" disabled={formState.isSubmitting}>{t('auth.resetSubmit')}</Button>
        </Stack>
      )}
      <Link component={RouterLink} to={LOGIN_PATH} sx={{ display: 'block', textAlign: 'center', mt: 2 }}>
        {t('auth.backToLogin')}
      </Link>
    </AuthLayout>
  )
}
