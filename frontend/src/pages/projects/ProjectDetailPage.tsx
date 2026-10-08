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
import Typography from '@mui/material/Typography';
import { useState } from 'react';
import { Link as RouterLink, useNavigate, useParams } from 'react-router-dom';
import {
  useAddMemberMutation,
  useDeleteProjectMutation,
  useGetMembersQuery,
  useGetProjectQuery,
  useRemoveMemberMutation,
  useUpdateProjectMutation,
} from '../../api/api';
import { errorMessage } from '../../api/http';
import type { Project, UserRef } from '../../api/types';
import { useHasRole } from '../../app/hooks';
import ConfirmDialog from '../../components/ConfirmDialog';
import { ErrorBanner, Loading } from '../../components/Feedback';
import { RoleLabel } from '../../components/Labels';
import PageHeader from '../../components/PageHeader';
import UserAvatar, { ProjectAvatar } from '../../components/UserAvatar';
import UserPicker from '../../components/UserPicker';
import NewTicketDialog from '../tickets/NewTicketDialog';

function EditProjectDialog({ project, onClose }: { project: Project; onClose: () => void }) {
  const [name, setName] = useState(project.name);
  const [description, setDescription] = useState(project.description ?? '');
  const [update, { isLoading, error }] = useUpdateProjectMutation();
  return (
    <Dialog open onClose={onClose}>
      <Box
        component="form"
        onSubmit={async (e: React.FormEvent) => {
          e.preventDefault();
          const ok = await update({ id: project.id, name: name.trim(), description: description.trim() || undefined })
            .unwrap()
            .then(() => true)
            .catch(() => false);
          if (ok) onClose();
        }}
      >
        <DialogTitle>Edit {project.key}</DialogTitle>
        <DialogContent sx={{ display: 'grid', gap: 2, pt: '8px !important' }}>
          {error && (
            <Alert severity="error" variant="outlined">
              {errorMessage(error)}
            </Alert>
          )}
          <TextField label="Name" value={name} onChange={(e) => setName(e.target.value)} required />
          <TextField label="Description" value={description} onChange={(e) => setDescription(e.target.value)} multiline minRows={3} />
        </DialogContent>
        <DialogActions sx={{ px: 3, pb: 2 }}>
          <Button onClick={onClose}>Cancel</Button>
          <Button type="submit" variant="contained" disabled={isLoading || !name.trim()}>
            Save
          </Button>
        </DialogActions>
      </Box>
    </Dialog>
  );
}

