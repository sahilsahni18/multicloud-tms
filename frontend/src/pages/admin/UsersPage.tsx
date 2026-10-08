import Alert from '@mui/material/Alert';
import Box from '@mui/material/Box';
import Button from '@mui/material/Button';
import Checkbox from '@mui/material/Checkbox';
import Dialog from '@mui/material/Dialog';
import DialogActions from '@mui/material/DialogActions';
import DialogContent from '@mui/material/DialogContent';
import DialogTitle from '@mui/material/DialogTitle';
import FormControlLabel from '@mui/material/FormControlLabel';
import FormGroup from '@mui/material/FormGroup';
import Link from '@mui/material/Link';
import MenuItem from '@mui/material/MenuItem';
import Paper from '@mui/material/Paper';
import Table from '@mui/material/Table';
import TableBody from '@mui/material/TableBody';
import TableCell from '@mui/material/TableCell';
import TableHead from '@mui/material/TableHead';
import TablePagination from '@mui/material/TablePagination';
import TableRow from '@mui/material/TableRow';
import TextField from '@mui/material/TextField';
import Typography from '@mui/material/Typography';
import { useEffect, useState } from 'react';
import {
  useCreateUserMutation,
  useDeleteUserMutation,
  useGetUsersQuery,
  useUpdateUserMutation,
  useUpdateUserRolesMutation,
} from '../../api/api';
import { errorMessage } from '../../api/http';
import { ROLES, type RoleName, type User } from '../../api/types';
import { useCurrentUser } from '../../app/hooks';
import ConfirmDialog from '../../components/ConfirmDialog';
import { ErrorBanner, TableMessage } from '../../components/Feedback';
import { RoleLabel } from '../../components/Labels';
import PageHeader from '../../components/PageHeader';
import UserAvatar from '../../components/UserAvatar';
import { humanize, timeAgo } from '../../utils/format';

function RolePicker({ value, onChange, disabled }: { value: RoleName[]; onChange: (r: RoleName[]) => void; disabled?: RoleName[] }) {
  return (
    <FormGroup row>
      {ROLES.map((role) => (
        <FormControlLabel
          key={role}
          label={<Typography variant="body2">{humanize(role)}</Typography>}
          control={
            <Checkbox
              size="small"
              checked={value.includes(role)}
              disabled={disabled?.includes(role)}
              onChange={(e) => onChange(e.target.checked ? [...value, role] : value.filter((r) => r !== role))}
            />
          }
        />
      ))}
    </FormGroup>
  );
}

function CreateUserDialog({ open, onClose }: { open: boolean; onClose: () => void }) {
  const [form, setForm] = useState({ fullName: '', email: '', password: '' });
  const [roles, setRoles] = useState<RoleName[]>(['USER']);
  const [create, { isLoading, error, reset }] = useCreateUserMutation();

  function close() {
    reset();
    setForm({ fullName: '', email: '', password: '' });
    setRoles(['USER']);
    onClose();
  }

  return (
    <Dialog open={open} onClose={close}>
      <Box
        component="form"
        onSubmit={async (e: React.FormEvent) => {
          e.preventDefault();
          const ok = await create({ ...form, email: form.email.trim(), roles })
            .unwrap()
            .then(() => true)
            .catch(() => false);
          if (ok) close();
        }}
      >
        <DialogTitle>New user</DialogTitle>
        <DialogContent sx={{ display: 'grid', gap: 2, pt: '8px !important' }}>
          {error && (
            <Alert severity="error" variant="outlined">
              {errorMessage(error)}
            </Alert>
          )}
          <TextField label="Full name" value={form.fullName} onChange={(e) => setForm({ ...form, fullName: e.target.value })} required autoFocus />
          <TextField label="Email" type="email" value={form.email} onChange={(e) => setForm({ ...form, email: e.target.value })} required />
          <TextField
            label="Initial password"
            type="password"
            autoComplete="new-password"
            value={form.password}
            onChange={(e) => setForm({ ...form, password: e.target.value })}
            helperText="At least 8 characters; ask them to change it from Profile"
            required
          />
          <Box>
            <Typography variant="body2" color="text.secondary">
              Roles
            </Typography>
            <RolePicker value={roles} onChange={setRoles} />
          </Box>
        </DialogContent>
        <DialogActions sx={{ px: 3, pb: 2 }}>
          <Button onClick={close}>Cancel</Button>
          <Button
            type="submit"
            variant="contained"
            disabled={isLoading || !form.fullName || !form.email || form.password.length < 8 || roles.length === 0}
          >
            Create
          </Button>
        </DialogActions>
      </Box>
    </Dialog>
  );
}

