import SearchIcon from '@mui/icons-material/Search';
import Alert from '@mui/material/Alert';
import Box from '@mui/material/Box';
import Button from '@mui/material/Button';
import ButtonBase from '@mui/material/ButtonBase';
import InputAdornment from '@mui/material/InputAdornment';
import MenuItem from '@mui/material/MenuItem';
import Snackbar from '@mui/material/Snackbar';
import TextField from '@mui/material/TextField';
import Typography from '@mui/material/Typography';
import { useEffect, useMemo, useState } from 'react';
import { useNavigate, useSearchParams } from 'react-router-dom';
import { useGetProjectsQuery, useGetTicketsQuery, useTransitionTicketMutation } from '../api/api';
import { errorMessage } from '../api/http';
import { TICKET_STATUSES, type TicketStatus, type TicketSummary } from '../api/types';
import { useCurrentUser } from '../app/hooks';
import { ErrorBanner, Loading } from '../components/Feedback';
import { PriorityIcon, TypeIcon } from '../components/Labels';
import PageHeader from '../components/PageHeader';
import UserAvatar from '../components/UserAvatar';
import { colors } from '../theme';
import { humanize } from '../utils/format';

const BOARD_SIZE = 100;

function Card({ ticket, onOpen, onDragStart }: {
  ticket: TicketSummary;
  onOpen: () => void;
  onDragStart: (e: React.DragEvent) => void;
}) {
  const done = ticket.status === 'CLOSED';
  return (
    <ButtonBase
      component="div"
      draggable
      onDragStart={onDragStart}
      onClick={onOpen}
      sx={{
        display: 'block',
        width: '100%',
        textAlign: 'left',
        bgcolor: '#fff',
        borderRadius: '3px',
        boxShadow: '0 1px 1px rgba(9,30,66,0.25), 0 0 1px rgba(9,30,66,0.31)',
        p: 1.5,
        mb: 1,
        cursor: 'grab',
        userSelect: 'none',
        '&:hover': { bgcolor: colors.subtle },
        '&:active': { cursor: 'grabbing' },
      }}
    >
      <Typography sx={{ fontSize: 14, mb: 1.5, color: colors.text, wordBreak: 'break-word' }}>{ticket.title}</Typography>
      <Box sx={{ display: 'flex', alignItems: 'center', gap: 0.75 }}>
        <TypeIcon type={ticket.type} />
        <Typography
          sx={{
            fontSize: 12,
            fontWeight: 600,
            color: colors.muted,
            textDecoration: done ? 'line-through' : undefined,
          }}
        >
          {ticket.key}
        </Typography>
        <Box sx={{ ml: 'auto', display: 'flex', alignItems: 'center', gap: 0.5 }}>
          <PriorityIcon priority={ticket.priority} />
          <UserAvatar name={ticket.assignee?.fullName} size={24} />
        </Box>
      </Box>
    </ButtonBase>
  );
}

