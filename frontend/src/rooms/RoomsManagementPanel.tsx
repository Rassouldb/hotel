import AddIcon from '@mui/icons-material/Add'
import PlaylistAddIcon from '@mui/icons-material/PlaylistAdd'
import {
  Alert,
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

import type { Room, RoomType } from '../api/rooms'
import { createRoom, createRooms, fetchRooms, fetchRoomTypes, updateRoom } from '../api/rooms'
import { fieldErrors } from '../api/http'
import { ErrorAlert } from '../components/ErrorAlert'

/** Gestion des chambres (US-ROOM-02) : liste, création unitaire ou en série, modification. */
export function RoomsManagementPanel({ hotelId, canCreate, canEdit }: { hotelId: string; canCreate: boolean; canEdit: boolean }) {
  const { t } = useTranslation()
  const rooms = useQuery({ queryKey: ['rooms', hotelId], queryFn: () => fetchRooms(hotelId) })
  const types = useQuery({ queryKey: ['room-types', hotelId], queryFn: () => fetchRoomTypes(hotelId) })
  const [editing, setEditing] = useState<Room | null | undefined>(undefined)
  const [series, setSeries] = useState(false)
  const [message, setMessage] = useState<string | null>(null)
  const activeTypes = (types.data ?? []).filter((type) => type.active)

  return (
    <Stack spacing={2}>
      {canCreate && (
        <Stack direction="row" spacing={1} sx={{ justifyContent: 'flex-end' }}>
          <Button startIcon={<PlaylistAddIcon />} variant="outlined" onClick={() => { setMessage(null); setSeries(true) }}>{t('rooms.addSeries')}</Button>
          <Button startIcon={<AddIcon />} variant="contained" onClick={() => { setMessage(null); setEditing(null) }}>{t('rooms.add')}</Button>
        </Stack>
      )}
      {message && <Alert severity="success">{message}</Alert>}
      {rooms.error ? <ErrorAlert error={rooms.error} /> : null}
      <TableContainer component={Paper} variant="outlined">
        <Table size="small">
          <TableHead>
            <TableRow>
              <TableCell>{t('rooms.number')}</TableCell>
              <TableCell>{t('rooms.type')}</TableCell>
              <TableCell>{t('rooms.filters.floor')}</TableCell>
              <TableCell>{t('rooms.building')}</TableCell>
              <TableCell>{t('rooms.features')}</TableCell>
              <TableCell>{t('rooms.filters.status')}</TableCell>
              {canEdit && <TableCell />}
            </TableRow>
          </TableHead>
          <TableBody>
            {(rooms.data ?? []).map((room) => (
              <TableRow key={room.id} hover sx={{ opacity: room.active ? 1 : 0.55 }}>
                <TableCell sx={{ fontWeight: 600 }}>{room.number}</TableCell>
                <TableCell>{room.roomTypeName}</TableCell>
                <TableCell>{room.floor ?? '—'}</TableCell>
                <TableCell>{room.building ?? '—'}</TableCell>
                <TableCell>{[room.accessible ? t('rooms.accessible') : null, ...room.features].filter(Boolean).join(', ')}</TableCell>
                <TableCell>
                  {room.active ? <Chip size="small" color={room.saleable ? 'success' : 'warning'} label={room.saleable ? t('settings.active') : t('rooms.notSaleable')} />
                    : <Chip size="small" label={t('rooms.inactive')} />}
                </TableCell>
                {canEdit && <TableCell align="right"><Button size="small" onClick={() => setEditing(room)}>{t('settings.edit')}</Button></TableCell>}
              </TableRow>
            ))}
          </TableBody>
        </Table>
      </TableContainer>
      {editing !== undefined && (
        <RoomDialog hotelId={hotelId} room={editing} types={activeTypes} onClose={() => setEditing(undefined)} />
      )}
      {series && (
        <SeriesDialog hotelId={hotelId} types={activeTypes} onClose={() => setSeries(false)}
          onCreated={(count) => { setSeries(false); setMessage(t('rooms.created', { count })) }} />
      )}
    </Stack>
  )
}

function RoomDialog({ hotelId, room, types, onClose }: { hotelId: string; room: Room | null; types: RoomType[]; onClose: () => void }) {
  const { t } = useTranslation()
  const queryClient = useQueryClient()
  const [number, setNumber] = useState(room?.number ?? '')
  const [roomTypeId, setRoomTypeId] = useState(room?.roomTypeId ?? types[0]?.id ?? '')
  const [floor, setFloor] = useState(room?.floor?.toString() ?? '')
  const [building, setBuilding] = useState(room?.building ?? '')
  const [accessible, setAccessible] = useState(room?.accessible ?? false)
  const [features, setFeatures] = useState(room?.features.join(', ') ?? '')
  const [notes, setNotes] = useState(room?.notes ?? '')
  const [active, setActive] = useState(room?.active ?? true)

  const save = useMutation({
    mutationFn: () => {
      const input = {
        roomTypeId, floor: floor === '' ? null : Number(floor), building: building || null, accessible,
        features: features.split(',').map((f) => f.trim()).filter(Boolean), notes: notes || null, active,
      }
      return room ? updateRoom(hotelId, room.id, { ...input, version: room.version }) : createRoom(hotelId, { ...input, number })
    },
    onSuccess: () => { void queryClient.invalidateQueries({ queryKey: ['rooms', hotelId] }); onClose() },
  })
  const errors = fieldErrors(save.error)

  return (
    <Dialog open onClose={onClose} maxWidth="sm" fullWidth>
      <DialogTitle>{room ? `${t('rooms.edit')} ${room.number}` : t('rooms.add')}</DialogTitle>
      <DialogContent>
        <Stack spacing={2} sx={{ pt: 1 }}>
          {save.error && Object.keys(errors).length === 0 ? <ErrorAlert error={save.error} /> : null}
          <Stack direction="row" spacing={2}>
            <TextField label={t('rooms.number')} value={number} onChange={(e) => setNumber(e.target.value)} disabled={!!room} sx={{ width: 140 }} />
            <TextField select label={t('rooms.type')} value={roomTypeId} onChange={(e) => setRoomTypeId(e.target.value)} fullWidth>
              {types.map((type) => <MenuItem key={type.id} value={type.id}>{type.name}</MenuItem>)}
            </TextField>
          </Stack>
          <Stack direction="row" spacing={2}>
            <TextField type="number" label={t('rooms.filters.floor')} value={floor} onChange={(e) => setFloor(e.target.value)} sx={{ width: 140 }} />
            <TextField label={t('rooms.building')} value={building} onChange={(e) => setBuilding(e.target.value)} fullWidth />
          </Stack>
          <TextField label={t('rooms.features')} helperText={t('rooms.featuresHelp')} value={features} onChange={(e) => setFeatures(e.target.value)} />
          <TextField label={t('rooms.notes')} value={notes} onChange={(e) => setNotes(e.target.value)} multiline minRows={2} />
          <FormControlLabel label={t('rooms.accessible')} control={<Checkbox checked={accessible} onChange={(e) => setAccessible(e.target.checked)} />} />
          {room && <FormControlLabel label={t('rooms.activeRoom')} control={<Checkbox checked={active} onChange={(e) => setActive(e.target.checked)} />} />}
        </Stack>
      </DialogContent>
      <DialogActions>
        <Button onClick={onClose}>{t('common.cancel')}</Button>
        <Button variant="contained" disabled={save.isPending || !number || !roomTypeId} onClick={() => save.mutate()}>{t('settings.save')}</Button>
      </DialogActions>
    </Dialog>
  )
}

function SeriesDialog({ hotelId, types, onClose, onCreated }: {
  hotelId: string; types: RoomType[]; onClose: () => void; onCreated: (count: number) => void
}) {
  const { t } = useTranslation()
  const queryClient = useQueryClient()
  const [prefix, setPrefix] = useState('')
  const [from, setFrom] = useState('')
  const [to, setTo] = useState('')
  const [roomTypeId, setRoomTypeId] = useState(types[0]?.id ?? '')
  const [floor, setFloor] = useState('')
  const [building, setBuilding] = useState('')
  const create = useMutation({
    mutationFn: () => createRooms(hotelId, {
      prefix, from: Number(from), to: Number(to), roomTypeId, floor: floor === '' ? null : Number(floor), building: building || null,
    }),
    onSuccess: (rooms) => { void queryClient.invalidateQueries({ queryKey: ['rooms', hotelId] }); onCreated(rooms.length) },
  })

  return (
    <Dialog open onClose={onClose} maxWidth="sm" fullWidth>
      <DialogTitle>{t('rooms.addSeries')}</DialogTitle>
      <DialogContent>
        <Stack spacing={2} sx={{ pt: 1 }}>
          <Alert severity="info">{t('rooms.seriesHelp')}</Alert>
          {create.error ? <ErrorAlert error={create.error} /> : null}
          <Stack direction="row" spacing={2}>
            <TextField label={t('rooms.prefix')} value={prefix} onChange={(e) => setPrefix(e.target.value)} sx={{ width: 160 }} />
            <TextField type="number" label={t('rooms.from')} value={from} onChange={(e) => setFrom(e.target.value)} fullWidth />
            <TextField type="number" label={t('rooms.to')} value={to} onChange={(e) => setTo(e.target.value)} fullWidth />
          </Stack>
          <TextField select label={t('rooms.type')} value={roomTypeId} onChange={(e) => setRoomTypeId(e.target.value)}>
            {types.map((type) => <MenuItem key={type.id} value={type.id}>{type.name}</MenuItem>)}
          </TextField>
          <Stack direction="row" spacing={2}>
            <TextField type="number" label={t('rooms.filters.floor')} value={floor} onChange={(e) => setFloor(e.target.value)} sx={{ width: 140 }} />
            <TextField label={t('rooms.building')} value={building} onChange={(e) => setBuilding(e.target.value)} fullWidth />
          </Stack>
        </Stack>
      </DialogContent>
      <DialogActions>
        <Button onClick={onClose}>{t('common.cancel')}</Button>
        <Button variant="contained" disabled={create.isPending || from === '' || to === '' || !roomTypeId} onClick={() => create.mutate()}>
          {t('settings.save')}
        </Button>
      </DialogActions>
    </Dialog>
  )
}
