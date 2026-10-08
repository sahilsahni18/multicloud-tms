import KeyboardArrowDownIcon from '@mui/icons-material/KeyboardArrowDown';
import Box from '@mui/material/Box';
import Button from '@mui/material/Button';
import Menu from '@mui/material/Menu';
import MenuItem from '@mui/material/MenuItem';
import Typography from '@mui/material/Typography';
import { useState } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import {
  useAssignTicketMutation,
  useDeleteTicketMutation,
  useGetMembersQuery,
  useGetTicketQuery,
  useTransitionTicketMutation,
} from '../../api/api';
import type { TicketDetail, TicketStatus } from '../../api/types';
import ConfirmDialog from '../../components/ConfirmDialog';
import { ErrorBanner, Loading } from '../../components/Feedback';
import { PriorityIcon, StatusLabel, TypeIcon } from '../../components/Labels';
import { LOZENGE, STATUS_TONE } from '../../components/tones';
import PageHeader from '../../components/PageHeader';
import UserAvatar from '../../components/UserAvatar';
import UserPicker from '../../components/UserPicker';
import { colors } from '../../theme';
import { formatDate, formatDateTime, humanize, timeAgo } from '../../utils/format';
import EditTicketDialog from './EditTicketDialog';
import Timeline from './Timeline';

/** Menu text for a move, by target (and source, for the backwards moves). */
function transitionLabel(from: TicketStatus, to: TicketStatus): string {
  if (to === 'IN_PROGRESS') return from === 'IN_REVIEW' ? 'Back to in progress' : 'Start progress';
  if (to === 'IN_REVIEW') return 'Send to review';
  if (to === 'CLOSED') return 'Close';
  return from === 'CLOSED' ? 'Reopen' : 'Back to open';
}

/** Jira-style status button: shows the current status, opens the allowed moves. */
function StatusButton({ ticket, busy, onMove }: { ticket: TicketDetail; busy: boolean; onMove: (to: TicketStatus) => void }) {
  const [anchor, setAnchor] = useState<HTMLElement | null>(null);
  const tone = LOZENGE[STATUS_TONE[ticket.status]];
  const canMove = ticket.allowedTransitions.length > 0;
  return (
    <>
      <Button
        disabled={busy || !canMove}
        onClick={(e) => setAnchor(e.currentTarget)}
        endIcon={canMove ? <KeyboardArrowDownIcon /> : undefined}
        sx={{
          bgcolor: tone.bg,
          color: tone.color,
          fontWeight: 600,
          '&:hover': { bgcolor: tone.bg, filter: 'brightness(0.95)' },
          '&.Mui-disabled': { bgcolor: tone.bg, color: tone.color },
        }}
      >
        {humanize(ticket.status)}
      </Button>
      <Menu anchorEl={anchor} open={!!anchor} onClose={() => setAnchor(null)}>
        {ticket.allowedTransitions.map((to) => (
          <MenuItem
            key={to}
            onClick={() => {
              setAnchor(null);
              onMove(to);
            }}
            sx={{ gap: 2, justifyContent: 'space-between', minWidth: 240 }}
          >
            {transitionLabel(ticket.status, to)}
            <StatusLabel status={to} />
          </MenuItem>
        ))}
      </Menu>
    </>
  );
}

function DetailRow({ label, children }: { label: string; children: React.ReactNode }) {
  return (
    <Box sx={{ display: 'grid', gridTemplateColumns: '110px 1fr', alignItems: 'center', minHeight: 40, fontSize: 14 }}>
      <Typography sx={{ fontSize: 14, fontWeight: 600, color: colors.muted }}>{label}</Typography>
      <Box sx={{ minWidth: 0 }}>{children}</Box>
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
      <PageHeader
        breadcrumbs={[
          { label: 'Projects', to: '/projects' },
          { label: ticket.projectName, to: `/projects/${ticket.projectId}` },
          { label: ticket.key },
        ]}
        title={
          <Box component="span" sx={{ display: 'inline-flex', alignItems: 'center', gap: 1.25 }}>
            <TypeIcon type={ticket.type} />
            {ticket.title}
          </Box>
        }
        actions={
          <>
            {ticket.permissions.canEdit && <Button onClick={() => setEditing(true)}>Edit</Button>}
            {ticket.permissions.canDelete && (
              <Button color="error" onClick={() => setConfirmDelete(true)}>
                Delete
              </Button>
            )}
          </>
        }
      />

      <ErrorBanner error={transitionState.error ?? assignState.error} />

      <Box sx={{ display: 'grid', gridTemplateColumns: 'minmax(0, 1fr) 340px', gap: 5 }}>
        <Box>
          <Typography variant="h3" sx={{ mb: 1 }}>
            Description
          </Typography>
          <Typography
            sx={{ whiteSpace: 'pre-wrap', fontSize: 14, mb: 4, color: ticket.description ? undefined : 'text.secondary' }}
          >
            {ticket.description || 'Add a description…'}
          </Typography>
          <Timeline ticketId={ticket.id} canComment={ticket.permissions.canComment} />
        </Box>

        <Box>
          <Box sx={{ mb: 2 }}>
            <StatusButton ticket={ticket} busy={transitionState.isLoading} onMove={(to) => transition({ id: ticket.id, status: to })} />
          </Box>
          <Box sx={{ border: 1, borderColor: 'divider', borderRadius: '3px' }}>
            <Typography sx={{ px: 2, py: 1.25, fontWeight: 600, borderBottom: 1, borderColor: 'divider' }}>Details</Typography>
            <Box sx={{ px: 2, py: 1 }}>
              <DetailRow label="Assignee">
                {ticket.permissions.canAssign ? (
                  <UserPicker
                    value={ticket.assignee ?? null}
                    options={memberOptions}
                    disabled={assignState.isLoading}
                    onChange={(user) => assign({ id: ticket.id, assigneeId: user?.id ?? null })}
                  />
                ) : (
                  <UserAvatar name={ticket.assignee?.fullName} withName />
                )}
              </DetailRow>
              <DetailRow label="Reporter">
                <UserAvatar name={ticket.reporter.fullName} withName />
              </DetailRow>
              <DetailRow label="Priority">
                <PriorityIcon priority={ticket.priority} withLabel />
              </DetailRow>
              <DetailRow label="Type">
                <TypeIcon type={ticket.type} withLabel />
              </DetailRow>
              <DetailRow label="Due date">
                {ticket.dueDate ? formatDate(ticket.dueDate) : <Box component="span" sx={{ color: 'text.secondary' }}>None</Box>}
              </DetailRow>
              <DetailRow label="Project">
                {ticket.projectName} <Box component="span" sx={{ color: 'text.secondary' }}>({ticket.key})</Box>
              </DetailRow>
            </Box>
          </Box>
          <Typography variant="body2" color="text.secondary" sx={{ mt: 1.5, lineHeight: 1.8 }}>
            <span title={formatDateTime(ticket.createdAt)}>Created {timeAgo(ticket.createdAt)}</span>
            <br />
            <span title={formatDateTime(ticket.updatedAt)}>Updated {timeAgo(ticket.updatedAt)}</span>
            {ticket.closedAt && (
              <>
                <br />
                <span title={formatDateTime(ticket.closedAt)}>Closed {timeAgo(ticket.closedAt)}</span>
              </>
            )}
          </Typography>
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
