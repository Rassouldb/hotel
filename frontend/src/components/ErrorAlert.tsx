import { Alert, Button, Typography } from '@mui/material'
import { useTranslation } from 'react-i18next'

import { ApiError } from '../api/http'

interface ErrorAlertProps {
  error: unknown
  onRetry?: () => void
}

/**
 * Affiche une erreur API avec son message métier. L'identifiant de trace n'est affiché que pour les
 * erreurs techniques (serveur, réseau), où il sert au support.
 */
export function ErrorAlert({ error, onRetry }: ErrorAlertProps) {
  const { t } = useTranslation()
  const message = error instanceof Error ? error.message : String(error)
  const traceId = error instanceof ApiError && (error.status >= 500 || error.status === 0) ? error.traceId : undefined

  return (
    <Alert
      severity="error"
      action={onRetry && <Button color="inherit" size="small" onClick={onRetry}>{t('errors.retry')}</Button>}
    >
      {message}
      {traceId && (
        <Typography variant="caption" component="div" sx={{ mt: 0.5, opacity: 0.8 }}>
          {t('errors.traceId')} : {traceId}
        </Typography>
      )}
    </Alert>
  )
}
