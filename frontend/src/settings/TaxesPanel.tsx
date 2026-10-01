import AddIcon from '@mui/icons-material/Add'
import {
  Alert,
  Button,
  Card,
  CardActions,
  CardContent,
  Checkbox,
  Chip,
  Dialog,
  DialogActions,
  DialogContent,
  DialogTitle,
  FormControlLabel,
  FormGroup,
  Grid,
  MenuItem,
  Stack,
  TextField,
  Typography,
} from '@mui/material'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'
import { useTranslation } from 'react-i18next'

import { formatAmount, formatDate } from '../app/format'
import type { ChargeCategory, Tax, TaxType } from '../api/configuration'
import { CHARGE_CATEGORIES, changeTaxRate, createTax, fetchTaxes, updateTax } from '../api/configuration'
import { fieldErrors } from '../api/http'
import { ErrorAlert } from '../components/ErrorAlert'

const TYPES: TaxType[] = ['PERCENTAGE', 'FIXED_PER_PERSON_NIGHT', 'FIXED_PER_UNIT']

interface TaxesPanelProps {
  hotelId: string
  currency: string
  businessDate: string
  canEdit: boolean
}

/** Taxes de l'hôtel et historique de leurs taux (US-TAX-01). */
export function TaxesPanel({ hotelId, currency, businessDate, canEdit }: TaxesPanelProps) {
  const { t } = useTranslation()
  const taxes = useQuery({ queryKey: ['taxes', hotelId], queryFn: () => fetchTaxes(hotelId) })
  const [editing, setEditing] = useState<Tax | null | undefined>(undefined)
  const [rateFor, setRateFor] = useState<Tax | null>(null)

  const value = (tax: Pick<Tax, 'type'>, rate: number | null, amount: number | null) =>
    tax.type === 'PERCENTAGE' ? `${rate ?? '—'} %` : amount == null ? '—' : formatAmount(amount, currency)

  return (
    <Stack spacing={2}>
      {canEdit && (
        <Button variant="contained" startIcon={<AddIcon />} sx={{ alignSelf: 'flex-end' }} onClick={() => setEditing(null)}>
          {t('settings.taxes.add')}
        </Button>
      )}
      {taxes.error ? <ErrorAlert error={taxes.error} /> : null}
      {taxes.data?.length === 0 && <Alert severity="info">{t('settings.taxes.none')}</Alert>}
      <Grid container spacing={2}>
        {(taxes.data ?? []).map((tax) => (
          <Grid key={tax.id} size={{ xs: 12, md: 6 }}>
            <Card variant="outlined">
              <CardContent>
                <Stack direction="row" spacing={1} sx={{ alignItems: 'center', mb: 1, flexWrap: 'wrap', gap: 0.5 }}>
                  <Typography variant="h6" component="h3">{tax.name}</Typography>
                  <Chip size="small" label={tax.code} />
                  {tax.vat && <Chip size="small" color="primary" label={t('settings.taxes.vat')} />}
                  {!tax.active && <Chip size="small" label={t('settings.inactive')} />}
                </Stack>
                <Typography variant="h5">{value(tax, tax.currentRate, tax.currentAmount)}</Typography>
                <Typography variant="body2" color="text.secondary" gutterBottom>{t(`settings.taxes.types.${tax.type}`)}</Typography>
                <Typography variant="body2">
                  {t('settings.taxes.appliesTo')} : {tax.appliesTo.map((c) => t(`settings.taxes.categories.${c}`)).join(', ')}
                </Typography>
                {tax.exemptChildrenUnderAge != null && (
                  <Typography variant="body2">{t('settings.taxes.exemptChildren')} : {tax.exemptChildrenUnderAge}</Typography>
                )}
                <Typography variant="caption" color="text.secondary" component="div" sx={{ mt: 1 }}>
                  {t('settings.taxes.history')} :{' '}
                  {tax.periods.map((p) => `${formatDate(p.validFrom)} → ${p.validTo ? formatDate(p.validTo) : t('settings.taxes.ongoing')} : ${value(tax, p.rate, p.amount)}`).join(' · ')}
                </Typography>
              </CardContent>
              {canEdit && (
                <CardActions>
                  <Button size="small" onClick={() => setEditing(tax)}>{t('settings.edit')}</Button>
                  <Button size="small" onClick={() => setRateFor(tax)}>{t('settings.taxes.changeRate')}</Button>
                </CardActions>
              )}
            </Card>
          </Grid>
        ))}
      </Grid>
      {editing !== undefined && (
        <TaxDialog hotelId={hotelId} tax={editing} businessDate={businessDate} onClose={() => setEditing(undefined)} />
      )}
      {rateFor && <RateDialog hotelId={hotelId} tax={rateFor} businessDate={businessDate} onClose={() => setRateFor(null)} />}
    </Stack>
  )
}

