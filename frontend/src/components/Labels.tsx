import Box from '@mui/material/Box';
import type { DeploymentStatus, RoleName, TicketPriority, TicketStatus, TicketType } from '../api/types';
import { humanize } from '../utils/format';

interface Tone {
  color: string;
  bg: string;
}

const TONES: Record<string, Tone> = {
  grey: { color: '#59636e', bg: '#eff2f5' },
  blue: { color: '#0b5cad', bg: '#ddf4ff' },
  yellow: { color: '#7d4e00', bg: '#fff8c5' },
  green: { color: '#1a7f37', bg: '#dafbe1' },
  red: { color: '#cf222e', bg: '#ffebe9' },
  orange: { color: '#9a3c00', bg: '#fff1e5' },
};

function Label({ tone, children }: { tone: keyof typeof TONES; children: React.ReactNode }) {
  const t = TONES[tone];
  return (
    <Box
      component="span"
      sx={{
        display: 'inline-block',
        px: 0.75,
        lineHeight: '20px',
        fontSize: 12,
        fontWeight: 500,
        borderRadius: '3px',
        color: t.color,
        bgcolor: t.bg,
        whiteSpace: 'nowrap',
      }}
    >
      {children}
    </Box>
  );
}

const STATUS_TONE: Record<TicketStatus, keyof typeof TONES> = {
  OPEN: 'grey',
  IN_PROGRESS: 'blue',
  IN_REVIEW: 'yellow',
  CLOSED: 'green',
};

export function StatusLabel({ status }: { status: TicketStatus }) {
  return <Label tone={STATUS_TONE[status]}>{humanize(status)}</Label>;
}

const PRIORITY_COLOR: Record<TicketPriority, string> = {
  LOW: '#59636e',
  MEDIUM: '#1f2328',
  HIGH: '#9a3c00',
  CRITICAL: '#cf222e',
};

/** Priority is plain coloured text; only CRITICAL is bold. */
export function PriorityText({ priority }: { priority: TicketPriority }) {
  return (
    <Box component="span" sx={{ color: PRIORITY_COLOR[priority], fontWeight: priority === 'CRITICAL' ? 600 : 400 }}>
      {humanize(priority)}
    </Box>
  );
}

export function TypeText({ type }: { type: TicketType }) {
  return (
    <Box component="span" sx={{ color: type === 'BUG' ? '#cf222e' : 'text.secondary' }}>
      {humanize(type)}
    </Box>
  );
}

export function RoleLabel({ role }: { role: RoleName }) {
  return <Label tone={role === 'ADMIN' ? 'red' : role === 'PROJECT_MANAGER' ? 'blue' : 'grey'}>{humanize(role)}</Label>;
}

export function DeploymentStatusLabel({ status }: { status: DeploymentStatus }) {
  const tone =
    status === 'COMPLETED' || status === 'PLANNED'
      ? 'green'
      : status === 'FAILED'
        ? 'red'
        : status === 'DESTROYED'
          ? 'grey'
          : status === 'DESTROYING'
            ? 'orange'
            : 'blue';
  return <Label tone={tone}>{humanize(status)}</Label>;
}
