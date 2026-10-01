import AddIcon from '@mui/icons-material/Add'
import {
  Button,
  Checkbox,
  Chip,
  Dialog,
  DialogActions,
  DialogContent,
  DialogTitle,
  FormControlLabel,
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
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'
import { useTranslation } from 'react-i18next'

import { ErrorAlert } from '../components/ErrorAlert'

/** Description d'un champ éditable d'une liste de référence. */
export interface FieldSpec {
  key: string
  label: string
  kind: 'text' | 'number' | 'boolean' | 'select'
  options?: Array<{ value: string; label: string }>
  /** Modifiable seulement à la création (code, type, domaine). */
  createOnly?: boolean
  /** Affiché dans le tableau ; la valeur est formatée par `display` si fourni. */
  column?: boolean
  display?: (value: unknown) => string
}

interface ReferenceListPanelProps<T extends { id: string; active: boolean }> {
  queryKey: unknown[]
  load: () => Promise<T[]>
  save: (item: Record<string, unknown>) => Promise<unknown>
  fields: FieldSpec[]
  addLabel: string
  emptyItem: Record<string, unknown>
  canEdit: boolean
}

/**
 * Liste de référence éditable (moyens de paiement, canaux, motifs) : tableau + fenêtre d'édition.
 * Les éléments ne sont jamais supprimés : on les désactive.
 */
export function ReferenceListPanel<T extends { id: string; active: boolean }>({
  queryKey, load, save, fields, addLabel, emptyItem, canEdit,
}: ReferenceListPanelProps<T>) {
  const { t } = useTranslation()
  const queryClient = useQueryClient()
  const items = useQuery({ queryKey, queryFn: load })
  const [editing, setEditing] = useState<Record<string, unknown> | null>(null)
  const mutation = useMutation({
    mutationFn: save,
    onSuccess: () => {
      setEditing(null)
      void queryClient.invalidateQueries({ queryKey })
    },
  })
  const columns = fields.filter((field) => field.column)
  const creating = editing !== null && !editing.id

  const display = (field: FieldSpec, value: unknown) => {
    if (field.display) {
      return field.display(value)
    }
    if (field.kind === 'boolean') {
      return value ? '✓' : ''
    }
    if (field.kind === 'select') {
      return field.options?.find((option) => option.value === value)?.label ?? String(value ?? '')
    }
    return value == null ? '' : String(value)
  }

  return (
    <Stack spacing={2}>
      {canEdit && (
        <Button variant="contained" startIcon={<AddIcon />} sx={{ alignSelf: 'flex-end' }} onClick={() => { mutation.reset(); setEditing({ ...emptyItem }) }}>
          {addLabel}
        </Button>
      )}
      {items.error ? <ErrorAlert error={items.error} /> : null}
      <TableContainer component={Paper} variant="outlined">
        <Table size="small">
          <TableHead>
            <TableRow>
              {columns.map((field) => <TableCell key={field.key}>{field.label}</TableCell>)}
              <TableCell>{t('settings.active')}</TableCell>
              {canEdit && <TableCell />}
            </TableRow>
          </TableHead>
          <TableBody>
            {(items.data ?? []).map((item) => {
              const record = item as unknown as Record<string, unknown>
              return (
                <TableRow key={item.id} hover sx={{ opacity: item.active ? 1 : 0.55 }}>
                  {columns.map((field) => <TableCell key={field.key}>{display(field, record[field.key])}</TableCell>)}
                  <TableCell>
                    <Chip size="small" color={item.active ? 'success' : 'default'} label={item.active ? t('settings.active') : t('settings.inactive')} />
                  </TableCell>
                  {canEdit && (
                    <TableCell align="right">
                      <Button size="small" onClick={() => { mutation.reset(); setEditing({ ...record }) }}>{t('settings.edit')}</Button>
                    </TableCell>
                  )}
                </TableRow>
              )
            })}
          </TableBody>
        </Table>
      </TableContainer>

      <Dialog open={editing !== null} onClose={() => setEditing(null)} maxWidth="xs" fullWidth>
        <DialogTitle>{creating ? addLabel : t('settings.edit')}</DialogTitle>
        <DialogContent>
          <Stack spacing={2} sx={{ pt: 1 }}>
            {mutation.error ? <ErrorAlert error={mutation.error} /> : null}
            {editing && fields.map((field) => {
              const value = editing[field.key]
              const disabled = field.createOnly && !creating
              const update = (next: unknown) => setEditing((current) => ({ ...current, [field.key]: next }))
              if (field.kind === 'boolean') {
                return (
                  <FormControlLabel key={field.key} label={field.label} disabled={disabled}
                    control={<Checkbox checked={!!value} onChange={(e) => update(e.target.checked)} />} />
                )
              }
              if (field.kind === 'select') {
                return (
                  <TextField key={field.key} select label={field.label} value={value ?? ''} disabled={disabled}
                    onChange={(e) => update(e.target.value)}>
                    {field.options?.map((option) => <MenuItem key={option.value} value={option.value}>{option.label}</MenuItem>)}
                  </TextField>
                )
              }
              return (
                <TextField key={field.key} label={field.label} type={field.kind === 'number' ? 'number' : 'text'} disabled={disabled}
                  value={value ?? ''}
                  onChange={(e) => update(field.kind === 'number' ? (e.target.value === '' ? null : Number(e.target.value)) : e.target.value)} />
              )
            })}
            {editing && (
              <FormControlLabel label={t('settings.active')}
                control={<Checkbox checked={!!editing.active} onChange={(e) => setEditing({ ...editing, active: e.target.checked })} />} />
            )}
          </Stack>
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setEditing(null)}>{t('common.cancel')}</Button>
          <Button variant="contained" disabled={mutation.isPending} onClick={() => editing && mutation.mutate(editing)}>{t('settings.save')}</Button>
        </DialogActions>
      </Dialog>
    </Stack>
  )
}
