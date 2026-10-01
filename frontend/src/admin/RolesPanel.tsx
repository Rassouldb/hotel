import AddIcon from '@mui/icons-material/Add'
import {
  Alert,
  Button,
  Card,
  CardActions,
  CardContent,
  Chip,
  Grid,
  Stack,
  Typography,
} from '@mui/material'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'
import { useTranslation } from 'react-i18next'

import type { Role } from '../api/administration'
import { deleteRole, fetchPermissions, fetchRoles } from '../api/administration'
import { useAuth } from '../auth/AuthContext'
import { ConfirmDialog } from '../components/ConfirmDialog'
import { ErrorAlert } from '../components/ErrorAlert'
import { RoleDialog } from './RoleDialog'

/** Liste des rôles de l'organisation et éditeur de permissions (US-IAM-02). */
export function RolesPanel() {
  const { t } = useTranslation()
  const { can } = useAuth()
  const queryClient = useQueryClient()
  const canManage = can('ROLE_MANAGE')
  const roles = useQuery({ queryKey: ['roles'], queryFn: fetchRoles })
  const permissions = useQuery({ queryKey: ['permissions'], queryFn: fetchPermissions, staleTime: Infinity })
  const [editing, setEditing] = useState<Role | null | undefined>(undefined)
  const [deleting, setDeleting] = useState<Role | null>(null)

  const remove = useMutation({
    mutationFn: (role: Role) => deleteRole(role.id),
    onSettled: () => {
      setDeleting(null)
      void queryClient.invalidateQueries({ queryKey: ['roles'] })
    },
  })

  return (
    <Stack spacing={2}>
      <Stack direction="row" sx={{ justifyContent: 'space-between', alignItems: 'center' }}>
        {!canManage ? <Alert severity="info" sx={{ flexGrow: 1 }}>{t('roles.readOnly')}</Alert> : <span />}
        {canManage && (
          <Button variant="contained" startIcon={<AddIcon />} onClick={() => setEditing(null)}>{t('roles.create')}</Button>
        )}
      </Stack>
      {roles.error ? <ErrorAlert error={roles.error} onRetry={() => void roles.refetch()} /> : null}
      {remove.error ? <ErrorAlert error={remove.error} /> : null}
      <Grid container spacing={2}>
        {(roles.data ?? []).map((role) => (
          <Grid key={role.id} size={{ xs: 12, sm: 6, lg: 4 }}>
            <Card variant="outlined" sx={{ height: '100%', display: 'flex', flexDirection: 'column' }}>
              <CardContent sx={{ flexGrow: 1 }}>
                <Stack direction="row" spacing={1} sx={{ alignItems: 'center', mb: 1, flexWrap: 'wrap', gap: 0.5 }}>
                  <Typography variant="h6" component="h3">{role.name}</Typography>
                  {role.system && <Chip size="small" label={t('roles.system')} />}
                </Stack>
                {role.description && <Typography variant="body2" color="text.secondary" gutterBottom>{role.description}</Typography>}
                <Typography variant="body2">
                  {t('roles.permissionCount', { count: role.permissions.length })} · {t('roles.users', { count: role.userCount })}
                </Typography>
              </CardContent>
              <CardActions>
                <Button size="small" onClick={() => setEditing(role)}>{canManage ? t('roles.edit') : t('audit.details')}</Button>
                {canManage && !role.system && (
                  <Button size="small" color="error" onClick={() => setDeleting(role)}>{t('roles.delete')}</Button>
                )}
              </CardActions>
            </Card>
          </Grid>
        ))}
      </Grid>

      {editing !== undefined && permissions.data && (
        <RoleDialog
          role={editing}
          permissions={permissions.data}
          readOnly={!canManage}
          onClose={() => setEditing(undefined)}
          onSaved={() => {
            setEditing(undefined)
            void queryClient.invalidateQueries({ queryKey: ['roles'] })
          }}
        />
      )}
      <ConfirmDialog
        open={deleting !== null}
        title={t('roles.delete')}
        message={deleting ? t('roles.confirmDelete', { name: deleting.name }) : ''}
        danger
        pending={remove.isPending}
        onClose={() => setDeleting(null)}
        onConfirm={() => deleting && remove.mutate(deleting)}
      />
    </Stack>
  )
}
