import LogoutIcon from '@mui/icons-material/Logout'
import PersonIcon from '@mui/icons-material/Person'
import { Avatar, Box, Button, Divider, ListItemIcon, Menu, MenuItem, Typography } from '@mui/material'
import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { useNavigate } from 'react-router'

import { useAuth } from '../auth/AuthContext'

function initials(firstName: string, lastName: string): string {
  return `${firstName.charAt(0)}${lastName.charAt(0)}`.toUpperCase()
}

export function UserMenu() {
  const { t } = useTranslation()
  const { profile, logout } = useAuth()
  const navigate = useNavigate()
  const [anchor, setAnchor] = useState<HTMLElement | null>(null)

  if (!profile) {
    return null
  }

  return (
    <>
      <Button
        color="inherit"
        onClick={(event) => setAnchor(event.currentTarget)}
        aria-label={t('userMenu.open')}
        sx={{ textTransform: 'none', gap: 1 }}
      >
        <Avatar sx={{ width: 32, height: 32, bgcolor: 'primary.main', fontSize: 14 }}>
          {initials(profile.firstName, profile.lastName)}
        </Avatar>
        <Box sx={{ display: { xs: 'none', sm: 'block' }, textAlign: 'left' }}>
          <Typography variant="body2" sx={{ fontWeight: 600, lineHeight: 1.2 }}>
            {profile.firstName} {profile.lastName}
          </Typography>
          <Typography variant="caption" color="text.secondary">
            {profile.superAdmin ? t('userMenu.superAdmin') : profile.username}
          </Typography>
        </Box>
      </Button>
      <Menu anchorEl={anchor} open={anchor !== null} onClose={() => setAnchor(null)}>
        <MenuItem onClick={() => { setAnchor(null); navigate('/profil') }}>
          <ListItemIcon><PersonIcon fontSize="small" /></ListItemIcon>
          {t('userMenu.profile')}
        </MenuItem>
        <Divider />
        <MenuItem onClick={() => { setAnchor(null); void logout() }}>
          <ListItemIcon><LogoutIcon fontSize="small" /></ListItemIcon>
          {t('userMenu.logout')}
        </MenuItem>
      </Menu>
    </>
  )
}
