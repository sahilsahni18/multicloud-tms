import Box from '@mui/material/Box';
import Paper from '@mui/material/Paper';
import Typography from '@mui/material/Typography';
import Logo from '../../components/Logo';
import { colors } from '../../theme';

/** Narrow centred column for the sign-in and sign-up forms. */
export default function AuthLayout({ title, children, footer }: {
  title: string;
  children: React.ReactNode;
  footer?: React.ReactNode;
}) {
  return (
    <Box sx={{ minHeight: '100%', bgcolor: colors.subtle, display: 'flex', justifyContent: 'center', pt: '12vh' }}>
      <Box sx={{ width: 380 }}>
        <Box sx={{ display: 'flex', justifyContent: 'center', mb: 3 }}>
          <Logo size={32} />
        </Box>
        <Paper variant="elevation" elevation={3} sx={{ p: 4 }}>
          <Typography variant="h2" sx={{ mb: 2, textAlign: 'center' }}>
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
