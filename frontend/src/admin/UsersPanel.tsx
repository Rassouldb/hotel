import AddIcon from '@mui/icons-material/Add'
import MoreVertIcon from '@mui/icons-material/MoreVert'
import {
  Button,
  Chip,
  IconButton,
  Menu,
  MenuItem,
  Paper,
  Stack,
  Table,
  TableBody,
  TableCell,
  TableContainer,
  TableHead,
  TablePagination,
  TableRow,
  TextField,
  Typography,
} from '@mui/material'
import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useDeferredValue, useState } from 'react'
import { useTranslation } from 'react-i18next'

import type { User, UserStatus } from '../api/administration'
import { disableUser, enableUser, fetchRoles, fetchUsers, resetUserPassword } from '../api/administration'
import { useAuth } from '../auth/AuthContext'
import { ConfirmDialog } from '../components/ConfirmDialog'
import { ErrorAlert } from '../components/ErrorAlert'
import { useActiveHotel } from '../hotel/ActiveHotelContext'
import { TemporaryPasswordDialog } from './TemporaryPasswordDialog'
import { UserDialog } from './UserDialog'

type PendingAction = { kind: 'disable' | 'reset'; user: User } | null

function fullName(user: Pick<User, 'firstName' | 'lastName'>): string {
  return `${user.firstName} ${user.lastName}`
}

function formatDateTime(value: string): string {
  return new Intl.DateTimeFormat('fr-FR', { dateStyle: 'short', timeStyle: 'short' }).format(new Date(value))
}

