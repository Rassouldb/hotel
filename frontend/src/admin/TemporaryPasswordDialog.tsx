import ContentCopyIcon from '@mui/icons-material/ContentCopy'
import { Alert, Box, Button, Dialog, DialogActions, DialogContent, DialogTitle, Typography } from '@mui/material'
import { useState } from 'react'
import { useTranslation } from 'react-i18next'

interface TemporaryPasswordDialogProps {
  name: string
  password: string | null
  onClose: () => void
}

/** Affiche une seule fois le mot de passe temporaire généré, à transmettre à l'employé. */
export function TemporaryPasswordDialog({ name, password, onClose }: TemporaryPasswordDialogProps) {
  const { t } = useTranslation()
  const [copied, setCopied] = useState(false)

  const copy = async () => {
    if (password) {
      await navigator.clipboard?.writeText(password)
      setCopied(true)
    }
  }

  return (
    <Dialog open={password !== null} onClose={onClose} maxWidth="xs" fullWidth>
      <DialogTitle>{t('users.temporaryTitle')}</DialogTitle>
      <DialogContent>
        <Alert severity="warning" sx={{ mb: 2 }}>{t('users.temporaryBody', { name })}</Alert>
        <Box sx={{ display: 'flex', alignItems: 'center', gap: 1, p: 2, bgcolor: 'action.hover', borderRadius: 1 }}>
          <Typography sx={{ fontFamily: 'monospace', fontSize: 20, letterSpacing: 1, flexGrow: 1 }} data-testid="temporary-password">
            {password}
          </Typography>
          <Button startIcon={<ContentCopyIcon />} onClick={() => void copy()}>
            {copied ? t('users.copied') : t('users.copy')}
          </Button>
        </Box>
      </DialogContent>
      <DialogActions>
        <Button variant="contained" onClick={() => { setCopied(false); onClose() }}>{t('users.close')}</Button>
      </DialogActions>
    </Dialog>
  )
}
