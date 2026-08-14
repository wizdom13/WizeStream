import { createTheme } from '@mui/material/styles';

export const theme = createTheme({
  cssVariables: true,
  shape: { borderRadius: 20 },
  colorSchemes: {
    light: {
      palette: {
        primary: { main: '#5f42a8' },
        secondary: { main: '#725572' },
        background: { default: '#fff7ff', paper: '#fff7ff' },
      },
    },
    dark: {
      palette: {
        primary: { main: '#cfbdff' },
        secondary: { main: '#dfc0df' },
        background: { default: '#151218', paper: '#151218' },
      },
    },
  },
  typography: {
    fontFamily: 'Inter, Roboto, system-ui, sans-serif',
    h4: { fontWeight: 650, letterSpacing: '-0.02em' },
    h6: { fontWeight: 650 },
    button: { textTransform: 'none', fontWeight: 650 },
  },
  components: {
    MuiButton: { styleOverrides: { root: { borderRadius: 999, paddingInline: 22 } } },
    MuiCard: { styleOverrides: { root: { borderRadius: 28, backgroundImage: 'none' } } },
    MuiTextField: { defaultProps: { variant: 'outlined' } },
  },
});
