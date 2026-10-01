import {
  Box,
  Button,
  Checkbox,
  Dialog,
  DialogActions,
  DialogContent,
  DialogTitle,
  FormControlLabel,
  Stack,
  TextField,
  Typography,
} from '@mui/material'
import { useMutation } from '@tanstack/react-query'
import { useMemo, useState } from 'react'
import { useTranslation } from 'react-i18next'

import type { PermissionDefinition, Role } from '../api/administration'
import { createRole, updateRole } from '../api/administration'
import { ErrorAlert } from '../components/ErrorAlert'

interface RoleDialogProps {
  role: Role | null
  permissions: PermissionDefinition[]
  readOnly: boolean
  onClose: () => void
  onSaved: () => void
}

/** Création ou modification d'un rôle : permissions cochées module par module (US-IAM-02). */
export function RoleDialog({ role, permissions, readOnly, onClose, onSaved }: RoleDialogProps) {
  const { t } = useTranslation()
  const [name, setName] = useState(role?.name ?? '')
  const [description, setDescription] = useState(role?.description ?? '')
  const [selected, setSelected] = useState<Set<string>>(new Set(role?.permissions ?? []))

  const modules = useMemo(() => {
    const grouped = new Map<string, { label: string; permissions: PermissionDefinition[] }>()
    for (const permission of permissions) {
      const entry = grouped.get(permission.module) ?? { label: permission.moduleLabel, permissions: [] }
      entry.permissions.push(permission)
      grouped.set(permission.module, entry)
    }
    return [...grouped.entries()]
  }, [permissions])

  const save = useMutation({
    mutationFn: () => {
      const input = { name: name.trim(), description: description.trim() || null, permissions: [...selected] }
      return role ? updateRole(role.id, { ...input, version: role.version }) : createRole(input)
    },
    onSuccess: onSaved,
  })

  const toggle = (codes: string[], checked: boolean) => {
    setSelected((current) => {
      const next = new Set(current)
      for (const code of codes) {
        if (checked) {
          next.add(code)
        } else {
          next.delete(code)
        }
      }
      return next
    })
  }

  return (
    <Dialog open onClose={onClose} maxWidth="md" fullWidth>
      <DialogTitle>{role ? `${t('roles.edit')} — ${role.name}` : t('roles.create')}</DialogTitle>
      <DialogContent dividers>
        <Stack spacing={2}>
          {save.error ? <ErrorAlert error={save.error} /> : null}
          <Stack direction={{ xs: 'column', sm: 'row' }} spacing={2}>
            <TextField label={t('roles.name')} value={name} onChange={(event) => setName(event.target.value)}
              required fullWidth disabled={readOnly} />
            <TextField label={t('roles.description')} value={description} onChange={(event) => setDescription(event.target.value)}
              fullWidth disabled={readOnly} />
          </Stack>
          <Typography variant="subtitle1" sx={{ fontWeight: 600 }}>
            {t('roles.permissions')} · {t('roles.permissionCount', { count: selected.size })}
          </Typography>
          <Box sx={{ display: 'grid', gridTemplateColumns: { xs: '1fr', md: '1fr 1fr' }, gap: 2 }}>
            {modules.map(([module, entry]) => {
              const codes = entry.permissions.map((permission) => permission.code)
              const count = codes.filter((code) => selected.has(code)).length
              return (
                <Box key={module} sx={{ border: 1, borderColor: 'divider', borderRadius: 1, p: 1.5 }}>
                  <FormControlLabel
                    label={<Typography sx={{ fontWeight: 600 }}>{entry.label}</Typography>}
                    control={
                      <Checkbox
                        checked={count === codes.length}
                        indeterminate={count > 0 && count < codes.length}
                        disabled={readOnly}
                        onChange={(event) => toggle(codes, event.target.checked)}
                        slotProps={{ input: { 'aria-label': `${t('roles.selectModule')} : ${entry.label}` } }}
                      />
                    }
                  />
                  <Stack sx={{ pl: 3 }}>
                    {entry.permissions.map((permission) => (
                      <FormControlLabel
                        key={permission.code}
                        label={<Typography variant="body2">{permission.description}</Typography>}
                        control={
                          <Checkbox
                            size="small"
                            checked={selected.has(permission.code)}
                            disabled={readOnly}
                            onChange={(event) => toggle([permission.code], event.target.checked)}
                          />
                        }
                      />
                    ))}
                  </Stack>
                </Box>
              )
            })}
          </Box>
        </Stack>
      </DialogContent>
      <DialogActions>
        <Button onClick={onClose}>{t('roles.cancel')}</Button>
        {!readOnly && (
          <Button variant="contained" onClick={() => save.mutate()} disabled={save.isPending || name.trim() === ''}>
            {t('roles.save')}
          </Button>
        )}
      </DialogActions>
    </Dialog>
  )
}
