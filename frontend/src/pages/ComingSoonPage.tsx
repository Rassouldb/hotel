import { Alert, AlertTitle, Stack, Typography } from '@mui/material'
import { useTranslation } from 'react-i18next'

import type { NavItem } from '../layout/navigation'

export function ComingSoonPage({ item }: { item: NavItem }) {
  const { t } = useTranslation()

  return (
    <Stack spacing={2}>
      <Typography variant="h4" component="h1">{t(`nav.${item.key}`)}</Typography>
      <Alert severity="info">
        <AlertTitle>{t('comingSoon.title')}</AlertTitle>
        {item.availableIn === 'later'
          ? t('comingSoon.later')
          : t('comingSoon.body', { iteration: item.availableIn })}
      </Alert>
    </Stack>
  )
}
