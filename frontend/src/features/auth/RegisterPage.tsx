import Alert from '@mui/material/Alert';
import Box from '@mui/material/Box';
import Button from '@mui/material/Button';
import Link from '@mui/material/Link';
import TextField from '@mui/material/TextField';
import { useState } from 'react';
import { Link as RouterLink, useNavigate } from 'react-router-dom';
import { errorMessage } from '../../api/http';
import { useAppDispatch } from '../../app/hooks';
import AuthLayout from './AuthLayout';
import { register } from './authSlice';

export default function RegisterPage() {
  const dispatch = useAppDispatch();
  const navigate = useNavigate();
  const [form, setForm] = useState({ fullName: '', email: '', password: '' });
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);
  const tooShort = form.password.length > 0 && form.password.length < 8;

  async function submit(e: React.FormEvent) {
    e.preventDefault();
    setBusy(true);
    setError(null);
    try {
      await dispatch(register({ ...form, email: form.email.trim() })).unwrap();
      navigate('/', { replace: true });
    } catch (err) {
      setError(errorMessage(err, 'Could not create the account'));
    } finally {
      setBusy(false);
    }
  }

  const set = (field: keyof typeof form) => (e: React.ChangeEvent<HTMLInputElement>) =>
    setForm({ ...form, [field]: e.target.value });

  return (
    <AuthLayout
      title="Create an account"
      footer={
        <>
          Already have one?{' '}
          <Link component={RouterLink} to="/login">
            Sign in
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
        <TextField label="Full name" value={form.fullName} onChange={set('fullName')} autoFocus required />
        <TextField label="Email" type="email" autoComplete="email" value={form.email} onChange={set('email')} required />
        <TextField
          label="Password"
          type="password"
          autoComplete="new-password"
          value={form.password}
          onChange={set('password')}
          error={tooShort}
          helperText="At least 8 characters"
          required
        />
        <Button
          type="submit"
          variant="contained"
          disabled={busy || !form.fullName || !form.email || form.password.length < 8}
        >
          {busy ? 'Creating…' : 'Create account'}
        </Button>
      </Box>
    </AuthLayout>
  );
}