function TaxDialog({ hotelId, tax, businessDate, onClose }: { hotelId: string; tax: Tax | null; businessDate: string; onClose: () => void }) {
  const { t } = useTranslation()
  const queryClient = useQueryClient()
  const [code, setCode] = useState(tax?.code ?? '')
  const [name, setName] = useState(tax?.name ?? '')
  const [type, setType] = useState<TaxType>(tax?.type ?? 'PERCENTAGE')
  const [vat, setVat] = useState(tax?.vat ?? false)
  const [appliesTo, setAppliesTo] = useState<ChargeCategory[]>(tax?.appliesTo ?? ['ACCOMMODATION'])
  const [exemptChildren, setExemptChildren] = useState(tax?.exemptChildrenUnderAge?.toString() ?? '')
  const [exemptResidents, setExemptResidents] = useState(tax?.exemptResidents ?? false)
  const [active, setActive] = useState(tax?.active ?? true)
  const [validFrom, setValidFrom] = useState(businessDate)
  const [value, setValue] = useState('')

  const save = useMutation({
    mutationFn: () => {
      const exemptChildrenUnderAge = exemptChildren === '' ? null : Number(exemptChildren)
      if (tax) {
        return updateTax(hotelId, tax, { name, appliesTo, exemptChildrenUnderAge, exemptResidents, active })
      }
      const numeric = value === '' ? null : Number(value)
      return createTax(hotelId, {
        code, name, type, vat, appliesTo, exemptChildrenUnderAge, exemptResidents, validFrom,
        rate: type === 'PERCENTAGE' ? numeric : null, amount: type === 'PERCENTAGE' ? null : numeric,
      })
    },
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ['taxes', hotelId] })
      onClose()
    },
  })
  const errors = fieldErrors(save.error)

  return (
    <Dialog open onClose={onClose} maxWidth="sm" fullWidth>
      <DialogTitle>{tax ? `${t('settings.edit')} — ${tax.name}` : t('settings.taxes.add')}</DialogTitle>
      <DialogContent>
        <Stack spacing={2} sx={{ pt: 1 }}>
          {save.error && Object.keys(errors).length === 0 ? <ErrorAlert error={save.error} /> : null}
          <Stack direction="row" spacing={2}>
            <TextField label={t('settings.code')} value={code} onChange={(e) => setCode(e.target.value.toUpperCase())} disabled={!!tax} sx={{ width: 140 }} />
            <TextField label={t('settings.name')} value={name} onChange={(e) => setName(e.target.value)} fullWidth />
          </Stack>
          <TextField select label={t('settings.taxes.type')} value={type} onChange={(e) => setType(e.target.value as TaxType)} disabled={!!tax}>
            {TYPES.map((tp) => <MenuItem key={tp} value={tp}>{t(`settings.taxes.types.${tp}`)}</MenuItem>)}
          </TextField>
          {!tax && (
            <Stack direction="row" spacing={2}>
              <TextField type="number" label={type === 'PERCENTAGE' ? t('settings.taxes.rate') : t('settings.taxes.amount')}
                value={value} onChange={(e) => setValue(e.target.value)} error={!!errors.rate || !!errors.amount}
                helperText={errors.rate ?? errors.amount} fullWidth />
              <TextField type="date" label={t('settings.taxes.validFrom')} value={validFrom} onChange={(e) => setValidFrom(e.target.value)}
                slotProps={{ inputLabel: { shrink: true } }} fullWidth />
            </Stack>
          )}
          <FormControlLabel label={t('settings.taxes.vat')} disabled={!!tax}
            control={<Checkbox checked={vat} onChange={(e) => setVat(e.target.checked)} />} />
          <Typography variant="subtitle2">{t('settings.taxes.appliesTo')}</Typography>
          <FormGroup row>
            {CHARGE_CATEGORIES.map((category) => (
              <FormControlLabel key={category} label={t(`settings.taxes.categories.${category}`)}
                control={<Checkbox checked={appliesTo.includes(category)}
                  onChange={(e) => setAppliesTo((current) => e.target.checked ? [...current, category] : current.filter((c) => c !== category))} />} />
            ))}
          </FormGroup>
          <Stack direction="row" spacing={2} sx={{ alignItems: 'center' }}>
            <TextField type="number" label={t('settings.taxes.exemptChildren')} value={exemptChildren}
              onChange={(e) => setExemptChildren(e.target.value)} sx={{ width: 280 }} />
            <FormControlLabel label={t('settings.taxes.exemptResidents')}
              control={<Checkbox checked={exemptResidents} onChange={(e) => setExemptResidents(e.target.checked)} />} />
          </Stack>
          {tax && (
            <FormControlLabel label={t('settings.active')} control={<Checkbox checked={active} onChange={(e) => setActive(e.target.checked)} />} />
          )}
        </Stack>
      </DialogContent>
      <DialogActions>
        <Button onClick={onClose}>{t('common.cancel')}</Button>
        <Button variant="contained" disabled={save.isPending || !name.trim() || appliesTo.length === 0} onClick={() => save.mutate()}>
          {t('settings.save')}
        </Button>
      </DialogActions>
    </Dialog>
  )
}

