import AddIcon from '@mui/icons-material/Add'
import DeleteIcon from '@mui/icons-material/Delete'
import {
  Avatar,
  Box,
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
  Grid,
  IconButton,
  Stack,
  TextField,
  Typography,
} from '@mui/material'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useEffect, useState } from 'react'
import { useTranslation } from 'react-i18next'

import { formatAmount } from '../app/format'
import type { RoomType, RoomTypeInput } from '../api/rooms'
import { deleteRoomTypePhoto, fetchAmenities, fetchRoomTypePhotoUrl, fetchRoomTypes, saveRoomType, uploadRoomTypePhoto } from '../api/rooms'
import { fieldErrors } from '../api/http'
import { ErrorAlert } from '../components/ErrorAlert'

/** Types de chambres (US-ROOM-01) : capacités, prix de base, équipements, photos. */
export function RoomTypesPanel({ hotelId, currency, canCreate, canEdit }: { hotelId: string; currency: string; canCreate: boolean; canEdit: boolean }) {
  const { t } = useTranslation()
  const types = useQuery({ queryKey: ['room-types', hotelId], queryFn: () => fetchRoomTypes(hotelId) })
  const amenities = useQuery({ queryKey: ['amenities', hotelId], queryFn: () => fetchAmenities(hotelId) })
  const [editing, setEditing] = useState<RoomType | null | undefined>(undefined)
  const amenityName = (id: string) => amenities.data?.find((a) => a.id === id)?.name ?? ''

  return (
    <Stack spacing={2}>
      {canCreate && (
        <Button variant="contained" startIcon={<AddIcon />} sx={{ alignSelf: 'flex-end' }} onClick={() => setEditing(null)}>
          {t('rooms.types.add')}
        </Button>
      )}
      {types.error ? <ErrorAlert error={types.error} /> : null}
      <Grid container spacing={2}>
        {(types.data ?? []).map((type) => (
          <Grid key={type.id} size={{ xs: 12, sm: 6, lg: 4 }}>
            <Card variant="outlined" sx={{ height: '100%', opacity: type.active ? 1 : 0.6 }}>
              <CardContent>
                <Stack direction="row" spacing={1} sx={{ alignItems: 'center', mb: 1, flexWrap: 'wrap', gap: 0.5 }}>
                  <Typography variant="h6" component="h3">{type.name}</Typography>
                  <Chip size="small" label={type.code} />
                  {!type.active && <Chip size="small" label={t('settings.inactive')} />}
                </Stack>
                <Typography variant="h5">{formatAmount(type.basePrice, currency)}</Typography>
                <Typography variant="body2" color="text.secondary" gutterBottom>
                  {t('rooms.types.capacity', { base: type.baseOccupancy, max: type.maxOccupancy })} · {t('rooms.types.rooms', { count: type.activeRoomCount })}
                </Typography>
                {type.description && <Typography variant="body2" gutterBottom>{type.description}</Typography>}
                <Stack direction="row" sx={{ flexWrap: 'wrap', gap: 0.5 }}>
                  {type.amenityIds.map((id) => <Chip key={id} size="small" variant="outlined" label={amenityName(id)} />)}
                </Stack>
              </CardContent>
              {canEdit && (
                <CardActions>
                  <Button size="small" onClick={() => setEditing(type)}>{t('settings.edit')}</Button>
                </CardActions>
              )}
            </Card>
          </Grid>
        ))}
      </Grid>
      {editing !== undefined && (
        <RoomTypeDialog hotelId={hotelId} type={editing} amenities={amenities.data ?? []} onClose={() => setEditing(undefined)} />
      )}
    </Stack>
  )
}

