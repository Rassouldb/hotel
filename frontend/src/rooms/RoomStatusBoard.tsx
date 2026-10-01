import AccessibleIcon from '@mui/icons-material/Accessible'
import {
  Alert,
  Box,
  Button,
  Card,
  CardActionArea,
  Chip,
  Drawer,
  List,
  ListItem,
  ListItemText,
  MenuItem,
  Stack,
  TextField,
  Typography,
} from '@mui/material'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useMemo, useState } from 'react'
import { useTranslation } from 'react-i18next'

import { formatDate } from '../app/format'
import type { HousekeepingStatus, Room } from '../api/rooms'
import { changeHousekeepingStatus, fetchRoomHistory, fetchRooms } from '../api/rooms'
import { useAuth } from '../auth/AuthContext'
import { ErrorAlert } from '../components/ErrorAlert'
import { housekeepingColor, nextHousekeepingStatuses, ROOM_STATES, roomState, stateColor, type RoomState } from './roomStatus'

/** État des chambres (US-ROOM-03) : tuiles par étage, compteurs, mise à jour du statut ménage (US-ROOM-04). */
export function RoomStatusBoard({ hotelId }: { hotelId: string }) {
  const { t } = useTranslation()
  const rooms = useQuery({ queryKey: ['rooms', hotelId], queryFn: () => fetchRooms(hotelId) })
  const [typeFilter, setTypeFilter] = useState('')
  const [stateFilter, setStateFilter] = useState<RoomState | ''>('')
  const [selectedId, setSelectedId] = useState<string | null>(null)

  const activeRooms = useMemo(() => (rooms.data ?? []).filter((room) => room.active), [rooms.data])
  const counters = useMemo(() => {
    const counts = Object.fromEntries(ROOM_STATES.map((state) => [state, 0])) as Record<RoomState, number>
    activeRooms.forEach((room) => { counts[roomState(room)] += 1 })
    return counts
  }, [activeRooms])
  const types = [...new Map(activeRooms.map((room) => [room.roomTypeId, room.roomTypeName ?? ''])).entries()]
  const visible = activeRooms.filter((room) =>
    (!typeFilter || room.roomTypeId === typeFilter) && (!stateFilter || roomState(room) === stateFilter))
  const floors = [...new Set(visible.map((room) => room.floor))].sort((a, b) => (a ?? 999) - (b ?? 999))
  const selected = rooms.data?.find((room) => room.id === selectedId) ?? null

  if (rooms.error) {
    return <ErrorAlert error={rooms.error} onRetry={() => void rooms.refetch()} />
  }

  return (
    <Stack spacing={2}>
      <Stack direction="row" sx={{ flexWrap: 'wrap', gap: 1 }}>
        {ROOM_STATES.map((state) => (
          <Chip
            key={state}
            clickable
            onClick={() => setStateFilter(stateFilter === state ? '' : state)}
            variant={stateFilter === state ? 'filled' : 'outlined'}
            label={`${t(`rooms.counters.${state}`)} : ${counters[state]}`}
            sx={{ borderColor: stateColor[state], borderWidth: 2 }}
          />
        ))}
      </Stack>
      <TextField select size="small" label={t('rooms.filters.type')} value={typeFilter} onChange={(e) => setTypeFilter(e.target.value)}
        sx={{ maxWidth: 260 }}>
        <MenuItem value="">{t('rooms.filters.all')}</MenuItem>
        {types.map(([id, name]) => <MenuItem key={id} value={id}>{name}</MenuItem>)}
      </TextField>

      {floors.map((floor) => (
        <Box key={floor ?? 'none'}>
          <Typography variant="subtitle1" sx={{ fontWeight: 600, mb: 1 }}>
            {floor == null ? t('rooms.noFloor') : t('rooms.floor', { floor })}
          </Typography>
          <Box sx={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fill, minmax(120px, 1fr))', gap: 1 }}>
            {visible.filter((room) => room.floor === floor).map((room) => (
              <RoomTile key={room.id} room={room} onClick={() => setSelectedId(room.id)} />
            ))}
          </Box>
        </Box>
      ))}

      <Drawer anchor="right" open={selected !== null} onClose={() => setSelectedId(null)}>
        {selected && <RoomDetails hotelId={hotelId} room={selected} />}
      </Drawer>
    </Stack>
  )
}

function RoomTile({ room, onClick }: { room: Room; onClick: () => void }) {
  const { t } = useTranslation()
  const state = roomState(room)

  return (
    <Card variant="outlined" sx={{ borderLeft: 6, borderLeftColor: stateColor[state] }}>
      <CardActionArea onClick={onClick} sx={{ p: 1.5 }} aria-label={t('rooms.room', { number: room.number })}>
        <Stack direction="row" sx={{ justifyContent: 'space-between', alignItems: 'center' }}>
          <Typography variant="h6" component="span">{room.number}</Typography>
          {room.accessible && <AccessibleIcon fontSize="small" color="action" />}
        </Stack>
        <Typography variant="caption" color="text.secondary" noWrap component="div">{room.roomTypeName}</Typography>
        <Typography variant="body2" noWrap>{t(`rooms.counters.${state}`)}</Typography>
      </CardActionArea>
    </Card>
  )
}

