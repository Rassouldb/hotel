import { Alert, Card, CardContent, Chip, CircularProgress, Grid, Stack, Typography } from '@mui/material'
import { useQuery } from '@tanstack/react-query'
import { useTranslation } from 'react-i18next'

import { formatBusinessDate } from '../app/format'
import { ErrorAlert } from '../components/ErrorAlert'
import { useActiveHotel } from '../hotel/ActiveHotelContext'

async function fetchApiHealth(): Promise<boolean> {
  const response = await fetch('/actuator/health')
  return response.ok
}

export function DashboardPage() {
  const { t } = useTranslation()
  const { activeHotel, isLoading, error, refetch } = useActiveHotel()
  const health = useQuery({ queryKey: ['health'], queryFn: fetchApiHealth, refetchInterval: 30_000, retry: false })

  if (isLoading) {
    return <CircularProgress aria-label="Chargement" />
  }
  if (error) {
    return <ErrorAlert error={error} onRetry={refetch} />
  }
  if (!activeHotel) {
    return <Alert severity="warning">{t('errors.noHotel')}</Alert>
  }

  return (
    <Stack spacing={3}>
      <Typography variant="h4" component="h1">{t('dashboard.title')}</Typography>
      <Grid container spacing={2}>
        <Grid size={{ xs: 12, md: 8 }}>
          <Card variant="outlined">
            <CardContent>
              <Typography variant="overline" color="text.secondary">{t('dashboard.hotelCard')}</Typography>
              <Typography variant="h5" component="h2">{activeHotel.name}</Typography>
              <Typography color="text.secondary" gutterBottom>{activeHotel.address}</Typography>
              <Stack direction="row" spacing={1} sx={{ flexWrap: 'wrap', gap: 1, mt: 2 }}>
                <Chip label={`${t('banner.businessDate')} : ${formatBusinessDate(activeHotel.businessDate)}`} />
                <Chip label={`${t('dashboard.currency')} : ${activeHotel.currencyCode}`} />
                <Chip label={`${t('dashboard.timezone')} : ${activeHotel.timezone}`} />
              </Stack>
            </CardContent>
          </Card>
        </Grid>
        <Grid size={{ xs: 12, md: 4 }}>
          <Card variant="outlined" sx={{ height: '100%' }}>
            <CardContent>
              <Typography variant="overline" color="text.secondary">{t('dashboard.systemCard')}</Typography>
              <Stack direction="row" sx={{ mt: 1 }}>
                {health.isPending ? (
                  <CircularProgress size={20} />
                ) : (
                  <Chip
                    color={health.data ? 'success' : 'error'}
                    label={health.data ? t('dashboard.apiUp') : t('dashboard.apiDown')}
                  />
                )}
              </Stack>
            </CardContent>
          </Card>
        </Grid>
      </Grid>
      <Alert severity="info">{t('dashboard.upcoming')}</Alert>
    </Stack>
  )
}
