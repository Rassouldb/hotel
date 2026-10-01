import AddIcon from '@mui/icons-material/Add'
import DeleteIcon from '@mui/icons-material/Delete'
import { Alert, Avatar, Box, Button, IconButton, MenuItem, Stack, TextField, Typography } from '@mui/material'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useEffect, useState } from 'react'
import { useTranslation } from 'react-i18next'

import type { Hotel } from '../api/hotels'
import { deleteLogo, fetchLogoUrl, updateHotel, uploadLogo } from '../api/hotels'
import { fieldErrors } from '../api/http'
import { ErrorAlert } from '../components/ErrorAlert'

interface LegalIdRow {
  label: string
  value: string
}

/** Informations figurant sur les factures et logo de l'hôtel (US-HOTEL-02). */
export function HotelInformationForm({ hotel, canEdit }: { hotel: Hotel; canEdit: boolean }) {
  const { t } = useTranslation()
  const queryClient = useQueryClient()
  const [name, setName] = useState(hotel.name)
  const [legalName, setLegalName] = useState(hotel.legalName ?? '')
  const [address, setAddress] = useState(hotel.address ?? '')
  const [phone, setPhone] = useState(hotel.phone ?? '')
  const [email, setEmail] = useState(hotel.email ?? '')
  const [language, setLanguage] = useState(hotel.language)
  const [legalIds, setLegalIds] = useState<LegalIdRow[]>(
    Object.entries(hotel.legalIds ?? {}).map(([label, value]) => ({ label, value })),
  )
  const [saved, setSaved] = useState(false)

  const save = useMutation({
    mutationFn: () => updateHotel(hotel.id, {
      name, legalName: legalName || null, address: address || null, phone: phone || null, email: email || null, language,
      legalIds: Object.fromEntries(legalIds.filter((row) => row.label && row.value).map((row) => [row.label, row.value])),
      version: hotel.version,
    }),
    onSuccess: () => {
      setSaved(true)
      void queryClient.invalidateQueries({ queryKey: ['hotels'] })
    },
  })
  const errors = fieldErrors(save.error)

  return (
    <Stack spacing={3} sx={{ maxWidth: 720 }}>
      {saved && <Alert severity="success">{t('settings.saved')}</Alert>}
      {save.error && Object.keys(errors).length === 0 ? <ErrorAlert error={save.error} /> : null}
      <LogoEditor hotel={hotel} canEdit={canEdit} />
      <Typography variant="body2" color="text.secondary">
        {hotel.code} · {t('settings.information.currencyFixed', { currency: hotel.currencyCode, timezone: hotel.timezone })}
      </Typography>
      <Stack direction={{ xs: 'column', sm: 'row' }} spacing={2}>
        <TextField label={t('hotels.name')} value={name} onChange={(e) => setName(e.target.value)} fullWidth required disabled={!canEdit} />
        <TextField label={t('hotels.legalName')} value={legalName} onChange={(e) => setLegalName(e.target.value)} fullWidth disabled={!canEdit} />
      </Stack>
      <TextField label={t('settings.information.address')} value={address} onChange={(e) => setAddress(e.target.value)} disabled={!canEdit} />
      <Stack direction={{ xs: 'column', sm: 'row' }} spacing={2}>
        <TextField label={t('settings.information.phone')} value={phone} onChange={(e) => setPhone(e.target.value)} fullWidth disabled={!canEdit} />
        <TextField label={t('settings.information.email')} value={email} onChange={(e) => setEmail(e.target.value)} fullWidth disabled={!canEdit}
          error={!!errors.email} helperText={errors.email} />
        <TextField select label={t('hotels.language')} value={language} onChange={(e) => setLanguage(e.target.value)} sx={{ minWidth: 140 }} disabled={!canEdit}>
          <MenuItem value="fr">{t('profile.languages.fr')}</MenuItem>
          <MenuItem value="en">{t('profile.languages.en')}</MenuItem>
        </TextField>
      </Stack>
      <Box>
        <Typography variant="subtitle1" sx={{ fontWeight: 600 }}>{t('settings.information.legalIds')}</Typography>
        <Typography variant="body2" color="text.secondary" gutterBottom>{t('settings.information.legalIdsHelp')}</Typography>
        <Stack spacing={1}>
          {legalIds.map((row, index) => (
            <Stack key={index} direction="row" spacing={1}>
              <TextField size="small" label={t('settings.information.label')} value={row.label} disabled={!canEdit}
                onChange={(e) => setLegalIds((rows) => rows.map((r, i) => (i === index ? { ...r, label: e.target.value } : r)))} />
              <TextField size="small" label={t('settings.information.value')} value={row.value} disabled={!canEdit} sx={{ flexGrow: 1 }}
                onChange={(e) => setLegalIds((rows) => rows.map((r, i) => (i === index ? { ...r, value: e.target.value } : r)))} />
              {canEdit && (
                <IconButton aria-label={t('users.removeAssignment')} onClick={() => setLegalIds((rows) => rows.filter((_, i) => i !== index))}>
                  <DeleteIcon />
                </IconButton>
              )}
            </Stack>
          ))}
          {canEdit && (
            <Button startIcon={<AddIcon />} sx={{ alignSelf: 'flex-start' }}
              onClick={() => setLegalIds((rows) => [...rows, { label: '', value: '' }])}>
              {t('settings.information.addLegalId')}
            </Button>
          )}
        </Stack>
      </Box>
      {canEdit && (
        <Button variant="contained" sx={{ alignSelf: 'flex-start' }} disabled={save.isPending || !name.trim()}
          onClick={() => { setSaved(false); save.mutate() }}>
          {t('settings.save')}
        </Button>
      )}
    </Stack>
  )
}