function EditUserDialog({ user, onClose }: { user: User; onClose: () => void }) {
  const me = useCurrentUser();
  const isSelf = me?.id === user.id;
  const [form, setForm] = useState({ fullName: user.fullName, email: user.email, enabled: user.enabled });
  const [roles, setRoles] = useState<RoleName[]>(user.roles);
  const [confirmDelete, setConfirmDelete] = useState(false);
  const [update, updateState] = useUpdateUserMutation();
  const [updateRoles, rolesState] = useUpdateUserRolesMutation();
  const [deleteUser, deleteState] = useDeleteUserMutation();
  const error = updateState.error ?? rolesState.error ?? deleteState.error;

  async function save(e: React.FormEvent) {
    e.preventDefault();
    try {
      await update({ id: user.id, ...form, email: form.email.trim() }).unwrap();
      const changed = roles.length !== user.roles.length || roles.some((r) => !user.roles.includes(r));
      if (changed) await updateRoles({ id: user.id, roles }).unwrap();
      onClose();
    } catch {
      // shown via mutation error state
    }
  }

  return (
    <Dialog open onClose={onClose}>
      <Box component="form" onSubmit={save}>
        <DialogTitle>Edit {user.fullName}</DialogTitle>
        <DialogContent sx={{ display: 'grid', gap: 2, pt: '8px !important' }}>
          {error && (
            <Alert severity="error" variant="outlined">
              {errorMessage(error)}
            </Alert>
          )}
          <TextField label="Full name" value={form.fullName} onChange={(e) => setForm({ ...form, fullName: e.target.value })} required />
          <TextField label="Email" type="email" value={form.email} onChange={(e) => setForm({ ...form, email: e.target.value })} required />
          <FormControlLabel
            label={<Typography variant="body2">Active (can sign in)</Typography>}
            control={
              <Checkbox
                size="small"
                checked={form.enabled}
                disabled={isSelf}
                onChange={(e) => setForm({ ...form, enabled: e.target.checked })}
              />
            }
          />
          <Box>
            <Typography variant="body2" color="text.secondary">
              Roles
            </Typography>
            <RolePicker value={roles} onChange={setRoles} disabled={isSelf ? ['ADMIN'] : undefined} />
          </Box>
        </DialogContent>
        <DialogActions sx={{ px: 3, pb: 2 }}>
          {!isSelf && (
            <Button color="error" sx={{ mr: 'auto' }} onClick={() => setConfirmDelete(true)}>
              Delete user
            </Button>
          )}
          <Button onClick={onClose}>Cancel</Button>
          <Button
            type="submit"
            variant="contained"
            disabled={updateState.isLoading || rolesState.isLoading || roles.length === 0 || !form.fullName || !form.email}
          >
            Save
          </Button>
        </DialogActions>
      </Box>
      <ConfirmDialog
        open={confirmDelete}
        title={`Delete ${user.fullName}?`}
        message="They are signed out and can no longer sign in. Tickets and history they created are kept."
        confirmLabel="Delete"
        danger
        busy={deleteState.isLoading}
        onClose={() => setConfirmDelete(false)}
        onConfirm={async () => {
          const ok = await deleteUser(user.id).unwrap().then(() => true).catch(() => false);
          setConfirmDelete(false);
          if (ok) onClose();
        }}
      />
    </Dialog>
  );
}

