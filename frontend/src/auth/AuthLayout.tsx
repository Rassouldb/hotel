import HotelIcon from '@mui/icons-material/Hotel'
import { Box, Card, CardContent, Stack, Typography } from '@mui/material'
import type { ReactNode } from 'react'
import { useTranslation } from 'react-i18next'

/** Mise en page des écrans hors session : connexion, mot de passe oublié, réinitialisation. */
export function AuthLayout({ title, subtitle, children }: { title: string; subtitle?: string; children: ReactNode }) {
  const { t } = useTranslation()

  return (
    <Box sx={{ minHeight: '100vh', display: 'grid', placeItems: 'center', bgcolor: 'background.default', p: 2 }}>
      <Stack spacing={3} sx={{ width: '100%', maxWidth: 420, alignItems: 'center' }}>
        <Stack direction="row" spacing={1} sx={{ alignItems: 'center', color: 'primary.main' }}>
          <HotelIcon fontSize="large" />
          <Typography variant="h5" component="div" sx={{ fontWeight: 700 }}>{t('app.title')}</Typography>
        </Stack>
        <Card variant="outlined" sx={{ width: '100%' }}>
          <CardContent sx={{ p: 4 }}>
            <Typography variant="h5" component="h1" gutterBottom>{title}</Typography>
            {subtitle && <Typography color="text.secondary" sx={{ mb: 3 }}>{subtitle}</Typography>}
            {children}
          </CardContent>
        </Card>
      </Stack>
    </Box>
  )
}