function RoomDetails({ hotelId, room }: { hotelId: string; room: Room }) {
  const { t } = useTranslation()
  const { can } = useAuth()
  const queryClient = useQueryClient()
  const [dirtyReason, setDirtyReason] = useState('')
  const history = useQuery({ queryKey: ['room-history', room.id], queryFn: () => fetchRoomHistory(hotelId, room.id) })
  const change = useMutation({
    mutationFn: ({ status, reason }: { status: HousekeepingStatus; reason?: string }) =>
      changeHousekeepingStatus(hotelId, room.id, status, reason),
    onSuccess: () => {
      setDirtyReason('')
      void queryClient.invalidateQueries({ queryKey: ['rooms', hotelId] })
      void queryClient.invalidateQueries({ queryKey: ['room-history', room.id] })
    },
  })
  const canUpdate = can('HK_STATUS_UPDATE', hotelId)
  const label = (dimension: string, value: string | null) => {
    if (!value) return '—'
    const key = dimension === 'HOUSEKEEPING' ? 'housekeeping' : dimension === 'OCCUPANCY' ? 'occupancy' : 'blockTypes'
    return t(`rooms.${key}.${value}`, { defaultValue: value })
  }

  return (
    <Box sx={{ width: { xs: '100vw', sm: 400 }, p: 3 }} role="dialog" aria-label={t('rooms.room', { number: room.number })}>
      <Stack spacing={2}>
        <Typography variant="h5" component="h2">{t('rooms.room', { number: room.number })}</Typography>
        <Typography color="text.secondary">{room.roomTypeName}{room.features.length > 0 ? ` · ${room.features.join(', ')}` : ''}</Typography>
        <Stack direction="row" sx={{ flexWrap: 'wrap', gap: 1 }}>
          <Chip label={t(`rooms.occupancy.${room.occupancyStatus}`)} color={room.occupancyStatus === 'OCCUPIED' ? 'primary' : 'default'} />
          <Chip label={t(`rooms.housekeeping.${room.housekeepingStatus}`)} color={housekeepingColor[room.housekeepingStatus]} />
          <Chip label={t(`rooms.technical.${room.technicalStatus}`)} variant="outlined" />
          {!room.saleable && <Chip label={t('rooms.notSaleable')} variant="outlined" color="warning" />}
        </Stack>
        {room.currentBlock && (
          <Alert severity="warning">
            {t('rooms.blockedUntil', { date: formatDate(room.currentBlock.endDate), reason: room.currentBlock.reason })}
          </Alert>
        )}
        {room.notes && <Typography variant="body2">{room.notes}</Typography>}

        {canUpdate && (
          <Stack spacing={1}>
            {change.error ? <ErrorAlert error={change.error} /> : null}
            {nextHousekeepingStatuses(room.housekeepingStatus)
              .filter((status) => status !== 'INSPECTED' || can('HK_INSPECT', hotelId))
              .map((status) => status === 'DIRTY' ? (
                <Stack key={status} direction="row" spacing={1}>
                  <TextField size="small" label={t('rooms.reasonDirty')} value={dirtyReason} onChange={(e) => setDirtyReason(e.target.value)} fullWidth />
                  <Button color="error" variant="outlined" disabled={!dirtyReason.trim() || change.isPending}
                    onClick={() => change.mutate({ status, reason: dirtyReason })}>
                    {t(`rooms.housekeeping.${status}`)}
                  </Button>
                </Stack>
              ) : (
                <Button key={status} variant="contained" color={housekeepingColor[status]} disabled={change.isPending}
                  onClick={() => change.mutate({ status })}>
                  {t('rooms.markAs', { status: t(`rooms.housekeeping.${status}`) })}
                </Button>
              ))}
          </Stack>
        )}

        <Typography variant="subtitle1" sx={{ fontWeight: 600 }}>{t('rooms.history')}</Typography>
        {history.data?.length === 0 && <Typography color="text.secondary">{t('rooms.noHistory')}</Typography>}
        <List dense disablePadding>
          {(history.data ?? []).map((entry) => (
            <ListItem key={entry.id} disableGutters>
              <ListItemText
                primary={`${t(`rooms.dimensions.${entry.dimension}`)} : ${label(entry.dimension, entry.oldValue)} → ${label(entry.dimension, entry.newValue)}`}
                secondary={`${new Intl.DateTimeFormat('fr-FR', { dateStyle: 'short', timeStyle: 'short' }).format(new Date(entry.occurredAt))}`
                  + `${entry.userName ? ` · ${entry.userName}` : ''}${entry.reason ? ` · ${entry.reason}` : ''}`}
              />
            </ListItem>
          ))}
        </List>
      </Stack>
    </Box>
  )
}
