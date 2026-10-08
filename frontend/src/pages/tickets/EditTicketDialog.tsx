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
import { useUpdateTicketMutation } from '../../api/api';
import { errorMessage } from '../../api/http';
import { TICKET_PRIORITIES, TICKET_TYPES, type TicketDetail, type TicketPriority, type TicketType } from '../../api/types';
import { humanize } from '../../utils/format';

/** Edits the ticket fields. Sends the loaded version, so a concurrent edit comes back as 409. */
export default function EditTicketDialog({ ticket, onClose }: { ticket: TicketDetail; onClose: () => void }) {
  const [title, setTitle] = useState(ticket.title);
  const [description, setDescription] = useState(ticket.description ?? '');
  const [type, setType] = useState<TicketType>(ticket.type);
  const [priority, setPriority] = useState<TicketPriority>(ticket.priority);
  const [dueDate, setDueDate] = useState(ticket.dueDate ?? '');
  const [update, { isLoading, error }] = useUpdateTicketMutation();

  async function submit(e: React.FormEvent) {
    e.preventDefault();
    const ok = await update({
      id: ticket.id,
      title: title.trim(),
      description: description.trim() || undefined,
      type,
      priority,
      dueDate: dueDate || null,
      version: ticket.version,
    })
      .unwrap()
      .then(() => true)
      .catch(() => false);
    if (ok) onClose();
  }

  return (
    <Dialog open onClose={onClose}>
      <Box component="form" onSubmit={submit}>
        <DialogTitle>Edit {ticket.key}</DialogTitle>
        <DialogContent sx={{ display: 'grid', gap: 2, pt: '8px !important' }}>
          {error && (
            <Alert severity="error" variant="outlined">
              {errorMessage(error)}
            </Alert>
          )}
          <TextField label="Title" value={title} onChange={(e) => setTitle(e.target.value)} required slotProps={{ htmlInput: { maxLength: 200 } }} />
          <TextField label="Description" value={description} onChange={(e) => setDescription(e.target.value)} multiline minRows={5} />
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
        </DialogContent>
        <DialogActions sx={{ px: 3, pb: 2 }}>
          <Button onClick={onClose}>Cancel</Button>
          <Button type="submit" variant="contained" disabled={isLoading || !title.trim()}>
            Save
          </Button>
        </DialogActions>
      </Box>
    </Dialog>
  );
}
