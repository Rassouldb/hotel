import AddIcon from '@mui/icons-material/Add'
import {
  Alert,
  Autocomplete,
  Button,
  Chip,
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
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'
import { useTranslation } from 'react-i18next'

import { formatDate } from '../app/format'
import { fetchReasons } from '../api/configuration'
import type { BlockType, RoomBlock } from '../api/rooms'
import { createBlock, deleteBlock, fetchBlocks, fetchRooms, releaseBlock } from '../api/rooms'
import { ConfirmDialog } from '../components/ConfirmDialog'
import { ErrorAlert } from '../components/ErrorAlert'

const BLOCK_TYPES: BlockType[] = ['MAINTENANCE', 'OUT_OF_ORDER', 'HOUSE_USE']

/** Blocages de chambres en cours et à venir (US-ROOM-05). */
export function BlocksPanel({ hotelId, businessDate, canManage }: { hotelId: string; businessDate: string; canManage: boolean }) {
  const { t } = useTranslation()
  const queryClient = useQueryClient()
  const blocks = useQuery({ queryKey: ['room-blocks', hotelId], queryFn: () => fetchBlocks(hotelId) })
  const [creating, setCreating] = useState(false)
  const [pending, setPending] = useState<{ kind: 'release' | 'delete'; block: RoomBlock } | null>(null)
  const refresh = () => {
    void queryClient.invalidateQueries({ queryKey: ['room-blocks', hotelId] })
    void queryClient.invalidateQueries({ queryKey: ['rooms', hotelId] })
  }
  const action = useMutation({
    mutationFn: async ({ kind, block }: { kind: 'release' | 'delete'; block: RoomBlock }) => {
      if (kind === 'release') {
        await releaseBlock(hotelId, block.id)
      } else {
        await deleteBlock(hotelId, block.id)
      }
    },
    onSettled: () => { setPending(null); refresh() },
  })

  return (
    <Stack spacing={2}>
      {canManage && (
        <Button variant="contained" startIcon={<AddIcon />} sx={{ alignSelf: 'flex-end' }} onClick={() => setCreating(true)}>
          {t('rooms.blocks.add')}
        </Button>
      )}
      {blocks.error ? <ErrorAlert error={blocks.error} /> : null}
      {action.error ? <ErrorAlert error={action.error} /> : null}
      {blocks.data?.length === 0 && <Alert severity="info">{t('rooms.blocks.none')}</Alert>}
      {blocks.data && blocks.data.length > 0 && (
        <TableContainer component={Paper} variant="outlined">
          <Table size="small">
            <TableHead>
              <TableRow>
                <TableCell>{t('rooms.blocks.room')}</TableCell>
                <TableCell>{t('rooms.blocks.type')}</TableCell>
                <TableCell>{t('rooms.blocks.period')}</TableCell>
                <TableCell>{t('rooms.blocks.reason')}</TableCell>
                <TableCell />
                {canManage && <TableCell />}
              </TableRow>
            </TableHead>
            <TableBody>
              {blocks.data.map((block) => (
                <TableRow key={block.id}>
                  <TableCell sx={{ fontWeight: 600 }}>{block.roomNumber}</TableCell>
                  <TableCell>{t(`rooms.blockTypes.${block.type}`)}</TableCell>
                  <TableCell>{formatDate(block.startDate)} → {formatDate(block.endDate)}</TableCell>
                  <TableCell>{block.reason}</TableCell>
                  <TableCell>
                    <Chip size="small" color={block.inProgress ? 'warning' : 'default'}
                      label={block.inProgress ? t('rooms.blocks.inProgress') : t('rooms.blocks.upcoming')} />
                  </TableCell>
                  {canManage && (
                    <TableCell align="right">
                      {block.inProgress
                        ? <Button size="small" onClick={() => setPending({ kind: 'release', block })}>{t('rooms.blocks.release')}</Button>
                        : <Button size="small" color="error" onClick={() => setPending({ kind: 'delete', block })}>{t('rooms.blocks.delete')}</Button>}
                    </TableCell>
                  )}
                </TableRow>
              ))}
            </TableBody>
          </Table>
        </TableContainer>
      )}
      {creating && <BlockDialog hotelId={hotelId} businessDate={businessDate} onClose={() => setCreating(false)} onCreated={() => { setCreating(false); refresh() }} />}
      <ConfirmDialog
        open={pending !== null}
        title={pending?.kind === 'release' ? t('rooms.blocks.release') : t('rooms.blocks.delete')}
        message={pending ? t(pending.kind === 'release' ? 'rooms.blocks.confirmRelease' : 'rooms.blocks.confirmDelete', { room: pending.block.roomNumber }) : ''}
        danger={pending?.kind === 'delete'}
        pending={action.isPending}
        onClose={() => setPending(null)}
        onConfirm={() => pending && action.mutate(pending)}
      />
    </Stack>
  )
}

function addDays(isoDate: string, days: number): string {
  const [year, month, day] = isoDate.split('-').map(Number)
  const date = new Date(Date.UTC(year, month - 1, day + days))
  return date.toISOString().slice(0, 10)
}

function BlockDialog({ hotelId, businessDate, onClose, onCreated }: {
  hotelId: string; businessDate: string; onClose: () => void; onCreated: () => void
}) {
  const { t } = useTranslation()
  const rooms = useQuery({ queryKey: ['rooms', hotelId], queryFn: () => fetchRooms(hotelId) })
  const reasons = useQuery({ queryKey: ['reasons', hotelId, 'ROOM_BLOCK'], queryFn: () => fetchReasons(hotelId, 'ROOM_BLOCK') })
  const [roomId, setRoomId] = useState('')
  const [type, setType] = useState<BlockType>('MAINTENANCE')
  const [startDate, setStartDate] = useState(businessDate)
  const [endDate, setEndDate] = useState(addDays(businessDate, 1))
  const [reason, setReason] = useState('')
  const create = useMutation({
    mutationFn: () => createBlock(hotelId, { roomId, type, startDate, endDate, reason }),
    onSuccess: onCreated,
  })
  const roomOptions = (rooms.data ?? []).filter((room) => room.active)

  return (
    <Dialog open onClose={onClose} maxWidth="sm" fullWidth>
      <DialogTitle>{t('rooms.blocks.add')}</DialogTitle>
      <DialogContent>
        <Stack spacing={2} sx={{ pt: 1 }}>
          {create.error ? <ErrorAlert error={create.error} /> : null}
          <Autocomplete
            options={roomOptions}
            getOptionLabel={(room) => `${room.number} — ${room.roomTypeName ?? ''}`}
            value={roomOptions.find((room) => room.id === roomId) ?? null}
            onChange={(_, room) => setRoomId(room?.id ?? '')}
            renderInput={(params) => <TextField {...params} label={t('rooms.blocks.room')} />}
          />
          <TextField select label={t('rooms.blocks.type')} value={type} onChange={(e) => setType(e.target.value as BlockType)}>
            {BLOCK_TYPES.map((value) => <MenuItem key={value} value={value}>{t(`rooms.blockTypes.${value}`)}</MenuItem>)}
          </TextField>
          <Stack direction="row" spacing={2}>
            <TextField type="date" label={t('rooms.blocks.start')} value={startDate} onChange={(e) => setStartDate(e.target.value)}
              slotProps={{ inputLabel: { shrink: true }, htmlInput: { min: businessDate } }} fullWidth />
            <TextField type="date" label={t('rooms.blocks.end')} value={endDate} onChange={(e) => setEndDate(e.target.value)}
              slotProps={{ inputLabel: { shrink: true }, htmlInput: { min: addDays(startDate, 1) } }} fullWidth />
          </Stack>
          <Autocomplete
            freeSolo
            options={(reasons.data ?? []).filter((r) => r.active).map((r) => r.label)}
            inputValue={reason}
            onInputChange={(_, value) => setReason(value)}
            renderInput={(params) => <TextField {...params} label={t('rooms.blocks.reason')} />}
          />
        </Stack>
      </DialogContent>
      <DialogActions>
        <Button onClick={onClose}>{t('common.cancel')}</Button>
        <Button variant="contained" disabled={create.isPending || !roomId || !reason.trim()} onClick={() => create.mutate()}>
          {t('settings.save')}
        </Button>
      </DialogActions>
    </Dialog>
  )
}
