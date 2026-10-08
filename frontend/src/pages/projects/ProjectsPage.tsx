import Alert from '@mui/material/Alert';
import Box from '@mui/material/Box';
import Button from '@mui/material/Button';
import Dialog from '@mui/material/Dialog';
import DialogActions from '@mui/material/DialogActions';
import DialogContent from '@mui/material/DialogContent';
import DialogTitle from '@mui/material/DialogTitle';
import Link from '@mui/material/Link';
import Paper from '@mui/material/Paper';
import Table from '@mui/material/Table';
import TableBody from '@mui/material/TableBody';
import TableCell from '@mui/material/TableCell';
import TableHead from '@mui/material/TableHead';
import TableRow from '@mui/material/TableRow';
import TextField from '@mui/material/TextField';
import { useState } from 'react';
import { Link as RouterLink, useNavigate } from 'react-router-dom';
import { useCreateProjectMutation, useGetProjectsQuery } from '../../api/api';
import { errorMessage } from '../../api/http';
import { useHasRole } from '../../app/hooks';
import { ErrorBanner, TableMessage } from '../../components/Feedback';
import PageHeader from '../../components/PageHeader';
import UserAvatar, { ProjectAvatar } from '../../components/UserAvatar';

function NewProjectDialog({ open, onClose }: { open: boolean; onClose: () => void }) {
  const navigate = useNavigate();
  const [key, setKey] = useState('');
  const [name, setName] = useState('');
  const [description, setDescription] = useState('');
  const [create, { isLoading, error, reset }] = useCreateProjectMutation();
  const keyValid = /^[A-Z][A-Z0-9]{1,9}$/.test(key);

  function close() {
    reset();
    setKey('');
    setName('');
    setDescription('');
    onClose();
  }

  async function submit(e: React.FormEvent) {
    e.preventDefault();
    const project = await create({ key, name: name.trim(), description: description.trim() || undefined })
      .unwrap()
      .catch(() => null);
    if (project) {
      close();
      navigate(`/projects/${project.id}`);
    }
  }

  return (
    <Dialog open={open} onClose={close}>
      <Box component="form" onSubmit={submit}>
        <DialogTitle>New project</DialogTitle>
        <DialogContent sx={{ display: 'grid', gap: 2, pt: '8px !important' }}>
          {error && (
            <Alert severity="error" variant="outlined">
              {errorMessage(error)}
            </Alert>
          )}
          <Box sx={{ display: 'grid', gridTemplateColumns: '140px 1fr', gap: 2 }}>
            <TextField
              label="Key"
              value={key}
              onChange={(e) => setKey(e.target.value.toUpperCase().replace(/[^A-Z0-9]/g, '').slice(0, 10))}
              error={key.length > 0 && !keyValid}
              helperText="e.g. WEB → WEB-1"
              required
              autoFocus
            />
            <TextField label="Name" value={name} onChange={(e) => setName(e.target.value)} required />
          </Box>
          <TextField
            label="Description"
            value={description}
            onChange={(e) => setDescription(e.target.value)}
            multiline
            minRows={3}
          />
        </DialogContent>
        <DialogActions sx={{ px: 3, pb: 2 }}>
          <Button onClick={close}>Cancel</Button>
          <Button type="submit" variant="contained" disabled={isLoading || !keyValid || !name.trim()}>
            Create
          </Button>
        </DialogActions>
      </Box>
    </Dialog>
  );
}

export default function ProjectsPage() {
  const canCreate = useHasRole('ADMIN', 'PROJECT_MANAGER');
  const [creating, setCreating] = useState(false);
  const { data, error } = useGetProjectsQuery({});

  return (
    <>
      <PageHeader
        title="Projects"
        actions={
          canCreate && (
            <Button variant="contained" onClick={() => setCreating(true)}>
              New project
            </Button>
          )
        }
      />
      <ErrorBanner error={error} />
      <Paper>
        <Table size="small">
          <TableHead>
            <TableRow>
              <TableCell>Name</TableCell>
              <TableCell sx={{ width: 90 }}>Key</TableCell>
              <TableCell>Lead</TableCell>
              <TableCell align="right">Members</TableCell>
              <TableCell align="right">Open</TableCell>
              <TableCell align="right">Total</TableCell>
              <TableCell sx={{ width: 70 }} />
            </TableRow>
          </TableHead>
          <TableBody>
            {!data && !error && <TableMessage colSpan={7}>Loading…</TableMessage>}
            {data?.content.length === 0 && <TableMessage colSpan={7}>You are not a member of any project yet.</TableMessage>}
            {data?.content.map((p) => (
              <TableRow key={p.id} hover>
                <TableCell>
                  <Box sx={{ display: 'flex', alignItems: 'center', gap: 1.25 }}>
                    <ProjectAvatar projectKey={p.key} />
                    <Link component={RouterLink} to={`/projects/${p.id}`} sx={{ fontWeight: 500 }}>
                      {p.name}
                    </Link>
                  </Box>
                </TableCell>
                <TableCell>{p.key}</TableCell>
                <TableCell>
                  <UserAvatar name={p.owner.fullName} withName />
                </TableCell>
                <TableCell align="right">{p.memberCount}</TableCell>
                <TableCell align="right">
                  <Link component={RouterLink} to={`/tickets?projectId=${p.id}&status=OPEN&status=IN_PROGRESS&status=IN_REVIEW`}>
                    {p.openTickets}
                  </Link>
                </TableCell>
                <TableCell align="right">{p.totalTickets}</TableCell>
                <TableCell align="right">
                  <Link component={RouterLink} to={`/board?projectId=${p.id}`} variant="body2">
                    Board
                  </Link>
                </TableCell>
              </TableRow>
            ))}
          </TableBody>
        </Table>
      </Paper>
      <NewProjectDialog open={creating} onClose={() => setCreating(false)} />
    </>
  );
}
