import { zodResolver } from '@hookform/resolvers/zod'
import AddIcon from '@mui/icons-material/Add'
import DeleteIcon from '@mui/icons-material/Delete'
import {
  Alert,
  Button,
  Checkbox,
  Dialog,
  DialogActions,
  DialogContent,
  DialogTitle,
  FormControlLabel,
  FormHelperText,
  IconButton,
  MenuItem,
  Stack,
  TextField,
  Typography,
} from '@mui/material'
import { useMutation } from '@tanstack/react-query'
import { Controller, useFieldArray, useForm, useWatch } from 'react-hook-form'
import { useTranslation } from 'react-i18next'
import { z } from 'zod'

import type { Role, User, UserInput, UserWithTemporaryPassword } from '../api/administration'
import { createUser, updateUser } from '../api/administration'
import { fieldErrors } from '../api/http'
import type { Hotel } from '../api/hotels'
import { useAuth } from '../auth/AuthContext'
import { ErrorAlert } from '../components/ErrorAlert'

interface UserDialogProps {
  open: boolean
  /** Compte à modifier ; absent pour une création. */
  user: User | null
  hotels: Hotel[]
  roles: Role[]
  onClose: () => void
  onCreated: (result: UserWithTemporaryPassword) => void
  onUpdated: (user: User) => void
}