/** Gestion des comptes du personnel (US-IAM-01). */
export function UsersPanel() {
  const { t } = useTranslation()
  const { can } = useAuth()
  const { hotels } = useActiveHotel()
  const queryClient = useQueryClient()
  const [search, setSearch] = useState('')
  const deferredSearch = useDeferredValue(search)
  const [status, setStatus] = useState<UserStatus | ''>('')
  const [page, setPage] = useState(0)
  const [size, setSize] = useState(25)
  const [editing, setEditing] = useState<User | null | undefined>(undefined)
  const [menu, setMenu] = useState<{ anchor: HTMLElement; user: User } | null>(null)
  const [pending, setPending] = useState<PendingAction>(null)
  const [temporary, setTemporary] = useState<{ name: string; password: string } | null>(null)

  const users = useQuery({
    queryKey: ['users', deferredSearch, status, page, size],
    queryFn: () => fetchUsers({ search: deferredSearch, status, page: page + 1, size }),
    placeholderData: keepPreviousData,
  })
  const roles = useQuery({ queryKey: ['roles'], queryFn: fetchRoles })
  const refresh = () => queryClient.invalidateQueries({ queryKey: ['users'] })

  const action = useMutation({
    mutationFn: async ({ kind, user }: { kind: 'disable' | 'enable' | 'reset'; user: User }) => {
      if (kind === 'disable') {
        await disableUser(user.id)
      } else if (kind === 'enable') {
        await enableUser(user.id)
      } else {
        const result = await resetUserPassword(user.id)
        setTemporary({ name: fullName(user), password: result.temporaryPassword })
      }
    },
    onSettled: () => {
      setPending(null)
      void refresh()
    },
  })

  const hotelName = (id: string) => hotels.find((hotel) => hotel.id === id)?.name ?? '—'

  return (
    <Stack spacing={2}>
      <Stack direction={{ xs: 'column', sm: 'row' }} spacing={2} sx={{ alignItems: { sm: 'center' } }}>
        <TextField
          size="small"
          label={t('users.search')}
          value={search}
          onChange={(event) => { setSearch(event.target.value); setPage(0) }}
          sx={{ flexGrow: 1, maxWidth: { sm: 360 } }}
        />
        <TextField
          select
          size="small"
          label={t('users.status')}
          value={status}
          onChange={(event) => { setStatus(event.target.value as UserStatus | ''); setPage(0) }}
          sx={{ minWidth: 160 }}
        >
          <MenuItem value="">{t('users.statuses.all')}</MenuItem>
          <MenuItem value="ACTIVE">{t('users.statuses.ACTIVE')}</MenuItem>
          <MenuItem value="DISABLED">{t('users.statuses.DISABLED')}</MenuItem>
        </TextField>
        <BoxSpacer />
        {can('USER_CREATE') && (
          <Button variant="contained" startIcon={<AddIcon />} onClick={() => setEditing(null)}>{t('users.create')}</Button>
        )}
      </Stack>

      {users.error ? <ErrorAlert error={users.error} onRetry={() => void users.refetch()} /> : null}
      {action.error ? <ErrorAlert error={action.error} /> : null}

      <TableContainer component={Paper} variant="outlined">
        <Table size="small">
          <TableHead>
            <TableRow>
              <TableCell>{t('users.columns.name')}</TableCell>
              <TableCell>{t('users.columns.username')}</TableCell>
              <TableCell>{t('users.columns.access')}</TableCell>
              <TableCell>{t('users.columns.status')}</TableCell>
              <TableCell>{t('users.columns.lastLogin')}</TableCell>
              <TableCell align="right">{t('users.columns.actions')}</TableCell>
            </TableRow>
          </TableHead>
          <TableBody>
            {(users.data?.data ?? []).map((user) => (
              <TableRow key={user.id} hover>
                <TableCell>
                  <Typography variant="body2" sx={{ fontWeight: 600 }}>{fullName(user)}</Typography>
                  {user.email && <Typography variant="caption" color="text.secondary">{user.email}</Typography>}
                </TableCell>
                <TableCell>{user.username}</TableCell>
                <TableCell>
                  {user.superAdmin
                    ? <Chip size="small" color="primary" label={t('users.superAdmin')} />
                    : user.assignments.map((a) => (
                      <Chip key={`${a.hotelId}-${a.roleId}`} size="small" variant="outlined" sx={{ mr: 0.5, mb: 0.5 }}
                        label={`${hotelName(a.hotelId)} · ${a.roleName ?? ''}`} />
                    ))}
                </TableCell>
                <TableCell>
                  <Stack direction="row" spacing={0.5} sx={{ flexWrap: 'wrap', gap: 0.5 }}>
                    <Chip size="small" color={user.status === 'ACTIVE' ? 'success' : 'default'} label={t(`users.statuses.${user.status}`)} />
                    {user.locked && <Chip size="small" color="error" label={t('users.locked')} />}
                    {user.mustChangePassword && <Chip size="small" color="warning" variant="outlined" label={t('users.mustChange')} />}
                  </Stack>
                </TableCell>
                <TableCell>{user.lastLoginAt ? formatDateTime(user.lastLoginAt) : t('users.never')}</TableCell>
                <TableCell align="right">
                  <IconButton aria-label={t('users.columns.actions')} onClick={(event) => setMenu({ anchor: event.currentTarget, user })}>
                    <MoreVertIcon />
                  </IconButton>
                </TableCell>
              </TableRow>
            ))}
            {users.data && users.data.data.length === 0 && (
              <TableRow><TableCell colSpan={6}><Typography color="text.secondary">{t('users.empty')}</Typography></TableCell></TableRow>
            )}
          </TableBody>
        </Table>
        <TablePagination
          component="div"
          count={users.data?.meta?.total ?? 0}
          page={page}
          rowsPerPage={size}
          onPageChange={(_, value) => setPage(value)}
          onRowsPerPageChange={(event) => { setSize(Number(event.target.value)); setPage(0) }}
          rowsPerPageOptions={[10, 25, 50, 100]}
          labelRowsPerPage={t('common.rowsPerPage')}
        />
      </TableContainer>

      <Menu anchorEl={menu?.anchor} open={menu !== null} onClose={() => setMenu(null)}>
        {menu && can('USER_UPDATE') && (
          <MenuItem onClick={() => { setEditing(menu.user); setMenu(null) }}>{t('users.edit')}</MenuItem>
        )}
        {menu && can('USER_UPDATE') && (
          <MenuItem onClick={() => { setPending({ kind: 'reset', user: menu.user }); setMenu(null) }}>{t('users.resetPassword')}</MenuItem>
        )}
        {menu && can('USER_DISABLE') && menu.user.status === 'ACTIVE' && (
          <MenuItem onClick={() => { setPending({ kind: 'disable', user: menu.user }); setMenu(null) }} sx={{ color: 'error.main' }}>
            {t('users.disable')}
          </MenuItem>
        )}
        {menu && can('USER_DISABLE') && menu.user.status === 'DISABLED' && (
          <MenuItem onClick={() => { action.mutate({ kind: 'enable', user: menu.user }); setMenu(null) }}>{t('users.enable')}</MenuItem>
        )}
      </Menu>

      {editing !== undefined && (
        <UserDialog
          open
          user={editing}
          hotels={hotels}
          roles={roles.data ?? []}
          onClose={() => setEditing(undefined)}
          onCreated={(result) => {
            setEditing(undefined)
            setTemporary({ name: fullName(result.user), password: result.temporaryPassword })
            void refresh()
          }}
          onUpdated={() => {
            setEditing(undefined)
            void refresh()
          }}
        />
      )}

      <ConfirmDialog
        open={pending !== null}
        title={pending?.kind === 'disable' ? t('users.disable') : t('users.resetPassword')}
        message={pending ? t(pending.kind === 'disable' ? 'users.confirmDisable' : 'users.confirmReset', { name: fullName(pending.user) }) : ''}
        danger={pending?.kind === 'disable'}
        pending={action.isPending}
        onClose={() => setPending(null)}
        onConfirm={() => pending && action.mutate(pending)}
      />
      <TemporaryPasswordDialog name={temporary?.name ?? ''} password={temporary?.password ?? null} onClose={() => setTemporary(null)} />
    </Stack>
  )
}

function BoxSpacer() {
  return <span style={{ flexGrow: 1 }} />
}
