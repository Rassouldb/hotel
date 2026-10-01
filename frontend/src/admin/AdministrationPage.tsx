import { Alert, Stack, Tab, Tabs, Typography } from '@mui/material'
import { useTranslation } from 'react-i18next'
import { Navigate, useNavigate, useParams } from 'react-router'

import { useAuth } from '../auth/AuthContext'
import { AuditPanel } from './AuditPanel'
import { RolesPanel } from './RolesPanel'
import { UsersPanel } from './UsersPanel'

type TabKey = 'utilisateurs' | 'roles' | 'audit'

/** Administration (itération 1) : utilisateurs, rôles, journal d'audit, selon les droits de chacun. */
export function AdministrationPage() {
  const { t } = useTranslation()
  const { can } = useAuth()
  const navigate = useNavigate()
  const { tab } = useParams<{ tab?: string }>()

  const available: Array<{ key: TabKey; label: string }> = []
  if (can('USER_VIEW')) {
    available.push({ key: 'utilisateurs', label: t('admin.tabs.users') })
  }
  if (can('USER_VIEW') || can('ROLE_MANAGE')) {
    available.push({ key: 'roles', label: t('admin.tabs.roles') })
  }
  if (can('AUDIT_VIEW')) {
    available.push({ key: 'audit', label: t('admin.tabs.audit') })
  }

  if (available.length === 0) {
    return <Alert severity="warning">{t('forbidden.body')}</Alert>
  }
  const current = available.find((entry) => entry.key === tab)
  if (!current) {
    return <Navigate to={`/administration/${available[0].key}`} replace />
  }

  return (
    <Stack spacing={2}>
      <Typography variant="h4" component="h1">{t('admin.title')}</Typography>
      <Tabs value={current.key} onChange={(_, value: TabKey) => navigate(`/administration/${value}`)}>
        {available.map((entry) => <Tab key={entry.key} value={entry.key} label={entry.label} />)}
      </Tabs>
      {current.key === 'utilisateurs' && <UsersPanel />}
      {current.key === 'roles' && <RolesPanel />}
      {current.key === 'audit' && <AuditPanel />}
    </Stack>
  )
}
