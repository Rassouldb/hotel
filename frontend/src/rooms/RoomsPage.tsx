import { Alert, CircularProgress, Stack, Tab, Tabs, Typography } from '@mui/material'
import { useTranslation } from 'react-i18next'
import { Navigate, useNavigate, useParams } from 'react-router'

import { useAuth } from '../auth/AuthContext'
import { useActiveHotel } from '../hotel/ActiveHotelContext'
import { BlocksPanel } from './BlocksPanel'
import { RoomsManagementPanel } from './RoomsManagementPanel'
import { RoomStatusBoard } from './RoomStatusBoard'
import { RoomTypesPanel } from './RoomTypesPanel'

type TabKey = 'etat' | 'types' | 'gestion' | 'blocages'
const TABS: Array<{ key: TabKey; label: string }> = [
  { key: 'etat', label: 'rooms.tabs.status' },
  { key: 'types', label: 'rooms.tabs.types' },
  { key: 'gestion', label: 'rooms.tabs.rooms' },
  { key: 'blocages', label: 'rooms.tabs.blocks' },
]

/** Chambres de l'hôtel actif (itération 2). */
export function RoomsPage() {
  const { t } = useTranslation()
  const { can } = useAuth()
  const { activeHotel, isLoading } = useActiveHotel()
  const navigate = useNavigate()
  const { tab } = useParams<{ tab?: string }>()

  if (isLoading) {
    return <CircularProgress aria-label="Chargement" />
  }
  if (!activeHotel) {
    return <Alert severity="warning">{t('errors.noHotel')}</Alert>
  }
  const hotelId = activeHotel.id
  if (!can('ROOM_VIEW', hotelId)) {
    return <Alert severity="warning">{t('forbidden.body')}</Alert>
  }
  const current = TABS.find((entry) => entry.key === tab)
  if (!current) {
    return <Navigate to="/chambres/etat" replace />
  }

  return (
    <Stack spacing={2}>
      <Typography variant="h4" component="h1">{t('rooms.title')}</Typography>
      <Tabs value={current.key} onChange={(_, value: TabKey) => navigate(`/chambres/${value}`)} variant="scrollable" allowScrollButtonsMobile>
        {TABS.map((entry) => <Tab key={entry.key} value={entry.key} label={t(entry.label)} />)}
      </Tabs>
      {current.key === 'etat' && <RoomStatusBoard hotelId={hotelId} />}
      {current.key === 'types' && (
        <RoomTypesPanel hotelId={hotelId} currency={activeHotel.currencyCode}
          canCreate={can('ROOM_CREATE', hotelId)} canEdit={can('ROOM_UPDATE', hotelId)} />
      )}
      {current.key === 'gestion' && (
        <RoomsManagementPanel hotelId={hotelId} canCreate={can('ROOM_CREATE', hotelId)} canEdit={can('ROOM_UPDATE', hotelId)} />
      )}
      {current.key === 'blocages' && (
        <BlocksPanel hotelId={hotelId} businessDate={activeHotel.businessDate} canManage={can('ROOM_BLOCK_MANAGE', hotelId)} />
      )}
    </Stack>
  )
}