export function UserDialog({ open, user, hotels, roles, onClose, onCreated, onUpdated }: UserDialogProps) {
  const { t } = useTranslation()
  const { profile } = useAuth()
  const creating = user === null

  const schema = z.object({
    username: z.string().trim().regex(/^[A-Za-z0-9._-]{3,50}$/, t('users.usernameHelp')),
    firstName: z.string().trim().min(1, t('errors.required')).max(100),
    lastName: z.string().trim().min(1, t('errors.required')).max(100),
    email: z.union([z.literal(''), z.email(t('errors.invalidEmail'))]),
    phone: z.string().trim().max(30),
    language: z.enum(['fr', 'en']),
    superAdmin: z.boolean(),
    assignments: z.array(z.object({ hotelId: z.string().min(1, t('errors.required')), roleId: z.string().min(1, t('errors.required')) })),
  }).refine((values) => values.superAdmin || values.assignments.length > 0, {
    path: ['assignments'],
    message: t('users.assignmentsHelp'),
  })
  type Form = z.infer<typeof schema>

  const { register, control, handleSubmit, formState } = useForm<Form>({
    resolver: zodResolver(schema),
    values: {
      username: user?.username ?? '',
      firstName: user?.firstName ?? '',
      lastName: user?.lastName ?? '',
      email: user?.email ?? '',
      phone: user?.phone ?? '',
      language: user?.language ?? 'fr',
      superAdmin: user?.superAdmin ?? false,
      assignments: user?.assignments.map(({ hotelId, roleId }) => ({ hotelId, roleId }))
        ?? (hotels.length === 1 ? [{ hotelId: hotels[0].id, roleId: '' }] : []),
    },
  })
  const assignments = useFieldArray({ control, name: 'assignments' })
  const superAdmin = useWatch({ control, name: 'superAdmin' })

  const mutation = useMutation({
    mutationFn: async (values: Form) => {
      const input: UserInput = {
        firstName: values.firstName,
        lastName: values.lastName,
        email: values.email || null,
        phone: values.phone || null,
        language: values.language,
        superAdmin: values.superAdmin,
        assignments: values.assignments,
      }
      if (creating) {
        onCreated(await createUser({ ...input, username: values.username }))
      } else {
        onUpdated(await updateUser(user.id, { ...input, version: user.version }))
      }
    },
  })
  const serverErrors = fieldErrors(mutation.error)

  const close = () => {
    mutation.reset()
    onClose()
  }

  return (
    <Dialog open={open} onClose={close} maxWidth="sm" fullWidth>
      <DialogTitle>{creating ? t('users.create') : `${t('users.edit')} — ${user.username}`}</DialogTitle>
      <DialogContent>
        <Stack component="form" id="user-form" spacing={2} sx={{ pt: 1 }} noValidate
          onSubmit={handleSubmit((values) => mutation.mutate(values))}>
          {mutation.error && Object.keys(serverErrors).length === 0 ? <ErrorAlert error={mutation.error} /> : null}
          <TextField
            label={t('users.username')}
            required
            disabled={!creating}
            error={!!formState.errors.username}
            helperText={formState.errors.username?.message ?? t('users.usernameHelp')}
            {...register('username')}
          />
          <Stack direction={{ xs: 'column', sm: 'row' }} spacing={2}>
            <TextField label={t('profile.firstName')} fullWidth required error={!!formState.errors.firstName}
              helperText={formState.errors.firstName?.message} {...register('firstName')} />
            <TextField label={t('profile.lastName')} fullWidth required error={!!formState.errors.lastName}
              helperText={formState.errors.lastName?.message} {...register('lastName')} />
          </Stack>
          <TextField
            label={t('users.email')}
            type="email"
            error={!!formState.errors.email || !!serverErrors.email}
            helperText={formState.errors.email?.message ?? serverErrors.email ?? t('users.emailHelp')}
            {...register('email')}
          />
          <Stack direction={{ xs: 'column', sm: 'row' }} spacing={2}>
            <TextField label={t('profile.phone')} fullWidth {...register('phone')} />
            <Controller
              name="language"
              control={control}
              render={({ field }) => (
                <TextField select label={t('profile.language')} fullWidth {...field}>
                  <MenuItem value="fr">{t('profile.languages.fr')}</MenuItem>
                  <MenuItem value="en">{t('profile.languages.en')}</MenuItem>
                </TextField>
              )}
            />
          </Stack>

          {profile?.superAdmin && (
            <Controller
              name="superAdmin"
              control={control}
              render={({ field }) => (
                <FormControlLabel
                  control={<Checkbox checked={field.value} onChange={(event) => field.onChange(event.target.checked)} />}
                  label={`${t('users.superAdmin')} — ${t('users.superAdminHelp')}`}
                />
              )}
            />
          )}

          <Typography variant="subtitle1" sx={{ fontWeight: 600 }}>{t('users.assignments')}</Typography>
          {superAdmin && <Alert severity="info">{t('users.superAdminHelp')}</Alert>}
          {assignments.fields.map((field, index) => (
            <Stack key={field.id} direction={{ xs: 'column', sm: 'row' }} spacing={1} sx={{ alignItems: { sm: 'flex-start' } }}>
              <Controller
                name={`assignments.${index}.hotelId`}
                control={control}
                render={({ field: hotelField, fieldState }) => (
                  <TextField select label={t('users.hotel')} fullWidth {...hotelField} error={!!fieldState.error}
                    helperText={fieldState.error?.message}>
                    {hotels.map((hotel) => <MenuItem key={hotel.id} value={hotel.id}>{hotel.name}</MenuItem>)}
                  </TextField>
                )}
              />
              <Controller
                name={`assignments.${index}.roleId`}
                control={control}
                render={({ field: roleField, fieldState }) => (
                  <TextField select label={t('users.role')} fullWidth {...roleField} error={!!fieldState.error}
                    helperText={fieldState.error?.message}>
                    {roles.map((role) => <MenuItem key={role.id} value={role.id}>{role.name}</MenuItem>)}
                  </TextField>
                )}
              />
              <IconButton aria-label={t('users.removeAssignment')} onClick={() => assignments.remove(index)} sx={{ mt: 1 }}>
                <DeleteIcon />
              </IconButton>
            </Stack>
          ))}
          {formState.errors.assignments?.message && (
            <FormHelperText error>{formState.errors.assignments.message}</FormHelperText>
          )}
          <Button
            startIcon={<AddIcon />}
            onClick={() => assignments.append({ hotelId: hotels.length === 1 ? hotels[0].id : '', roleId: '' })}
            sx={{ alignSelf: 'flex-start' }}
          >
            {t('users.addAssignment')}
          </Button>
        </Stack>
      </DialogContent>
      <DialogActions>
        <Button onClick={close}>{t('users.cancel')}</Button>
        <Button type="submit" form="user-form" variant="contained" disabled={mutation.isPending}>{t('users.save')}</Button>
      </DialogActions>
    </Dialog>
  )
}
