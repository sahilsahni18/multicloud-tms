import Box from '@mui/material/Box';
import Button from '@mui/material/Button';
import Link from '@mui/material/Link';
import Typography from '@mui/material/Typography';
import { useState } from 'react';
import { Link as RouterLink, useNavigate, useParams } from 'react-router-dom';
import {
  useAssignTicketMutation,
  useDeleteTicketMutation,
  useGetMembersQuery,
  useGetTicketQuery,
  useTransitionTicketMutation,
} from '../../api/api';
import type { TicketStatus } from '../../api/types';
import ConfirmDialog from '../../components/ConfirmDialog';
import { ErrorBanner, Loading } from '../../components/Feedback';
import { PriorityText, StatusLabel, TypeText } from '../../components/Labels';
import UserPicker from '../../components/UserPicker';
import { colors } from '../../theme';
import { formatDate, formatDateTime } from '../../utils/format';
import EditTicketDialog from './EditTicketDialog';
import Timeline from './Timeline';

/** Button text for a move, by target (and source, for the backwards moves). */
function transitionLabel(from: TicketStatus, to: TicketStatus): string {
  if (to === 'IN_PROGRESS') return from === 'IN_REVIEW' ? 'Back to in progress' : 'Start progress';
  if (to === 'IN_REVIEW') return 'Send to review';
  if (to === 'CLOSED') return 'Close';
  return from === 'CLOSED' ? 'Reopen' : 'Back to open';
}

function Field({ label, children }: { label: string; children: React.ReactNode }) {
  return (
    <Box sx={{ py: 1, borderBottom: 1, borderColor: 'divider' }}>
      <Typography sx={{ fontSize: 12, fontWeight: 600, color: 'text.secondary', mb: 0.25 }}>{label}</Typography>
      <Box sx={{ fontSize: 14 }}>{children}</Box>
    </Box>
  );
}

export default function TicketDetailPage() {
  const id = Number(useParams().id);
  const navigate = useNavigate();
  const { data: ticket, error, isLoading } = useGetTicketQuery(id);
  const { data: members } = useGetMembersQuery(ticket?.projectId ?? 0, { skip: !ticket?.permissions.canAssign });
  const [transition, transitionState] = useTransitionTicketMutation();
  const [assign, assignState] = useAssignTicketMutation();
  const [deleteTicket, deleteState] = useDeleteTicketMutation();
  const [editing, setEditing] = useState(false);
  const [confirmDelete, setConfirmDelete] = useState(false);

  if (isLoading) return <Loading />;
  if (!ticket) return <ErrorBanner error={error} fallback="Ticket not found" />;

  const memberOptions = members?.map((m) => ({ id: m.id, fullName: m.fullName, email: m.email })) ?? [];

  return (
    <>
      <Typography variant="body2" color="text.secondary" sx={{ mb: 0.5 }}>
        <Link component={RouterLink} to={`/projects/${ticket.projectId}`} color="inherit">
          {ticket.projectName}
        </Link>{' '}
        / <span className="mono">{ticket.key}</span>
      </Typography>
      <Box sx={{ display: 'flex', gap: 2, alignItems: 'flex-start', pb: 1.5, mb: 2, borderBottom: 1, borderColor: 'divider' }}>
        <Typography variant="h1" sx={{ flex: 1 }}>
          {ticket.title}{' '}
          <Box component="span" sx={{ color: 'text.secondary', fontWeight: 400 }}>
            {ticket.key}
          </Box>
        </Typography>
        {ticket.permissions.canEdit && <Button onClick={() => setEditing(true)}>Edit</Button>}
        {ticket.permissions.canDelete && (
          <Button color="error" onClick={() => setConfirmDelete(true)}>
            Delete
          </Button>
        )}
      </Box>

      <ErrorBanner error={transitionState.error ?? assignState.error} />

      <Box sx={{ display: 'grid', gridTemplateColumns: 'minmax(0, 1fr) 260px', gap: 4 }}>
        <Box>
          <Box sx={{ border: 1, borderColor: 'divider', borderRadius: 1, mb: 3 }}>
            <Box sx={{ px: 1.5, py: 0.75, bgcolor: colors.subtle, borderBottom: 1, borderColor: 'divider', fontSize: 13 }}>
              <b>{ticket.reporter.fullName}</b>{' '}
              <Box component="span" sx={{ color: 'text.secondary' }}>
                opened this on {formatDateTime(ticket.createdAt)}
              </Box>
            </Box>
            <Typography sx={{ px: 1.5, py: 1.25, whiteSpace: 'pre-wrap', fontSize: 14, color: ticket.description ? undefined : 'text.secondary' }}>
              {ticket.description || 'No description.'}
            </Typography>
          </Box>
          <Timeline ticketId={ticket.id} canComment={ticket.permissions.canComment} />
        </Box>

        <Box>
          <Field label="Status">
            <StatusLabel status={ticket.status} />
            {ticket.allowedTransitions.length > 0 && (
              <Box sx={{ display: 'flex', flexDirection: 'column', gap: 0.75, mt: 1 }}>
                {ticket.allowedTransitions.map((to) => (
                  <Button
                    key={to}
                    variant={to === 'CLOSED' || (ticket.status === 'OPEN' && to === 'IN_PROGRESS') ? 'contained' : 'outlined'}
                    disabled={transitionState.isLoading}
                    onClick={() => transition({ id: ticket.id, status: to })}
                  >
                    {transitionLabel(ticket.status, to)}
                  </Button>
                ))}
              </Box>
            )}
          </Field>
          <Field label="Assignee">
            {ticket.permissions.canAssign ? (
              <Box sx={{ mt: 0.5 }}>
                <UserPicker
                  value={ticket.assignee ?? null}
                  options={memberOptions}
                  disabled={assignState.isLoading}
                  onChange={(user) => assign({ id: ticket.id, assigneeId: user?.id ?? null })}
                />
              </Box>
            ) : (
              (ticket.assignee?.fullName ?? <Box component="span" sx={{ color: 'text.secondary' }}>Unassigned</Box>)
            )}
          </Field>
          <Field label="Priority">
            <PriorityText priority={ticket.priority} />
          </Field>
          <Field label="Type">
            <TypeText type={ticket.type} />
          </Field>
          <Field label="Reporter">{ticket.reporter.fullName}</Field>
          <Field label="Due date">{ticket.dueDate ? formatDate(ticket.dueDate) : <Box component="span" sx={{ color: 'text.secondary' }}>None</Box>}</Field>
          <Field label="Updated">{formatDateTime(ticket.updatedAt)}</Field>
          {ticket.closedAt && <Field label="Closed">{formatDateTime(ticket.closedAt)}</Field>}
        </Box>
      </Box>

      {editing && <EditTicketDialog ticket={ticket} onClose={() => setEditing(false)} />}
      <ConfirmDialog
        open={confirmDelete}
        title={`Delete ${ticket.key}?`}
        message="The ticket disappears from all lists. Its history is kept."
        confirmLabel="Delete"
        danger
        busy={deleteState.isLoading}
        onClose={() => setConfirmDelete(false)}
        onConfirm={async () => {
          await deleteTicket(ticket.id).unwrap().catch(() => undefined);
          navigate('/tickets');
        }}
      />
    </>
  );
}
