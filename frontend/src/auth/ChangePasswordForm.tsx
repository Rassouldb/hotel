import { zodResolver } from '@hookform/resolvers/zod'
import { Alert, Button, Stack } from '@mui/material'
import { useState } from 'react'
import { useForm } from 'react-hook-form'
import { useTranslation } from 'react-i18next'
import { z } from 'zod'

import * as authApi from '../api/auth'
import { fieldErrors } from '../api/http'
import { ErrorAlert } from '../components/ErrorAlert'
import { PasswordField } from '../components/PasswordField'
import { useAuth } from './AuthContext'

const MIN_LENGTH = 10

interface ChangePasswordFormProps {
  /** Appelé après le changement, une fois la session mise à jour. */
  onChanged?: () => void
}

/** Changement de mot de passe (US-AUTH-04), utilisé à la première connexion et dans le profil. */
export function ChangePasswordForm({ onChanged }: ChangePasswordFormProps) {
  const { t } = useTranslation()
  const { completePasswordChange } = useAuth()
  const [error, setError] = useState<unknown>(null)
  const [done, setDone] = useState(false)

  const schema = z.object({
    currentPassword: z.string().min(1, t('errors.required')),
    newPassword: z.string().min(MIN_LENGTH, t('password.tooShort')),
    confirmation: z.string(),
  }).refine((values) => values.newPassword === values.confirmation, {
    path: ['confirmation'],
    message: t('password.mismatch'),
  })
  type Form = z.infer<typeof schema>

  const { register, handleSubmit, formState, reset } = useForm<Form>({
    resolver: zodResolver(schema),
    defaultValues: { currentPassword: '', newPassword: '', confirmation: '' },
  })

  const serverErrors = fieldErrors(error)
  const shownOnField = Object.keys(serverErrors).length > 0

  const onSubmit = async (values: Form) => {
    setError(null)
    setDone(false)
    try {
      const response = await authApi.changePassword(values.currentPassword, values.newPassword)
      await completePasswordChange(response)
      reset()
      setDone(true)
      onChanged?.()
    } catch (cause) {
      setError(cause)
    }
  }

  return (
    <Stack component="form" spacing={2} noValidate onSubmit={handleSubmit(onSubmit)}>
      {done && <Alert severity="success">{t('password.changed')}</Alert>}
      {error && !shownOnField ? <ErrorAlert error={error} /> : null}
      <PasswordField
        label={t('password.current')}
        autoComplete="current-password"
        required
        error={!!formState.errors.currentPassword || !!serverErrors.currentPassword}
        helperText={formState.errors.currentPassword?.message ?? serverErrors.currentPassword}
        {...register('currentPassword')}
      />
      <PasswordField
        label={t('password.new')}
        autoComplete="new-password"
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
      <Button type="submit" variant="contained" disabled={formState.isSubmitting} sx={{ alignSelf: 'flex-start' }}>
        {t('password.submit')}
      </Button>
    </Stack>
  )
}