function RoomTypeDialog({ hotelId, type, amenities, onClose }: {
  hotelId: string; type: RoomType | null; amenities: Array<{ id: string; name: string; active: boolean }>; onClose: () => void
}) {
  const { t } = useTranslation()
  const queryClient = useQueryClient()
  const [values, setValues] = useState<RoomTypeInput>(type ?? {
    code: '', name: '', description: '', baseOccupancy: 2, maxOccupancy: 2, maxAdults: 2, maxChildren: 0, basePrice: 0,
    sortOrder: 10, active: true, amenityIds: [],
  })
  const set = <K extends keyof RoomTypeInput>(key: K, value: RoomTypeInput[K]) => setValues((current) => ({ ...current, [key]: value }))
  const refresh = () => queryClient.invalidateQueries({ queryKey: ['room-types', hotelId] })
  const save = useMutation({
    mutationFn: () => saveRoomType(hotelId, type?.id ?? null, { ...values, version: type?.version }),
    onSuccess: () => { void refresh(); onClose() },
  })
  const errors = fieldErrors(save.error)
  const number = (key: keyof RoomTypeInput, label: string) => (
    <TextField type="number" label={label} value={values[key] as number} onChange={(e) => set(key, Number(e.target.value) as never)}
      error={!!errors[key]} helperText={errors[key]} fullWidth />
  )

  return (
    <Dialog open onClose={onClose} maxWidth="sm" fullWidth>
      <DialogTitle>{type ? `${t('rooms.types.edit')} — ${type.name}` : t('rooms.types.add')}</DialogTitle>
      <DialogContent>
        <Stack spacing={2} sx={{ pt: 1 }}>
          {save.error && Object.keys(errors).length === 0 ? <ErrorAlert error={save.error} /> : null}
          <Stack direction="row" spacing={2}>
            <TextField label={t('rooms.types.code')} value={values.code} disabled={!!type} sx={{ width: 120 }}
              onChange={(e) => set('code', e.target.value.toUpperCase())} />
            <TextField label={t('rooms.types.name')} value={values.name} onChange={(e) => set('name', e.target.value)} fullWidth />
          </Stack>
          <TextField label={t('rooms.types.description')} value={values.description ?? ''} multiline minRows={2}
            onChange={(e) => set('description', e.target.value)} />
          <Stack direction={{ xs: 'column', sm: 'row' }} spacing={2}>
            {number('baseOccupancy', t('rooms.types.baseOccupancy'))}
            {number('maxOccupancy', t('rooms.types.maxOccupancy'))}
            {number('maxAdults', t('rooms.types.maxAdults'))}
            {number('maxChildren', t('rooms.types.maxChildren'))}
          </Stack>
          <Stack direction="row" spacing={2}>
            {number('basePrice', t('rooms.types.basePrice'))}
            {number('sortOrder', t('settings.order'))}
          </Stack>
          <Typography variant="subtitle2">{t('rooms.types.amenities')}</Typography>
          <Box sx={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fill, minmax(160px, 1fr))' }}>
            {amenities.filter((a) => a.active || values.amenityIds.includes(a.id)).map((amenity) => (
              <FormControlLabel key={amenity.id} label={amenity.name}
                control={<Checkbox size="small" checked={values.amenityIds.includes(amenity.id)}
                  onChange={(e) => set('amenityIds', e.target.checked
                    ? [...values.amenityIds, amenity.id]
                    : values.amenityIds.filter((id) => id !== amenity.id))} />} />
            ))}
          </Box>
          {type && <PhotoEditor hotelId={hotelId} type={type} onChanged={() => void refresh()} />}
          {type && (
            <FormControlLabel label={t('rooms.types.activeType')}
              control={<Checkbox checked={values.active} onChange={(e) => set('active', e.target.checked)} />} />
          )}
        </Stack>
      </DialogContent>
      <DialogActions>
        <Button onClick={onClose}>{t('common.cancel')}</Button>
        <Button variant="contained" disabled={save.isPending || !values.name.trim() || !values.code} onClick={() => save.mutate()}>
          {t('settings.save')}
        </Button>
      </DialogActions>
    </Dialog>
  )
}

function PhotoEditor({ hotelId, type, onChanged }: { hotelId: string; type: RoomType; onChanged: () => void }) {
  const { t } = useTranslation()
  const [photoIds, setPhotoIds] = useState(type.photoIds)
  const upload = useMutation({
    mutationFn: (file: File) => uploadRoomTypePhoto(hotelId, type.id, file),
    onSuccess: async () => {
      const refreshed = (await fetchRoomTypes(hotelId)).find((item) => item.id === type.id)
      setPhotoIds(refreshed?.photoIds ?? [])
      onChanged()
    },
  })
  const remove = useMutation({
    mutationFn: (photoId: string) => deleteRoomTypePhoto(hotelId, type.id, photoId),
    onSuccess: (_, photoId) => { setPhotoIds((ids) => ids.filter((id) => id !== photoId)); onChanged() },
  })

  return (
    <Stack spacing={1}>
      <Typography variant="subtitle2">{t('rooms.types.photos')}</Typography>
      {upload.error ? <ErrorAlert error={upload.error} /> : null}
      <Stack direction="row" sx={{ flexWrap: 'wrap', gap: 1, alignItems: 'center' }}>
        {photoIds.map((photoId) => (
          <Box key={photoId} sx={{ position: 'relative' }}>
            <Photo hotelId={hotelId} typeId={type.id} photoId={photoId} />
            <IconButton size="small" aria-label={t('roles.delete')} onClick={() => remove.mutate(photoId)}
              sx={{ position: 'absolute', top: 0, right: 0, bgcolor: 'background.paper' }}>
              <DeleteIcon fontSize="small" />
            </IconButton>
          </Box>
        ))}
        <Button component="label" size="small" variant="outlined" disabled={upload.isPending}>
          {t('rooms.types.addPhoto')}
          <input hidden type="file" accept="image/png,image/jpeg"
            onChange={(e) => { const file = e.target.files?.[0]; if (file) upload.mutate(file); e.target.value = '' }} />
        </Button>
      </Stack>
    </Stack>
  )
}

function Photo({ hotelId, typeId, photoId }: { hotelId: string; typeId: string; photoId: string }) {
  const photo = useQuery({
    queryKey: ['room-type-photo', photoId],
    queryFn: () => fetchRoomTypePhotoUrl(hotelId, typeId, photoId),
    staleTime: Infinity,
  })
  useEffect(() => () => { if (photo.data) URL.revokeObjectURL(photo.data) }, [photo.data])
  return <Avatar variant="rounded" src={photo.data} sx={{ width: 96, height: 72 }} />
}
