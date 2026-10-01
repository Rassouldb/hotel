import { zodResolver } from '@hookform/resolvers/zod'
import {
  Alert,
  Button,
  Card,
  CardContent,
  Chip,
  Grid,
  List,
  ListItem,
  ListItemText,
  MenuItem,
  Stack,
  TextField,
  Typography,
} from '@mui/material'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useState, type ReactNode } from 'react'
import { Controller, useForm } from 'react-hook-form'
import { useTranslation } from 'react-i18next'
import { z } from 'zod'

import * as authApi from '../api/auth'
import type { Profile } from '../api/auth'
import { useAuth } from '../auth/AuthContext'
import { ChangePasswordForm } from '../auth/ChangePasswordForm'
import { ErrorAlert } from '../components/ErrorAlert'
import { useActiveHotel } from '../hotel/ActiveHotelContext'

function formatDateTime(value: string): string {
  return new Intl.DateTimeFormat('fr-FR', { dateStyle: 'short', timeStyle: 'short' }).format(new Date(value))
}

/** Profil de l'utilisateur connecté : informations, mot de passe, sessions, accès (US-AUTH-04, 06, 07). */
export function ProfilePage() {
  const { t } = useTranslation()
  const { profile } = useAuth()

  if (!profile) {
    return null
  }

  return (
    <Stack spacing={3}>
      <Typography variant="h4" component="h1">{t('profile.title')}</Typography>
      <Grid container spacing={3}>
        <Grid size={{ xs: 12, md: 6 }}>
          <Section title={t('profile.information')}>
            <ProfileForm profile={profile} />
          </Section>
        </Grid>
        <Grid size={{ xs: 12, md: 6 }}>
          <Section title={t('profile.security')}>
            <ChangePasswordForm />
          </Section>
        </Grid>
        <Grid size={{ xs: 12, md: 6 }}>
          <Section title={t('profile.access')}>
            <AccessList profile={profile} />
          </Section>
        </Grid>
        <Grid size={{ xs: 12, md: 6 }}>
          <Section title={t('profile.sessions')}>
            <SessionList />
          </Section>
        </Grid>
      </Grid>
    </Stack>
  )
}

function Section({ title, children }: { title: string; children: ReactNode }) {
  return (
    <Card variant="outlined" sx={{ height: '100%' }}>
      <CardContent>
        <Typography variant="h6" component="h2" gutterBottom>{title}</Typography>
        {children}
      </CardContent>
    </Card>
  )
}

function ProfileForm({ profile }: { profile: Profile }) {
  const { t } = useTranslation()
  const { reloadProfile } = useAuth()
  const [saved, setSaved] = useState(false)
  const schema = z.object({
    firstName: z.string().trim().min(1, t('errors.required')).max(100),
    lastName: z.string().trim().min(1, t('errors.required')).max(100),
    phone: z.string().trim().max(30),
    language: z.enum(['fr', 'en']),
  })
  type Form = z.infer<typeof schema>
  const { register, control, handleSubmit, formState } = useForm<Form>({
    resolver: zodResolver(schema),
    defaultValues: {
      firstName: profile.firstName,
      lastName: profile.lastName,
      phone: profile.phone ?? '',
      language: profile.language,
    },
  })
  const mutation = useMutation({
    mutationFn: (values: Form) => authApi.updateProfile({ ...values, phone: values.phone || null }),
    onSuccess: async () => {
      await reloadProfile()
      setSaved(true)
    },
  })

  return (
    <Stack component="form" spacing={2} noValidate onSubmit={handleSubmit((values) => { setSaved(false); mutation.mutate(values) })}>
      {saved && <Alert severity="success">{t('profile.saved')}</Alert>}
      {mutation.error ? <ErrorAlert error={mutation.error} /> : null}
      <TextField label={t('users.username')} value={profile.username} disabled />
      <Stack direction={{ xs: 'column', sm: 'row' }} spacing={2}>
        <TextField label={t('profile.firstName')} fullWidth required error={!!formState.errors.firstName}
          helperText={formState.errors.firstName?.message} {...register('firstName')} />
        <TextField label={t('profile.lastName')} fullWidth required error={!!formState.errors.lastName}
          helperText={formState.errors.lastName?.message} {...register('lastName')} />
      </Stack>
      <TextField label={t('profile.phone')} {...register('phone')} />
      <Controller
        name="language"
        control={control}
        render={({ field }) => (
          <TextField select label={t('profile.language')} {...field}>
            <MenuItem value="fr">{t('profile.languages.fr')}</MenuItem>
            <MenuItem value="en">{t('profile.languages.en')}</MenuItem>
          </TextField>
        )}
      />
      <Button type="submit" variant="contained" disabled={mutation.isPending} sx={{ alignSelf: 'flex-start' }}>
        {t('profile.save')}
      </Button>
    </Stack>
  )
}

function AccessList({ profile }: { profile: Profile }) {
  const { t } = useTranslation()
  const { hotels } = useActiveHotel()
  const hotelName = (id: string) => hotels.find((hotel) => hotel.id === id)?.name ?? id

  if (profile.superAdmin) {
    return <Chip color="primary" label={t('users.superAdminHelp')} />
  }
  if (profile.hotels.length === 0) {
    return <Typography color="text.secondary">{t('profile.noAccess')}</Typography>
  }
  return (
    <List dense disablePadding>
      {profile.hotels.map((access) => (
        <ListItem key={access.hotelId} disableGutters>
          <ListItemText
            primary={hotelName(access.hotelId)}
            secondary={access.roles.map((role) => role.name).join(', ')}
          />
        </ListItem>
      ))}
    </List>
  )
}

function SessionList() {
  const { t } = useTranslation()
  const queryClient = useQueryClient()
  const sessions = useQuery({ queryKey: ['me', 'sessions'], queryFn: authApi.fetchSessions })
  const revoke = useMutation({
    mutationFn: authApi.revokeSession,
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['me', 'sessions'] }),
  })

  return (
    <Stack spacing={1}>
      <Typography variant="body2" color="text.secondary">{t('profile.sessionsHelp')}</Typography>
      {sessions.error ? <ErrorAlert error={sessions.error} /> : null}
      {revoke.error ? <ErrorAlert error={revoke.error} /> : null}
      <List dense disablePadding>
        {(sessions.data ?? []).map((session) => (
          <ListItem
            key={session.id}
            disableGutters
            secondaryAction={session.current
              ? <Chip size="small" color="success" label={t('profile.currentSession')} />
              : <Button size="small" color="error" onClick={() => revoke.mutate(session.id)}>{t('profile.revoke')}</Button>}
          >
            <ListItemText
              primary={session.userAgent ?? session.ip ?? session.id}
              secondary={`${t('profile.lastActivity')} : ${formatDateTime(session.lastActivityAt)} · ${session.ip ?? ''}`}
              slotProps={{ primary: { noWrap: true, sx: { maxWidth: { xs: 200, sm: 320 } } } }}
            />
          </ListItem>
        ))}
      </List>
    </Stack>
  )
}
