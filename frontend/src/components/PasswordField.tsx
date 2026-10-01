import VisibilityIcon from '@mui/icons-material/Visibility'
import VisibilityOffIcon from '@mui/icons-material/VisibilityOff'
import { IconButton, InputAdornment, TextField, type TextFieldProps } from '@mui/material'
import { forwardRef, useState } from 'react'
import { useTranslation } from 'react-i18next'

/** Champ mot de passe avec bouton afficher / masquer. */
export const PasswordField = forwardRef<HTMLInputElement, TextFieldProps>(function PasswordField(props, ref) {
  const { t } = useTranslation()
  const [visible, setVisible] = useState(false)

  return (
    <TextField
      {...props}
      inputRef={ref}
      type={visible ? 'text' : 'password'}
      slotProps={{
        input: {
          endAdornment: (
            <InputAdornment position="end">
              <IconButton
                aria-label={visible ? t('auth.hidePassword') : t('auth.showPassword')}
                onClick={() => setVisible((value) => !value)}
                edge="end"
              >
                {visible ? <VisibilityOffIcon /> : <VisibilityIcon />}
              </IconButton>
            </InputAdornment>
          ),
        },
      }}
    />
  )
})
