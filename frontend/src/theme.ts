import { createTheme, type Shadows } from '@mui/material/styles';

// Plain internal-tool look: system fonts, white surfaces, one blue, thin grey
// borders, no shadows, no ripples.
const fontFamily =
  '-apple-system, BlinkMacSystemFont, "Segoe UI", "Noto Sans", Helvetica, Arial, sans-serif';

export const colors = {
  text: '#1f2328',
  muted: '#59636e',
  border: '#d1d9e0',
  subtle: '#f6f8fa',
  blue: '#0b5cad',
  blueBg: '#ddf4ff',
};

export const theme = createTheme({
  palette: {
    primary: { main: colors.blue },
    background: { default: '#ffffff', paper: '#ffffff' },
    text: { primary: colors.text, secondary: colors.muted },
    divider: colors.border,
    error: { main: '#cf222e' },
    success: { main: '#1a7f37' },
    warning: { main: '#9a6700' },
  },
  shape: { borderRadius: 4 },
  shadows: Array(25).fill('none') as Shadows,
  typography: {
    fontFamily,
    fontSize: 14,
    h1: { fontSize: 20, fontWeight: 600, lineHeight: 1.4 },
    h2: { fontSize: 16, fontWeight: 600, lineHeight: 1.4 },
    h3: { fontSize: 14, fontWeight: 600, lineHeight: 1.4 },
    body2: { fontSize: 13 },
    button: { textTransform: 'none', fontWeight: 500, fontSize: 14 },
  },
  components: {
    MuiButtonBase: { defaultProps: { disableRipple: true } },
    MuiButton: {
      defaultProps: { disableElevation: true, size: 'small', variant: 'outlined' },
      styleOverrides: { root: { padding: '3px 12px' } },
    },
    MuiTextField: { defaultProps: { size: 'small' } },
    MuiSelect: { defaultProps: { size: 'small' } },
    MuiFormControl: { defaultProps: { size: 'small' } },
    MuiPaper: {
      defaultProps: { variant: 'outlined' },
      styleOverrides: { root: { borderColor: colors.border } },
    },
    MuiDialog: {
      defaultProps: { fullWidth: true, maxWidth: 'sm' },
      styleOverrides: { paper: { border: `1px solid ${colors.border}` } },
    },
    MuiDialogTitle: { styleOverrides: { root: { fontSize: 16, fontWeight: 600, padding: '14px 20px' } } },
    MuiTableCell: {
      styleOverrides: {
        root: { borderColor: colors.border, padding: '7px 12px', fontSize: 14 },
        head: { fontWeight: 600, color: colors.muted, backgroundColor: colors.subtle, fontSize: 13 },
      },
    },
    MuiTableRow: {
      styleOverrides: { root: { '&.MuiTableRow-hover:hover': { backgroundColor: colors.subtle } } },
    },
    MuiLink: { defaultProps: { underline: 'hover' } },
    MuiTooltip: { defaultProps: { arrow: false, enterDelay: 400 } },
  },
});
