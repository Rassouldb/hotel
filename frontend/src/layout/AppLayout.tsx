import MenuIcon from '@mui/icons-material/Menu'
import {
  AppBar,
  Box,
  Chip,
  Divider,
  Drawer,
  IconButton,
  List,
  ListItemButton,
  ListItemIcon,
  ListItemText,
  ListSubheader,
  MenuItem,
  Select,
  Toolbar,
  Typography,
  useMediaQuery,
  useTheme,
} from '@mui/material'
import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { NavLink, Outlet } from 'react-router'

import { formatBusinessDate } from '../app/format'
import { useActiveHotel } from '../hotel/ActiveHotelContext'
import { navigation } from './navigation'

const DRAWER_WIDTH = 260

export function AppLayout() {
  const { t } = useTranslation()
  const theme = useTheme()
  const isDesktop = useMediaQuery(theme.breakpoints.up('md'))
  const [mobileOpen, setMobileOpen] = useState(false)

  const menu = (
    <Box component="nav" aria-label="Menu principal">
      <Toolbar>
        <Box>
          <Typography variant="h6" component="div" sx={{ fontWeight: 700 }}>{t('app.title')}</Typography>
          <Typography variant="caption" color="text.secondary">{t('app.subtitle')}</Typography>
        </Box>
      </Toolbar>
      <Divider />
      {navigation.map((section) => (
        <List
          key={section.key ?? 'main'}
          dense
          subheader={section.key ? <ListSubheader disableSticky>{t(`nav.sections.${section.key}`)}</ListSubheader> : undefined}
        >
          {section.items.map((item) => (
            <ListItemButton
              key={item.key}
              component={NavLink}
              to={item.path}
              end={item.path === '/'}
              onClick={() => setMobileOpen(false)}
              sx={{
                opacity: item.availableIn === 0 ? 1 : 0.6,
                '&.active': { bgcolor: 'action.selected', opacity: 1 },
              }}
            >
              <ListItemIcon sx={{ minWidth: 40 }}>{item.icon}</ListItemIcon>
              <ListItemText primary={t(`nav.${item.key}`)} />
            </ListItemButton>
          ))}
        </List>
      ))}
    </Box>
  )

  return (
    <Box sx={{ display: 'flex', minHeight: '100vh' }}>
      <AppBar
        position="fixed"
        color="inherit"
        elevation={0}
        sx={{ borderBottom: 1, borderColor: 'divider', width: { md: `calc(100% - ${DRAWER_WIDTH}px)` }, ml: { md: `${DRAWER_WIDTH}px` } }}
      >
        <Toolbar sx={{ gap: 2 }}>
          {!isDesktop && (
            <IconButton edge="start" aria-label="Ouvrir le menu" onClick={() => setMobileOpen(true)}>
              <MenuIcon />
            </IconButton>
          )}
          <HotelBanner />
        </Toolbar>
      </AppBar>

      <Box component="aside" sx={{ width: { md: DRAWER_WIDTH }, flexShrink: { md: 0 } }}>
        <Drawer
          variant={isDesktop ? 'permanent' : 'temporary'}
          open={isDesktop || mobileOpen}
          onClose={() => setMobileOpen(false)}
          sx={{ '& .MuiDrawer-paper': { width: DRAWER_WIDTH, boxSizing: 'border-box' } }}
        >
          {menu}
        </Drawer>
      </Box>

      <Box component="main" sx={{ flexGrow: 1, p: { xs: 2, md: 3 }, width: { md: `calc(100% - ${DRAWER_WIDTH}px)` } }}>
        <Toolbar />
        <Outlet />
      </Box>
    </Box>
  )
}

/** Bandeau permanent (§37.2) : hôtel actif et date d'exploitation. */
function HotelBanner() {
  const { t } = useTranslation()
  const { hotels, activeHotel, selectHotel } = useActiveHotel()

  if (!activeHotel) {
    return null
  }

  return (
    <Box sx={{ display: 'flex', alignItems: 'center', gap: 2, flexWrap: 'wrap', minWidth: 0 }}>
      {hotels.length > 1 ? (
        <Select
          size="small"
          value={activeHotel.id}
          onChange={(event) => selectHotel(event.target.value)}
          aria-label={t('banner.chooseHotel')}
        >
          {hotels.map((hotel) => (
            <MenuItem key={hotel.id} value={hotel.id}>{hotel.name}</MenuItem>
          ))}
        </Select>
      ) : (
        <Typography variant="subtitle1" sx={{ fontWeight: 600 }} noWrap>{activeHotel.name}</Typography>
      )}
      <Chip
        color="primary"
        variant="outlined"
        label={`${t('banner.businessDate')} : ${formatBusinessDate(activeHotel.businessDate)}`}
      />
    </Box>
  )
}