function RateDialog({ hotelId, tax, businessDate, onClose }: { hotelId: string; tax: Tax; businessDate: string; onClose: () => void }) {
  const { t } = useTranslation()
  const queryClient = useQueryClient()
  const [validFrom, setValidFrom] = useState(businessDate)
  const [value, setValue] = useState('')
  const percentage = tax.type === 'PERCENTAGE'
  const save = useMutation({
    mutationFn: () => changeTaxRate(hotelId, tax.id, {
      validFrom, rate: percentage ? Number(value) : null, amount: percentage ? null : Number(value),
    }),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ['taxes', hotelId] })
      onClose()
    },
  })

  return (
    <Dialog open onClose={onClose} maxWidth="xs" fullWidth>
      <DialogTitle>{t('settings.taxes.changeRate')} — {tax.name}</DialogTitle>
      <DialogContent>
        <Stack spacing={2} sx={{ pt: 1 }}>
          <Alert severity="info">{t('settings.taxes.changeRateHelp')}</Alert>
          {save.error ? <ErrorAlert error={save.error} /> : null}
          <TextField type="number" label={percentage ? t('settings.taxes.rate') : t('settings.taxes.amount')} value={value}
            onChange={(e) => setValue(e.target.value)} />
          <TextField type="date" label={t('settings.taxes.validFrom')} value={validFrom} onChange={(e) => setValidFrom(e.target.value)}
            slotProps={{ inputLabel: { shrink: true }, htmlInput: { min: businessDate } }} />
        </Stack>
      </DialogContent>
      <DialogActions>
        <Button onClick={onClose}>{t('common.cancel')}</Button>
        <Button variant="contained" disabled={save.isPending || value === ''} onClick={() => save.mutate()}>{t('settings.save')}</Button>
      </DialogActions>
    </Dialog>
  )
}
