import { createTheme } from '@mui/material/styles'
import { frFR } from '@mui/material/locale'

export const theme = createTheme(
  {
    palette: {
      primary: { main: '#0f5c6e' },
      secondary: { main: '#c27c2c' },
      background: { default: '#f6f7f9' },
    },
    shape: { borderRadius: 8 },
    typography: {
      fontFamily: '"Inter", "Segoe UI", "Roboto", "Helvetica", "Arial", sans-serif',
      h4: { fontWeight: 600 },
    },
  },
  frFR,
)