function LogoEditor({ hotel, canEdit }: { hotel: Hotel; canEdit: boolean }) {
  const { t } = useTranslation()
  const queryClient = useQueryClient()
  const logo = useQuery({
    queryKey: ['hotel-logo', hotel.id, hotel.version, hotel.hasLogo],
    queryFn: () => fetchLogoUrl(hotel.id),
    enabled: hotel.hasLogo,
    staleTime: Infinity,
  })
  useEffect(() => () => { if (logo.data) URL.revokeObjectURL(logo.data) }, [logo.data])

  const refresh = () => queryClient.invalidateQueries({ queryKey: ['hotels'] })
  const upload = useMutation({ mutationFn: (file: File) => uploadLogo(hotel.id, file), onSuccess: refresh })
  const remove = useMutation({ mutationFn: () => deleteLogo(hotel.id), onSuccess: refresh })

  return (
    <Stack direction="row" spacing={2} sx={{ alignItems: 'center' }}>
      <Avatar variant="rounded" src={hotel.hasLogo ? logo.data : undefined} sx={{ width: 96, height: 96, bgcolor: 'action.hover', color: 'text.secondary' }}>
        {hotel.code}
      </Avatar>
      <Stack spacing={1}>
        <Typography variant="subtitle1" sx={{ fontWeight: 600 }}>{t('settings.information.logo')}</Typography>
        <Typography variant="caption" color="text.secondary">{t('settings.information.logoHelp')}</Typography>
        {upload.error ? <ErrorAlert error={upload.error} /> : null}
        {canEdit && (
          <Stack direction="row" spacing={1}>
            <Button component="label" size="small" variant="outlined" disabled={upload.isPending}>
              {t('settings.information.uploadLogo')}
              <input hidden type="file" accept="image/png,image/jpeg"
                onChange={(e) => { const file = e.target.files?.[0]; if (file) upload.mutate(file); e.target.value = '' }} />
            </Button>
            {hotel.hasLogo && (
              <Button size="small" color="error" onClick={() => remove.mutate()} disabled={remove.isPending}>
                {t('settings.information.deleteLogo')}
              </Button>
            )}
          </Stack>
        )}
      </Stack>
    </Stack>
  )
}
