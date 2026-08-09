import { StrictMode } from 'react';
import { createRoot } from 'react-dom/client';
import { Alert, Box, CssBaseline, ThemeProvider, Typography } from '@mui/material';
import { App } from './App';
import { theme } from './theme';
import './styles.css';

const secureBridgeAvailable = Object.prototype.hasOwnProperty.call(window, 'wizestream')
  && window.wizestream !== undefined;

function StartupFailure() {
  return (
    <Box sx={{ minHeight: '100vh', display: 'grid', placeItems: 'center', p: 4 }}>
      <Box sx={{ width: 'min(640px, 100%)' }}>
        <Typography variant="h4" gutterBottom>WizeStream could not start</Typography>
        <Alert severity="error">
          The secure desktop bridge did not load. Please reinstall the latest WizeStream Desktop
          preview. If the problem continues, attach the application startup log to a bug report.
        </Alert>
      </Box>
    </Box>
  );
}

createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <ThemeProvider theme={theme} defaultMode="system">
      <CssBaseline />
      {secureBridgeAvailable ? <App /> : <StartupFailure />}
    </ThemeProvider>
  </StrictMode>,
);