export default function UsersPage() {
  const [search, setSearch] = useState('');
  const [q, setQ] = useState('');
  const [role, setRole] = useState<RoleName | ''>('');
  const [page, setPage] = useState(0);
  const [creating, setCreating] = useState(false);
  const [editing, setEditing] = useState<User | null>(null);

  useEffect(() => {
    const t = setTimeout(() => {
      setQ(search.trim());
      setPage(0);
    }, 300);
    return () => clearTimeout(t);
  }, [search]);

  const { data, error } = useGetUsersQuery({ q: q || undefined, role: role || undefined, page, size: 50, sort: 'fullName,asc' });

  return (
    <>
      <PageHeader
        title="Users"
        description={data && `${data.totalElements} user${data.totalElements === 1 ? '' : 's'}`}
        actions={
          <Button variant="contained" onClick={() => setCreating(true)}>
            New user
          </Button>
        }
      />
      <Box sx={{ display: 'flex', gap: 1, mb: 1.5 }}>
        <TextField placeholder="Name or email" value={search} onChange={(e) => setSearch(e.target.value)} sx={{ width: 260 }} />
        <TextField
          select
          label="Role"
          value={role}
          onChange={(e) => {
            setRole(e.target.value as RoleName | '');
            setPage(0);
          }}
          sx={{ width: 180 }}
        >
          <MenuItem value="">Any role</MenuItem>
          {ROLES.map((r) => (
            <MenuItem key={r} value={r}>
              {humanize(r)}
            </MenuItem>
          ))}
        </TextField>
      </Box>
      <ErrorBanner error={error} />
      <Paper>
        <Table size="small">
          <TableHead>
            <TableRow>
              <TableCell>Name</TableCell>
              <TableCell>Email</TableCell>
              <TableCell>Roles</TableCell>
              <TableCell>Status</TableCell>
              <TableCell>Last sign-in</TableCell>
              <TableCell sx={{ width: 60 }} />
            </TableRow>
          </TableHead>
          <TableBody>
            {!data && !error && <TableMessage colSpan={6}>Loading…</TableMessage>}
            {data?.content.length === 0 && <TableMessage colSpan={6}>No users match.</TableMessage>}
            {data?.content.map((u) => (
              <TableRow key={u.id} hover>
                <TableCell>
                  <UserAvatar name={u.fullName} withName />
                </TableCell>
                <TableCell>{u.email}</TableCell>
                <TableCell>
                  <Box sx={{ display: 'flex', gap: 0.5, flexWrap: 'wrap' }}>
                    {u.roles.map((r) => (
                      <RoleLabel key={r} role={r} />
                    ))}
                  </Box>
                </TableCell>
                <TableCell sx={{ color: u.enabled ? undefined : 'error.main' }}>{u.enabled ? 'Active' : 'Disabled'}</TableCell>
                <TableCell sx={{ color: 'text.secondary' }}>{u.lastLoginAt ? timeAgo(u.lastLoginAt) : 'Never'}</TableCell>
                <TableCell align="right">
                  <Link component="button" variant="body2" onClick={() => setEditing(u)}>
                    Edit
                  </Link>
                </TableCell>
              </TableRow>
            ))}
          </TableBody>
        </Table>
        {data && data.totalPages > 1 && (
          <TablePagination
            component="div"
            count={data.totalElements}
            page={page}
            rowsPerPage={50}
            rowsPerPageOptions={[50]}
            onPageChange={(_e, p) => setPage(p)}
          />
        )}
      </Paper>
      <CreateUserDialog open={creating} onClose={() => setCreating(false)} />
      {editing && <EditUserDialog user={editing} onClose={() => setEditing(null)} />}
    </>
  );
}
