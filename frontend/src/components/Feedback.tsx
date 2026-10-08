import Alert from '@mui/material/Alert';
import TableCell from '@mui/material/TableCell';
import TableRow from '@mui/material/TableRow';
import Typography from '@mui/material/Typography';
import { errorMessage } from '../api/http';

export function ErrorBanner({ error, fallback }: { error: unknown; fallback?: string }) {
  if (!error) return null;
  return (
    <Alert severity="error" variant="outlined" sx={{ mb: 2 }}>
      {errorMessage(error, fallback)}
    </Alert>
  );
}

export function Loading({ label = 'Loading…' }: { label?: string }) {
  return (
    <Typography variant="body2" color="text.secondary" sx={{ py: 2 }}>
      {label}
    </Typography>
  );
}

/** A full-width message row for empty or loading tables. */
export function TableMessage({ colSpan, children }: { colSpan: number; children: React.ReactNode }) {
  return (
    <TableRow>
      <TableCell colSpan={colSpan} sx={{ color: 'text.secondary', py: 3, textAlign: 'center' }}>
        {children}
      </TableCell>
    </TableRow>
  );
}
