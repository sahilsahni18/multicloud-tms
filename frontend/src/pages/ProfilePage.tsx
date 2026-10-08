import Alert from '@mui/material/Alert';
import Box from '@mui/material/Box';
import Button from '@mui/material/Button';
import Paper from '@mui/material/Paper';
import TextField from '@mui/material/TextField';
import Typography from '@mui/material/Typography';
import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { useChangePasswordMutation, useUpdateProfileMutation } from '../api/api';
import { errorMessage } from '../api/http';
import { useAppDispatch, useCurrentUser } from '../app/hooks';
import { signOut } from '../app/store';
import { RoleLabel } from '../components/Labels';
import PageHeader from '../components/PageHeader';
import { profileRenamed } from '../features/auth/authSlice';

export default function ProfilePage() {
  const user = useCurrentUser();
  const dispatch = useAppDispatch();
  const navigate = useNavigate();
  const [fullName, setFullName] = useState(user?.fullName ?? '');
  const [saved, setSaved] = useState(false);
  const [passwords, setPasswords] = useState({ currentPassword: '', newPassword: '', confirm: '' });
  const [updateProfile, profileState] = useUpdateProfileMutation();
  const [changePassword, passwordState] = useChangePasswordMutation();

  if (!user) return null;
  const mismatch = passwords.confirm.length > 0 && passwords.confirm !== passwords.newPassword;

  return (
    <>
      <PageHeader title="Profile" />
      <Box sx={{ display: 'grid', gap: 3, maxWidth: 520 }}>
        <Paper sx={{ p: 2 }}>
          <Typography variant="h2" sx={{ mb: 1.5 }}>
            Account
          </Typography>
          <Typography variant="body2" color="text.secondary">
            Email
          </Typography>
          <Typography sx={{ mb: 1.5 }}>{user.email}</Typography>
          <Typography variant="body2" color="text.secondary" sx={{ mb: 0.5 }}>
            Roles
          </Typography>
          <Box sx={{ display: 'flex', gap: 0.5, mb: 2 }}>
            {user.roles.map((r) => (
              <RoleLabel key={r} role={r} />
            ))}
          </Box>
          <Box
            component="form"
            sx={{ display: 'flex', gap: 1, alignItems: 'flex-start' }}
            onSubmit={async (e: React.FormEvent) => {
              e.preventDefault();
              setSaved(false);
              const result = await updateProfile({ fullName: fullName.trim() }).unwrap().catch(() => null);
              if (result) {
                dispatch(profileRenamed(result.fullName));
                setSaved(true);
              }
            }}
          >
            <TextField label="Full name" value={fullName} onChange={(e) => setFullName(e.target.value)} sx={{ flex: 1 }} />
            <Button type="submit" variant="contained" disabled={profileState.isLoading || !fullName.trim() || fullName.trim() === user.fullName}>
              Save
            </Button>
          </Box>
          {saved && (
            <Typography variant="body2" color="success.main" sx={{ mt: 1 }}>
              Saved.
            </Typography>
          )}
          {profileState.error && (
            <Typography variant="body2" color="error" sx={{ mt: 1 }}>
              {errorMessage(profileState.error)}
            </Typography>
          )}
        </Paper>

        <Paper
          component="form"
          sx={{ p: 2, display: 'grid', gap: 1.5 }}
          onSubmit={async (e: React.FormEvent) => {
            e.preventDefault();
            const ok = await changePassword({ currentPassword: passwords.currentPassword, newPassword: passwords.newPassword })
              .unwrap()
              .then(() => true)
              .catch(() => false);
            if (ok) {
              await signOut();
              navigate('/login', { replace: true });
            }
          }}
        >
          <Typography variant="h2">Change password</Typography>
          <Typography variant="body2" color="text.secondary">
            You will be signed out on every device and asked to sign in again.
          </Typography>
          {passwordState.error && (
            <Alert severity="error" variant="outlined">
              {errorMessage(passwordState.error)}
            </Alert>
          )}
          <TextField
            label="Current password"
            type="password"
            autoComplete="current-password"
            value={passwords.currentPassword}
            onChange={(e) => setPasswords({ ...passwords, currentPassword: e.target.value })}
          />
          <TextField
            label="New password"
            type="password"
            autoComplete="new-password"
            value={passwords.newPassword}
            helperText="At least 8 characters"
            onChange={(e) => setPasswords({ ...passwords, newPassword: e.target.value })}
          />
          <TextField
            label="Repeat new password"
            type="password"
            autoComplete="new-password"
            value={passwords.confirm}
            error={mismatch}
            helperText={mismatch ? 'Does not match' : ' '}
            onChange={(e) => setPasswords({ ...passwords, confirm: e.target.value })}
          />
          <Box>
            <Button
              type="submit"
              variant="contained"
              disabled={
                passwordState.isLoading ||
                !passwords.currentPassword ||
                passwords.newPassword.length < 8 ||
                passwords.newPassword !== passwords.confirm
              }
            >
              Change password
            </Button>
          </Box>
        </Paper>
      </Box>
    </>
  );
}