/** Kanban board: one column per status; drag a card to move it through the workflow. */
export default function BoardPage() {
  const user = useCurrentUser();
  const navigate = useNavigate();
  const [params, setParams] = useSearchParams();
  const { data: projects, isLoading: projectsLoading } = useGetProjectsQuery({});
  const projectId = Number(params.get('projectId')) || projects?.content[0]?.id;
  const project = projects?.content.find((p) => p.id === projectId);

  const [text, setText] = useState('');
  const [onlyMine, setOnlyMine] = useState(false);
  const [assigneeFilter, setAssigneeFilter] = useState<number | null>(null);
  const [dragging, setDragging] = useState<TicketSummary | null>(null);
  const [overColumn, setOverColumn] = useState<TicketStatus | null>(null);
  const [toast, setToast] = useState<string | null>(null);

  const { data, error, isFetching } = useGetTicketsQuery(
    { projectId, size: BOARD_SIZE, sort: 'priority,desc' },
    { skip: !projectId },
  );
  const [transition] = useTransitionTicketMutation();

  useEffect(() => {
    setAssigneeFilter(null);
  }, [projectId]);

  const assignees = useMemo(() => {
    const seen = new Map<number, string>();
    data?.content.forEach((t) => t.assignee && seen.set(t.assignee.id, t.assignee.fullName));
    return [...seen.entries()].map(([id, fullName]) => ({ id, fullName }));
  }, [data]);

  const visible = (data?.content ?? []).filter((t) => {
    if (onlyMine && t.assignee?.id !== user?.id) return false;
    if (assigneeFilter !== null && t.assignee?.id !== assigneeFilter) return false;
    if (text && !`${t.key} ${t.title}`.toLowerCase().includes(text.toLowerCase())) return false;
    return true;
  });

  async function drop(target: TicketStatus) {
    const ticket = dragging;
    setDragging(null);
    setOverColumn(null);
    if (!ticket || ticket.status === target) return;
    try {
      await transition({ id: ticket.id, status: target }).unwrap();
    } catch (e) {
      setToast(`${ticket.key}: ${errorMessage(e, 'Move not allowed')}`);
    }
  }

  if (projectsLoading) return <Loading />;
  if (!projects?.content.length) {
    return (
      <>
        <PageHeader title="Board" />
        <Typography color="text.secondary">You are not a member of any project yet.</Typography>
      </>
    );
  }

  return (
    <>
      <PageHeader
        breadcrumbs={[{ label: 'Projects', to: '/projects' }, { label: project?.name ?? '', to: `/projects/${projectId}` }]}
        title={`${project?.key ?? ''} board`}
        actions={
          <TextField
            select
            label="Project"
            value={projectId ?? ''}
            onChange={(e) => setParams({ projectId: String(e.target.value) }, { replace: true })}
            sx={{ minWidth: 220 }}
          >
            {projects.content.map((p) => (
              <MenuItem key={p.id} value={p.id}>
                {p.key} – {p.name}
              </MenuItem>
            ))}
          </TextField>
        }
      />

      <Box sx={{ display: 'flex', alignItems: 'center', gap: 1.5, mb: 2, flexWrap: 'wrap' }}>
        <TextField
          placeholder="Search board"
          value={text}
          onChange={(e) => setText(e.target.value)}
          sx={{ width: 200 }}
          slotProps={{
            input: { startAdornment: <InputAdornment position="start"><SearchIcon sx={{ fontSize: 18 }} /></InputAdornment> },
          }}
        />
        <Box sx={{ display: 'flex', pl: 0.5 }}>
          {assignees.map((a) => (
            <ButtonBase
              key={a.id}
              onClick={() => setAssigneeFilter(assigneeFilter === a.id ? null : a.id)}
              sx={{
                ml: -0.5,
                borderRadius: '50%',
                border: 2,
                borderColor: assigneeFilter === a.id ? colors.blue : '#fff',
                '&:hover': { zIndex: 1, transform: 'translateY(-2px)' },
                transition: 'transform 0.1s',
              }}
            >
              <UserAvatar name={a.fullName} size={32} />
            </ButtonBase>
          ))}
        </Box>
        <Button
          onClick={() => setOnlyMine(!onlyMine)}
          sx={onlyMine ? { bgcolor: colors.blueBg, color: colors.blue, '&:hover': { bgcolor: '#CCE0FF' } } : undefined}
        >
          Only my issues
        </Button>
        {(text || onlyMine || assigneeFilter !== null) && (
          <Button
            variant="text"
            sx={{ bgcolor: 'transparent' }}
            onClick={() => {
              setText('');
              setOnlyMine(false);
              setAssigneeFilter(null);
            }}
          >
            Clear filters
          </Button>
        )}
      </Box>

      <ErrorBanner error={error} />
      {data && data.totalElements > BOARD_SIZE && (
        <Alert severity="info" variant="outlined" sx={{ mb: 2 }}>
          Showing the {BOARD_SIZE} highest-priority of {data.totalElements} tickets. Use the Tickets list for the rest.
        </Alert>
      )}

      <Box sx={{ display: 'grid', gridTemplateColumns: 'repeat(4, minmax(220px, 1fr))', gap: 1.5, opacity: isFetching ? 0.7 : 1 }}>
        {TICKET_STATUSES.map((status) => {
          const cards = visible.filter((t) => t.status === status);
          const canDrop = !!dragging && dragging.status !== status;
          return (
            <Box
              key={status}
              onDragOver={(e) => {
                if (!canDrop) return;
                e.preventDefault();
                setOverColumn(status);
              }}
              onDragLeave={() => setOverColumn((c) => (c === status ? null : c))}
              onDrop={(e) => {
                e.preventDefault();
                drop(status);
              }}
              sx={{
                bgcolor: overColumn === status ? colors.blueBg : colors.column,
                outline: overColumn === status ? `2px dashed ${colors.blue}` : 'none',
                borderRadius: '6px',
                p: 1,
                userSelect: 'none',
                minHeight: 'calc(100vh - 290px)',
              }}
            >
              <Typography
                sx={{ fontSize: 12, fontWeight: 600, color: colors.muted, textTransform: 'uppercase', px: 0.75, pt: 0.5, pb: 1.25 }}
              >
                {humanize(status)} <Box component="span" sx={{ fontWeight: 400 }}>{cards.length}</Box>
              </Typography>
              {cards.map((t) => (
                <Card
                  key={t.id}
                  ticket={t}
                  onOpen={() => navigate(`/tickets/${t.id}`)}
                  onDragStart={(e) => {
                    e.dataTransfer.effectAllowed = 'move';
                    e.dataTransfer.setData('text/plain', String(t.id));
                    setDragging(t);
                  }}
                />
              ))}
            </Box>
          );
        })}
      </Box>

      <Snackbar
        open={!!toast}
        autoHideDuration={5000}
        onClose={() => setToast(null)}
        anchorOrigin={{ vertical: 'bottom', horizontal: 'left' }}
      >
        <Alert severity="error" variant="filled" onClose={() => setToast(null)} sx={{ fontSize: 14 }}>
          {toast}
        </Alert>
      </Snackbar>
    </>
  );
}
