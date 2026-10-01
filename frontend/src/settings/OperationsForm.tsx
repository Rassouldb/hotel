import { Alert, Button, Checkbox, FormControlLabel, MenuItem, Stack, TextField, Typography } from '@mui/material'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'
import { useTranslation } from 'react-i18next'

import type { CheckInGuarantee, HotelSettings } from '../api/hotels'
import { fetchSettings, updateSettings } from '../api/hotels'
import { ErrorAlert } from '../components/ErrorAlert'

const GUARANTEES: CheckInGuarantee[] = ['NONE', 'FIRST_NIGHT', 'FULL_STAY', 'FIXED_DEPOSIT']
const ROLE_LABELS: Record<string, string> = { RECEPTIONIST: 'Réceptionniste', DIRECTOR: 'Directeur / manager' }

/** Paramètres d'exploitation de l'hôtel (US-HOTEL-03). */
export function OperationsForm({ hotelId, canEdit }: { hotelId: string; canEdit: boolean }) {
  const settings = useQuery({ queryKey: ['hotel-settings', hotelId], queryFn: () => fetchSettings(hotelId) })

  if (settings.error) {
    return <ErrorAlert error={settings.error} onRetry={() => void settings.refetch()} />
  }
  if (!settings.data) {
    return null
  }
  return <OperationsEditor key={hotelId} hotelId={hotelId} initial={settings.data} canEdit={canEdit} />
}

function OperationsEditor({ hotelId, initial, canEdit }: { hotelId: string; initial: HotelSettings; canEdit: boolean }) {
  const { t } = useTranslation()
  const queryClient = useQueryClient()
  const [values, setValues] = useState<HotelSettings>(initial)
  const [saved, setSaved] = useState(false)
  const save = useMutation({
    mutationFn: () => updateSettings(hotelId, values),
    onSuccess: (result) => {
      setSaved(true)
      queryClient.setQueryData(['hotel-settings', hotelId], result)
    },
  })
  const set = <K extends keyof HotelSettings>(key: K, value: HotelSettings[K]) => setValues((current) => ({ ...current, [key]: value }))
  const setPolicy = (key: keyof HotelSettings['cancellationPolicy'], value: number) =>
    setValues((current) => ({ ...current, cancellationPolicy: { ...current.cancellationPolicy, [key]: value } }))
  const time = (value: string) => value.slice(0, 5)

  return (
    <Stack spacing={3} sx={{ maxWidth: 720 }}>
      {saved && <Alert severity="success">{t('settings.saved')}</Alert>}
      {save.error ? <ErrorAlert error={save.error} /> : null}
      <Stack direction={{ xs: 'column', sm: 'row' }} spacing={2}>
        <TextField select label={t('settings.operations.prices')} value={values.pricesIncludeTax ? 'TTC' : 'HT'} disabled={!canEdit}
          onChange={(e) => set('pricesIncludeTax', e.target.value === 'TTC')} sx={{ minWidth: 220 }}>
          <MenuItem value="TTC">{t('settings.operations.pricesTtc')}</MenuItem>
          <MenuItem value="HT">{t('settings.operations.pricesHt')}</MenuItem>
        </TextField>
        <TextField type="time" label={t('settings.operations.checkInTime')} value={time(values.checkInTime)} disabled={!canEdit}
          onChange={(e) => set('checkInTime', `${e.target.value}:00`)} slotProps={{ inputLabel: { shrink: true } }} />
        <TextField type="time" label={t('settings.operations.checkOutTime')} value={time(values.checkOutTime)} disabled={!canEdit}
          onChange={(e) => set('checkOutTime', `${e.target.value}:00`)} slotProps={{ inputLabel: { shrink: true } }} />
      </Stack>
      <Stack>
        <FormControlLabel disabled={!canEdit} label={t('settings.operations.inspectionRequired')}
          control={<Checkbox checked={values.inspectionRequired} onChange={(e) => set('inspectionRequired', e.target.checked)} />} />
        <FormControlLabel disabled={!canEdit} label={t('settings.operations.idDocumentRequired')}
          control={<Checkbox checked={values.idDocumentRequired} onChange={(e) => set('idDocumentRequired', e.target.checked)} />} />
        <FormControlLabel disabled={!canEdit} label={t('settings.operations.walkIn')}
          control={<Checkbox checked={values.walkInFirstNightPayment} onChange={(e) => set('walkInFirstNightPayment', e.target.checked)} />} />
      </Stack>
      <Stack direction={{ xs: 'column', sm: 'row' }} spacing={2}>
        <TextField select label={t('settings.operations.guarantee')} value={values.checkInGuarantee} disabled={!canEdit} sx={{ minWidth: 260 }}
          onChange={(e) => set('checkInGuarantee', e.target.value as CheckInGuarantee)}>
          {GUARANTEES.map((g) => <MenuItem key={g} value={g}>{t(`settings.operations.guarantees.${g}`)}</MenuItem>)}
        </TextField>
        {values.checkInGuarantee === 'FIXED_DEPOSIT' && (
          <TextField type="number" label={t('settings.operations.guaranteeAmount')} value={values.guaranteeAmount ?? ''} disabled={!canEdit}
            onChange={(e) => set('guaranteeAmount', e.target.value === '' ? null : Number(e.target.value))} />
        )}
      </Stack>
      <Typography variant="subtitle1" sx={{ fontWeight: 600 }}>{t('settings.operations.cancellation')}</Typography>
      <Stack direction={{ xs: 'column', sm: 'row' }} spacing={2}>
        <TextField type="number" label={t('settings.operations.freeUntilHours')} value={values.cancellationPolicy.freeUntilHours}
          disabled={!canEdit} onChange={(e) => setPolicy('freeUntilHours', Number(e.target.value))} fullWidth />
        <TextField type="number" label={t('settings.operations.latePenalty')} value={values.cancellationPolicy.lateCancellationPenaltyNights}
          disabled={!canEdit} onChange={(e) => setPolicy('lateCancellationPenaltyNights', Number(e.target.value))} fullWidth />
        <TextField type="number" label={t('settings.operations.noShowPenalty')} value={values.cancellationPolicy.noShowPenaltyNights}
          disabled={!canEdit} onChange={(e) => setPolicy('noShowPenaltyNights', Number(e.target.value))} fullWidth />
      </Stack>
      <Typography variant="subtitle1" sx={{ fontWeight: 600 }}>{t('settings.operations.discounts')}</Typography>
      <Stack direction={{ xs: 'column', sm: 'row' }} spacing={2}>
        {Object.entries(values.discountLimits).map(([role, limit]) => (
          <TextField key={role} type="number" label={ROLE_LABELS[role] ?? role} value={limit} disabled={!canEdit}
            onChange={(e) => set('discountLimits', { ...values.discountLimits, [role]: Number(e.target.value) })} />
        ))}
      </Stack>
      {canEdit && (
        <Button variant="contained" sx={{ alignSelf: 'flex-start' }} disabled={save.isPending} onClick={() => { setSaved(false); save.mutate() }}>
          {t('settings.save')}
        </Button>
      )}
    </Stack>
  )
}
