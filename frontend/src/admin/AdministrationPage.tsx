import { Alert, CircularProgress, Stack, Tab, Tabs, Typography } from '@mui/material'
import { useTranslation } from 'react-i18next'
import { Navigate, useNavigate, useParams } from 'react-router'

import { useAuth } from '../auth/AuthContext'
import { useActiveHotel } from '../hotel/ActiveHotelContext'
import { HotelSettingsPanel } from '../settings/HotelSettingsPanel'
import { HotelsPanel } from '../settings/HotelsPanel'
import { AuditPanel } from './AuditPanel'
import { RolesPanel } from './RolesPanel'
import { UsersPanel } from './UsersPanel'

type TabKey = 'parametres' | 'hotels' | 'utilisateurs' | 'roles' | 'audit'

const SETTINGS_PERMISSIONS = ['HOTEL_UPDATE', 'SETTINGS_UPDATE', 'TAX_MANAGE', 'PAYMENT_METHOD_MANAGE']

/** Administration : paramètres de l'hôtel, hôtels, utilisateurs, rôles, journal d'audit, selon les droits de chacun. */
export function AdministrationPage() {
  const { t } = useTranslation()
  const { can, profile } = useAuth()
  const { activeHotel, isLoading } = useActiveHotel()
  const navigate = useNavigate()
  const { tab } = useParams<{ tab?: string }>()

  // Les onglets dépendent de l'hôtel actif : on attend son chargement avant de choisir l'onglet affiché.
  if (isLoading) {
    return <CircularProgress aria-label="Chargement" />
  }

  const available: Array<{ key: TabKey; label: string }> = []
  if (activeHotel && SETTINGS_PERMISSIONS.some((permission) => can(permission, activeHotel.id))) {
    available.push({ key: 'parametres', label: t('admin.tabs.settings') })
  }
  if (profile?.superAdmin) {
    available.push({ key: 'hotels', label: t('admin.tabs.hotels') })
  }
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
      <Tabs value={current.key} onChange={(_, value: TabKey) => navigate(`/administration/${value}`)} variant="scrollable" allowScrollButtonsMobile>
        {available.map((entry) => <Tab key={entry.key} value={entry.key} label={entry.label} />)}
      </Tabs>
      {current.key === 'parametres' && <HotelSettingsPanel />}
      {current.key === 'hotels' && <HotelsPanel />}
      {current.key === 'utilisateurs' && <UsersPanel />}
      {current.key === 'roles' && <RolesPanel />}
      {current.key === 'audit' && <AuditPanel />}
    </Stack>
  )
}
