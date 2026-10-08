import Alert from '@mui/material/Alert';
import Box from '@mui/material/Box';
import Button from '@mui/material/Button';
import Dialog from '@mui/material/Dialog';
import DialogActions from '@mui/material/DialogActions';
import DialogContent from '@mui/material/DialogContent';
import DialogTitle from '@mui/material/DialogTitle';
import MenuItem from '@mui/material/MenuItem';
import TextField from '@mui/material/TextField';
import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { useCreateTicketMutation, useGetMembersQuery, useGetProjectsQuery } from '../../api/api';
import { errorMessage } from '../../api/http';
import { TICKET_PRIORITIES, TICKET_TYPES, type TicketPriority, type TicketType, type UserRef } from '../../api/types';
import { useHasRole } from '../../app/hooks';
import UserPicker from '../../components/UserPicker';
import { humanize } from '../../utils/format';

interface Props {
  open: boolean;
  onClose: () => void;
  defaultProjectId?: number;
}

export default function NewTicketDialog({ open, onClose, defaultProjectId }: Props) {
  const navigate = useNavigate();
  const canAssign = useHasRole('ADMIN', 'PROJECT_MANAGER');
  const { data: projects } = useGetProjectsQuery({}, { skip: !open });
  const [projectId, setProjectId] = useState<number | ''>(defaultProjectId ?? '');
  const [title, setTitle] = useState('');
  const [description, setDescription] = useState('');
  const [type, setType] = useState<TicketType>('TASK');
  const [priority, setPriority] = useState<TicketPriority>('MEDIUM');
  const [dueDate, setDueDate] = useState('');
  const [assignee, setAssignee] = useState<UserRef | null>(null);
  const { data: members } = useGetMembersQuery(projectId as number, { skip: !open || !projectId || !canAssign });
  const [createTicket, { isLoading, error, reset }] = useCreateTicketMutation();

  function close() {
    reset();
    setTitle('');
    setDescription('');
    setAssignee(null);
    setDueDate('');
    onClose();
  }

  async function submit(e: React.FormEvent) {
    e.preventDefault();
    if (!projectId) return;
    const ticket = await createTicket({
      projectId,
      title: title.trim(),
      description: description.trim() || undefined,
      type,
      priority,
      dueDate: dueDate || undefined,
      assigneeId: assignee?.id,
    }).unwrap().catch(() => null);
    if (ticket) {
      close();
      navigate(`/tickets/${ticket.id}`);
    }
  }

  return (
    <Dialog open={open} onClose={close}>
      <Box component="form" onSubmit={submit}>
        <DialogTitle>New ticket</DialogTitle>
        <DialogContent sx={{ display: 'grid', gap: 2, pt: '8px !important' }}>
          {error && (
            <Alert severity="error" variant="outlined">
              {errorMessage(error)}
            </Alert>
          )}
          <TextField
            select
            label="Project"
            value={projectId}
            onChange={(e) => {
              setProjectId(Number(e.target.value));
              setAssignee(null);
            }}
            required
          >
            {projects?.content.map((p) => (
              <MenuItem key={p.id} value={p.id}>
                {p.key} – {p.name}
              </MenuItem>
            ))}
          </TextField>
          <TextField label="Title" value={title} onChange={(e) => setTitle(e.target.value)} required autoFocus slotProps={{ htmlInput: { maxLength: 200 } }} />
          <TextField label="Description" value={description} onChange={(e) => setDescription(e.target.value)} multiline minRows={4} />
          <Box sx={{ display: 'grid', gridTemplateColumns: '1fr 1fr 1fr', gap: 2 }}>
            <TextField select label="Type" value={type} onChange={(e) => setType(e.target.value as TicketType)}>
              {TICKET_TYPES.map((t) => (
                <MenuItem key={t} value={t}>
                  {humanize(t)}
                </MenuItem>
              ))}
            </TextField>
            <TextField select label="Priority" value={priority} onChange={(e) => setPriority(e.target.value as TicketPriority)}>
              {TICKET_PRIORITIES.map((p) => (
                <MenuItem key={p} value={p}>
                  {humanize(p)}
                </MenuItem>
              ))}
            </TextField>
            <TextField
              label="Due date"
              type="date"
              value={dueDate}
              onChange={(e) => setDueDate(e.target.value)}
              slotProps={{ inputLabel: { shrink: true } }}
            />
          </Box>
          {canAssign && (
            <UserPicker
              label="Assignee (optional)"
              value={assignee}
              onChange={setAssignee}
              options={members?.map((m) => ({ id: m.id, fullName: m.fullName, email: m.email })) ?? []}
              disabled={!projectId}
            />
          )}
        </DialogContent>
        <DialogActions sx={{ px: 3, pb: 2 }}>
          <Button onClick={close}>Cancel</Button>
          <Button type="submit" variant="contained" disabled={isLoading || !projectId || !title.trim()}>
            Create
          </Button>
        </DialogActions>
      </Box>
    </Dialog>
  );
}