export default function ProjectDetailPage() {
  const id = Number(useParams().id);
  const navigate = useNavigate();
  const isAdmin = useHasRole('ADMIN');
  const { data: project, error, isLoading } = useGetProjectQuery(id);
  const { data: members } = useGetMembersQuery(id, { skip: !project });
  const [addMember, addState] = useAddMemberMutation();
  const [removeMember, removeState] = useRemoveMemberMutation();
  const [deleteProject, deleteState] = useDeleteProjectMutation();
  const [newMember, setNewMember] = useState<UserRef | null>(null);
  const [editing, setEditing] = useState(false);
  const [creatingTicket, setCreatingTicket] = useState(false);
  const [confirmDelete, setConfirmDelete] = useState(false);

  if (isLoading) return <Loading />;
  if (!project) return <ErrorBanner error={error} fallback="Project not found" />;

  return (
    <>
      <PageHeader
        breadcrumbs={[{ label: 'Projects', to: '/projects' }, { label: project.name }]}
        title={
          <Box component="span" sx={{ display: 'inline-flex', alignItems: 'center', gap: 1.5 }}>
            <ProjectAvatar projectKey={project.key} size={32} />
            {project.name}
            <Box component="span" sx={{ color: 'text.secondary', fontWeight: 400, fontSize: 16 }}>
              {project.key}
            </Box>
          </Box>
        }
        description={`Lead: ${project.owner.fullName}`}
        actions={
          <>
            <Button variant="outlined" component={RouterLink} to={`/board?projectId=${project.id}`}>
              Open board
            </Button>
            <Button onClick={() => setCreatingTicket(true)}>New ticket</Button>
            {project.canManage && <Button onClick={() => setEditing(true)}>Edit</Button>}
            {isAdmin && (
              <Button color="error" onClick={() => setConfirmDelete(true)}>
                Delete
              </Button>
            )}
          </>
        }
      />

      {project.description && (
        <Typography sx={{ whiteSpace: 'pre-wrap', mb: 2, fontSize: 14 }}>{project.description}</Typography>
      )}
      <Typography variant="body2" sx={{ mb: 3 }}>
        <Link component={RouterLink} to={`/tickets?projectId=${project.id}&status=OPEN&status=IN_PROGRESS&status=IN_REVIEW`}>
          {project.openTickets} open
        </Link>
        {' · '}
        <Link component={RouterLink} to={`/tickets?projectId=${project.id}`}>
          {project.totalTickets} total tickets
        </Link>
      </Typography>

      <Typography variant="h2" sx={{ mb: 1 }}>
        Members
      </Typography>
      <ErrorBanner error={addState.error ?? removeState.error} />
      {project.canManage && (
        <Box sx={{ display: 'flex', gap: 1, mb: 1.5, maxWidth: 480 }}>
          <Box sx={{ flex: 1 }}>
            <UserPicker label="Add a member" value={newMember} onChange={setNewMember} />
          </Box>
          <Button
            variant="contained"
            disabled={!newMember || addState.isLoading}
            onClick={async () => {
              if (!newMember) return;
              await addMember({ projectId: project.id, userId: newMember.id }).unwrap().catch(() => undefined);
              setNewMember(null);
            }}
          >
            Add
          </Button>
        </Box>
      )}
      <Paper>
        <Table size="small">
          <TableHead>
            <TableRow>
              <TableCell>Name</TableCell>
              <TableCell>Email</TableCell>
              <TableCell>Roles</TableCell>
              {project.canManage && <TableCell sx={{ width: 90 }} />}
            </TableRow>
          </TableHead>
          <TableBody>
            {members?.map((m) => (
              <TableRow key={m.id}>
                <TableCell>
                  <UserAvatar name={m.fullName} withName />
                  {m.owner && (
                    <Box component="span" sx={{ color: 'text.secondary', ml: 1, fontSize: 12 }}>
                      lead
                    </Box>
                  )}
                </TableCell>
                <TableCell>{m.email}</TableCell>
                <TableCell>
                  <Box sx={{ display: 'flex', gap: 0.5 }}>
                    {m.roles.map((r) => (
                      <RoleLabel key={r} role={r} />
                    ))}
                  </Box>
                </TableCell>
                {project.canManage && (
                  <TableCell align="right">
                    {!m.owner && (
                      <Link
                        component="button"
                        variant="body2"
                        color="error"
                        onClick={() => removeMember({ projectId: project.id, userId: m.id })}
                      >
                        Remove
                      </Link>
                    )}
                  </TableCell>
                )}
              </TableRow>
            ))}
          </TableBody>
        </Table>
      </Paper>

      {editing && <EditProjectDialog project={project} onClose={() => setEditing(false)} />}
      <NewTicketDialog open={creatingTicket} onClose={() => setCreatingTicket(false)} defaultProjectId={project.id} />
      <ConfirmDialog
        open={confirmDelete}
        title={`Delete project ${project.key}?`}
        message="The project and its tickets disappear from every list. History is kept in the database."
        confirmLabel="Delete project"
        danger
        busy={deleteState.isLoading}
        onClose={() => setConfirmDelete(false)}
        onConfirm={async () => {
          await deleteProject(project.id).unwrap().catch(() => undefined);
          navigate('/projects');
        }}
      />
    </>
  );
}
