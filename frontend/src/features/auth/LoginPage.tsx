import Alert from '@mui/material/Alert';
import Box from '@mui/material/Box';
import Button from '@mui/material/Button';
import Link from '@mui/material/Link';
import TextField from '@mui/material/TextField';
import Typography from '@mui/material/Typography';
import { useState } from 'react';
import { Link as RouterLink, useLocation, useNavigate } from 'react-router-dom';
import { errorMessage } from '../../api/http';
import { useAppDispatch } from '../../app/hooks';
import AuthLayout from './AuthLayout';
import { login } from './authSlice';

const DEMO_ACCOUNTS = [
  ['admin@trackflow.dev', 'Admin'],
  ['pm@trackflow.dev', 'Project manager'],
  ['dev@trackflow.dev', 'Developer'],
  ['user@trackflow.dev', 'User'],
];

export default function LoginPage() {
  const dispatch = useAppDispatch();
  const navigate = useNavigate();
  const location = useLocation();
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  async function submit(e: React.FormEvent) {
    e.preventDefault();
    setBusy(true);
    setError(null);
    try {
      await dispatch(login({ email: email.trim(), password })).unwrap();
      const from = (location.state as { from?: string } | null)?.from;
      navigate(from && from !== '/login' ? from : '/', { replace: true });
    } catch (err) {
      setError(errorMessage(err, 'Sign in failed'));
    } finally {
      setBusy(false);
    }
  }

  return (
    <AuthLayout
      title="Sign in"
      footer={
        <>
          New here?{' '}
          <Link component={RouterLink} to="/register">
            Create an account
          </Link>
        </>
      }
    >
      <Box component="form" onSubmit={submit} noValidate sx={{ display: 'grid', gap: 1.5 }}>
        {error && (
          <Alert severity="error" variant="outlined">
            {error}
          </Alert>
        )}
        <TextField
          label="Email"
          type="email"
          autoComplete="username"
          value={email}
          onChange={(e) => setEmail(e.target.value)}
          autoFocus
          required
        />
        <TextField
          label="Password"
          type="password"
          autoComplete="current-password"
          value={password}
          onChange={(e) => setPassword(e.target.value)}
          required
        />
        <Button type="submit" variant="contained" disabled={busy || !email || !password}>
          {busy ? 'Signing in…' : 'Sign in'}
        </Button>
      </Box>

      {import.meta.env.DEV && (
        <Box sx={{ mt: 2.5, pt: 1.5, borderTop: 1, borderColor: 'divider' }}>
          <Typography variant="body2" color="text.secondary" sx={{ mb: 0.5 }}>
            Demo accounts (password <code>Password@123</code>)
          </Typography>
          {DEMO_ACCOUNTS.map(([demoEmail, label]) => (
            <Link
              key={demoEmail}
              component="button"
              type="button"
              variant="body2"
              onClick={() => {
                setEmail(demoEmail);
                setPassword('Password@123');
              }}
              sx={{ display: 'block' }}
            >
              {label} – {demoEmail}
            </Link>
          ))}
        </Box>
      )}
    </AuthLayout>
  );
}
