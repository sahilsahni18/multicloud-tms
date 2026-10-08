import Box from '@mui/material/Box';
import Typography from '@mui/material/Typography';
import { Navigate, Outlet, useLocation } from 'react-router-dom';
import type { RoleName } from '../../api/types';
import { useAppSelector } from '../../app/hooks';
import { canSee } from '../../app/navigation';

/** Lets signed-in users through; sends everyone else to /login and back afterwards. */
export function RequireAuth() {
  const status = useAppSelector((s) => s.auth.status);
  const location = useLocation();
  if (status === 'unknown') {
    return (
      <Typography variant="body2" color="text.secondary" sx={{ p: 3 }}>
        Loading…
      </Typography>
    );
  }
  if (status === 'anonymous') {
    return <Navigate to="/login" replace state={{ from: location.pathname + location.search }} />;
  }
  return <Outlet />;
}

/** Hides a route from roles that may not use it (the API would answer 403 anyway). */
export function RequireRole({ roles }: { roles: RoleName[] }) {
  const userRoles = useAppSelector((s) => s.auth.user?.roles ?? []);
  if (!canSee({ roles }, userRoles)) {
    return (
      <Box sx={{ py: 4 }}>
        <Typography variant="h1">Not allowed</Typography>
        <Typography variant="body2" color="text.secondary" sx={{ mt: 1 }}>
          Your role does not have access to this page.
        </Typography>
      </Box>
    );
  }
  return <Outlet />;
}

/** Login / register are only for signed-out users. */
export function GuestOnly() {
  const status = useAppSelector((s) => s.auth.status);
  if (status === 'authenticated') return <Navigate to="/" replace />;
  if (status === 'unknown') return null;
  return <Outlet />;
}
