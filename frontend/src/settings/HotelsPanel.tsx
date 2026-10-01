import AddIcon from '@mui/icons-material/Add'
import {
  Alert,
  Button,
  Dialog,
  DialogActions,
  DialogContent,
  DialogTitle,
  MenuItem,
  Paper,
  Stack,
  Table,
  TableBody,
  TableCell,
  TableContainer,
  TableHead,
  TableRow,
  TextField,
} from '@mui/material'
import { useMutation, useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'
import { useTranslation } from 'react-i18next'

import { formatDate } from '../app/format'
import { createHotel } from '../api/hotels'
import { fieldErrors } from '../api/http'
import { ErrorAlert } from '../components/ErrorAlert'
import { useActiveHotel } from '../hotel/ActiveHotelContext'

/** Hôtels de l'organisation et création d'un hôtel, réservées au super administrateur (US-HOTEL-01). */
export function HotelsPanel() {
  const { t } = useTranslation()
  const { hotels } = useActiveHotel()
  const [creating, setCreating] = useState(false)
  const [created, setCreated] = useState(false)

  return (
    <Stack spacing={2}>
      <Button variant="contained" startIcon={<AddIcon />} sx={{ alignSelf: 'flex-end' }} onClick={() => { setCreated(false); setCreating(true) }}>
        {t('hotels.create')}
      </Button>
      {created && <Alert severity="success">{t('hotels.created')}</Alert>}
      <TableContainer component={Paper} variant="outlined">
        <Table size="small">
          <TableHead>
            <TableRow>
              <TableCell>{t('hotels.columns.code')}</TableCell>
              <TableCell>{t('hotels.columns.name')}</TableCell>
              <TableCell>{t('hotels.columns.currency')}</TableCell>
              <TableCell>{t('hotels.columns.businessDate')}</TableCell>
            </TableRow>
          </TableHead>
          <TableBody>
            {hotels.map((hotel) => (
              <TableRow key={hotel.id}>
                <TableCell>{hotel.code}</TableCell>
                <TableCell>{hotel.name}</TableCell>
                <TableCell>{hotel.currencyCode}</TableCell>
                <TableCell>{formatDate(hotel.businessDate)}</TableCell>
              </TableRow>
            ))}
          </TableBody>
        </Table>
      </TableContainer>
      {creating && <CreateHotelDialog onClose={() => setCreating(false)} onCreated={() => { setCreating(false); setCreated(true) }} />}
    </Stack>
  )
}

function CreateHotelDialog({ onClose, onCreated }: { onClose: () => void; onCreated: () => void }) {
  const { t } = useTranslation()
  const queryClient = useQueryClient()
  const [code, setCode] = useState('')
  const [name, setName] = useState('')
  const [legalName, setLegalName] = useState('')
  const [currencyCode, setCurrencyCode] = useState('XOF')
  const [timezone, setTimezone] = useState('Africa/Dakar')
  const [language, setLanguage] = useState<'fr' | 'en'>('fr')
  const [businessDate, setBusinessDate] = useState('')

  const create = useMutation({
    mutationFn: () => createHotel({ code, name, legalName: legalName || undefined, currencyCode, timezone, language, businessDate: businessDate || undefined }),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ['hotels'] })
      onCreated()
    },
  })
  const errors = fieldErrors(create.error)

  return (
    <Dialog open onClose={onClose} maxWidth="sm" fullWidth>
      <DialogTitle>{t('hotels.create')}</DialogTitle>
      <DialogContent>
        <Stack spacing={2} sx={{ pt: 1 }}>
          {create.error && Object.keys(errors).length === 0 ? <ErrorAlert error={create.error} /> : null}
          <TextField label={t('hotels.code')} value={code} onChange={(e) => setCode(e.target.value.toUpperCase())} required
            error={!!errors.code} helperText={errors.code ?? t('hotels.codeHelp')} />
          <TextField label={t('hotels.name')} value={name} onChange={(e) => setName(e.target.value)} required />
          <TextField label={t('hotels.legalName')} value={legalName} onChange={(e) => setLegalName(e.target.value)} />
          <Stack direction={{ xs: 'column', sm: 'row' }} spacing={2}>
            <TextField label={t('hotels.currency')} value={currencyCode} onChange={(e) => setCurrencyCode(e.target.value.toUpperCase())}
              error={!!errors.currencyCode} helperText={errors.currencyCode} fullWidth />
            <TextField label={t('hotels.timezone')} value={timezone} onChange={(e) => setTimezone(e.target.value)}
              error={!!errors.timezone} helperText={errors.timezone} fullWidth />
            <TextField select label={t('hotels.language')} value={language} onChange={(e) => setLanguage(e.target.value as 'fr' | 'en')} sx={{ minWidth: 130 }}>
              <MenuItem value="fr">{t('profile.languages.fr')}</MenuItem>
              <MenuItem value="en">{t('profile.languages.en')}</MenuItem>
            </TextField>
          </Stack>
          <TextField type="date" label={t('hotels.businessDate')} value={businessDate} onChange={(e) => setBusinessDate(e.target.value)}
            slotProps={{ inputLabel: { shrink: true } }} />
        </Stack>
      </DialogContent>
      <DialogActions>
        <Button onClick={onClose}>{t('common.cancel')}</Button>
        <Button variant="contained" disabled={create.isPending || !code || !name} onClick={() => create.mutate()}>{t('settings.save')}</Button>
      </DialogActions>
    </Dialog>
  )
}
