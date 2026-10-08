import { createTheme, type Shadows } from '@mui/material/styles';

// Atlassian-style look: system fonts, navy text, one blue, light neutral
// surfaces, flat controls.
const fontFamily =
  '-apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, "Noto Sans", Ubuntu, "Helvetica Neue", sans-serif';

export const colors = {
  text: '#172B4D',
  muted: '#626F86',
  border: '#DCDFE4',
  subtle: '#F7F8F9',
  hover: '#F1F2F4',
  blue: '#0C66E4',
  blueHover: '#0055CC',
  blueBg: '#E9F2FF',
  column: '#F1F2F4',
};

const raised = '0 1px 1px rgba(9,30,66,0.25), 0 0 1px rgba(9,30,66,0.31)';
const overlay = '0 8px 12px rgba(9,30,66,0.15), 0 0 1px rgba(9,30,66,0.31)';

export const theme = createTheme({
  palette: {
    primary: { main: colors.blue, dark: colors.blueHover },
    background: { default: '#FFFFFF', paper: '#FFFFFF' },
    text: { primary: colors.text, secondary: colors.muted },
    divider: colors.border,
    error: { main: '#C9372C' },
    success: { main: '#216E4E' },
    warning: { main: '#A54800' },
  },
  shape: { borderRadius: 3 },
  shadows: ['none', raised, raised, overlay, ...Array(21).fill(overlay)] as Shadows,
  typography: {
    fontFamily,
    fontSize: 14,
    h1: { fontSize: 24, fontWeight: 500, lineHeight: 1.33, letterSpacing: '-0.01em' },
    h2: { fontSize: 16, fontWeight: 600, lineHeight: 1.5 },
    h3: { fontSize: 14, fontWeight: 600, lineHeight: 1.5 },
    body2: { fontSize: 13 },
    button: { textTransform: 'none', fontWeight: 500, fontSize: 14 },
  },
  components: {
    MuiButtonBase: { defaultProps: { disableRipple: true } },
    MuiButton: {
      defaultProps: { disableElevation: true, size: 'small', variant: 'text' },
      styleOverrides: {
        root: { padding: '4px 12px', lineHeight: '24px', minWidth: 0 },
        text: {
          color: colors.text,
          backgroundColor: 'rgba(9,30,66,0.06)',
          '&:hover': { backgroundColor: 'rgba(9,30,66,0.14)' },
        },
        outlined: { borderColor: colors.border, color: colors.text },
        containedPrimary: { '&:hover': { backgroundColor: colors.blueHover } },
      },
    },
    MuiTextField: { defaultProps: { size: 'small' } },
    MuiSelect: { defaultProps: { size: 'small' } },
    MuiFormControl: { defaultProps: { size: 'small' } },
    MuiOutlinedInput: {
      styleOverrides: {
        root: {
          backgroundColor: '#FFFFFF',
          '& .MuiOutlinedInput-notchedOutline': { borderColor: '#8590A2', borderWidth: 1 },
          '&:hover .MuiOutlinedInput-notchedOutline': { borderColor: '#626F86' },
        },
      },
    },
    MuiPaper: {
      defaultProps: { variant: 'outlined' },
      styleOverrides: { root: { borderColor: colors.border } },
    },
    MuiMenu: { defaultProps: { slotProps: { paper: { variant: 'elevation', elevation: 3 } } } },
    MuiPopover: { defaultProps: { slotProps: { paper: { variant: 'elevation', elevation: 3 } } } },
    MuiAutocomplete: { defaultProps: { slotProps: { paper: { variant: 'elevation', elevation: 3 } } } },
    MuiDialog: {
      defaultProps: { fullWidth: true, maxWidth: 'sm', slotProps: { paper: { variant: 'elevation', elevation: 3 } } },
    },
    MuiDialogTitle: { styleOverrides: { root: { fontSize: 20, fontWeight: 500, padding: '20px 24px 12px' } } },
    MuiTableCell: {
      styleOverrides: {
        root: { borderColor: colors.border, padding: '8px 12px', fontSize: 14 },
        head: { fontWeight: 600, color: colors.muted, fontSize: 12, borderBottomWidth: 2, padding: '6px 12px' },
      },
    },
    MuiTableRow: {
      styleOverrides: { root: { '&.MuiTableRow-hover:hover': { backgroundColor: colors.hover } } },
    },
    MuiLink: { defaultProps: { underline: 'hover' } },
    MuiTooltip: {
      defaultProps: { arrow: false, enterDelay: 400 },
      styleOverrides: { tooltip: { backgroundColor: colors.text, fontSize: 12 } },
    },
    MuiMenuItem: { styleOverrides: { root: { fontSize: 14 } } },
  },
});
