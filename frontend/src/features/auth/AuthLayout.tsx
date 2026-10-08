import Box from '@mui/material/Box';
import Paper from '@mui/material/Paper';
import Typography from '@mui/material/Typography';
import { colors } from '../../theme';

/** Narrow centred column for the sign-in and sign-up forms. */
export default function AuthLayout({ title, children, footer }: {
  title: string;
  children: React.ReactNode;
  footer?: React.ReactNode;
}) {
  return (
    <Box sx={{ minHeight: '100%', bgcolor: colors.subtle, display: 'flex', justifyContent: 'center', pt: '12vh' }}>
      <Box sx={{ width: 340 }}>
        <Typography sx={{ fontWeight: 700, fontSize: 18, color: colors.blue, textAlign: 'center', mb: 2 }}>
          TrackFlow
        </Typography>
        <Paper sx={{ p: 3 }}>
          <Typography variant="h2" sx={{ mb: 2 }}>
            {title}
          </Typography>
          {children}
        </Paper>
        {footer && (
          <Paper sx={{ p: 1.5, mt: 1.5, textAlign: 'center' }}>
            <Typography variant="body2">{footer}</Typography>
          </Paper>
        )}
      </Box>
    </Box>
  );
}
