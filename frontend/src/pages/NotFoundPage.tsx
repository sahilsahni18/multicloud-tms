import Link from '@mui/material/Link';
import Typography from '@mui/material/Typography';
import { Link as RouterLink } from 'react-router-dom';

export default function NotFoundPage() {
  return (
    <>
      <Typography variant="h1" sx={{ mt: 2 }}>
        Page not found
      </Typography>
      <Typography variant="body2" color="text.secondary" sx={{ mt: 1 }}>
        <Link component={RouterLink} to="/">
          Go to the dashboard
        </Link>
      </Typography>
    </>
  );
}
