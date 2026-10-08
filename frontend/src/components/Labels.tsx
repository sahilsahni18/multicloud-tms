import BookmarkIcon from '@mui/icons-material/Bookmark';
import CheckIcon from '@mui/icons-material/Check';
import FiberManualRecordIcon from '@mui/icons-material/FiberManualRecord';
import KeyboardArrowDownIcon from '@mui/icons-material/KeyboardArrowDown';
import KeyboardArrowUpIcon from '@mui/icons-material/KeyboardArrowUp';
import KeyboardDoubleArrowUpIcon from '@mui/icons-material/KeyboardDoubleArrowUp';
import DragHandleIcon from '@mui/icons-material/DragHandle';
import Box from '@mui/material/Box';
import Tooltip from '@mui/material/Tooltip';
import type { DeploymentStatus, RoleName, TicketPriority, TicketStatus, TicketType } from '../api/types';
import { humanize } from '../utils/format';
import { LOZENGE, STATUS_TONE, type LozengeTone } from './tones';

// ---- Lozenges ---------------------------------------------------------------

/** Small uppercase status pill, as on Jira issues. */
export function Lozenge({ tone, children }: { tone: LozengeTone; children: React.ReactNode }) {
  const t = LOZENGE[tone];
  return (
    <Box
      component="span"
      sx={{
        display: 'inline-block',
        px: '4px',
        lineHeight: '16px',
        fontSize: 11,
        fontWeight: 700,
        textTransform: 'uppercase',
        borderRadius: '3px',
        color: t.color,
        bgcolor: t.bg,
        whiteSpace: 'nowrap',
        verticalAlign: 'middle',
      }}
    >
      {children}
    </Box>
  );
}

export function StatusLabel({ status }: { status: TicketStatus }) {
  return <Lozenge tone={STATUS_TONE[status]}>{humanize(status)}</Lozenge>;
}

export function RoleLabel({ role }: { role: RoleName }) {
  return <Lozenge tone={role === 'ADMIN' ? 'red' : role === 'PROJECT_MANAGER' ? 'blue' : 'grey'}>{humanize(role)}</Lozenge>;
}

export function DeploymentStatusLabel({ status }: { status: DeploymentStatus }) {
  const tone: LozengeTone =
    status === 'COMPLETED' || status === 'PLANNED'
      ? 'green'
      : status === 'FAILED'
        ? 'red'
        : status === 'DESTROYED'
          ? 'grey'
          : status === 'DESTROYING'
            ? 'orange'
            : 'blue';
  return <Lozenge tone={tone}>{humanize(status)}</Lozenge>;
}

// ---- Issue type icons ---------------------------------------------------------

const TYPE_STYLE: Record<TicketType, { bg: string; icon: React.ReactNode }> = {
  BUG: { bg: '#E5493A', icon: <FiberManualRecordIcon sx={{ fontSize: 8 }} /> },
  TASK: { bg: '#4BADE8', icon: <CheckIcon sx={{ fontSize: 12 }} /> },
  STORY: { bg: '#63BA3C', icon: <BookmarkIcon sx={{ fontSize: 10 }} /> },
};

/** 16px coloured square: red bug, blue task, green story. */
export function TypeIcon({ type, withLabel }: { type: TicketType; withLabel?: boolean }) {
  const icon = (
    <Box
      component="span"
      sx={{
        width: 16,
        height: 16,
        borderRadius: '3px',
        bgcolor: TYPE_STYLE[type].bg,
        color: '#fff',
        display: 'inline-flex',
        alignItems: 'center',
        justifyContent: 'center',
        flexShrink: 0,
        verticalAlign: 'middle',
      }}
    >
      {TYPE_STYLE[type].icon}
    </Box>
  );
  if (!withLabel) {
    return <Tooltip title={humanize(type)}>{icon}</Tooltip>;
  }
  return (
    <Box component="span" sx={{ display: 'inline-flex', alignItems: 'center', gap: 0.75 }}>
      {icon}
      {humanize(type)}
    </Box>
  );
}

// ---- Priority icons -------------------------------------------------------------

const PRIORITY_STYLE: Record<TicketPriority, { color: string; Icon: typeof KeyboardArrowUpIcon }> = {
  CRITICAL: { color: '#C9372C', Icon: KeyboardDoubleArrowUpIcon },
  HIGH: { color: '#E2483D', Icon: KeyboardArrowUpIcon },
  MEDIUM: { color: '#E2B203', Icon: DragHandleIcon },
  LOW: { color: '#0C66E4', Icon: KeyboardArrowDownIcon },
};

export function PriorityIcon({ priority, withLabel }: { priority: TicketPriority; withLabel?: boolean }) {
  const { color, Icon } = PRIORITY_STYLE[priority];
  const icon = <Icon sx={{ fontSize: 18, color, verticalAlign: 'middle' }} />;
  if (!withLabel) {
    return (
      <Tooltip title={`${humanize(priority)} priority`}>
        <Box component="span" sx={{ display: 'inline-flex' }}>
          {icon}
        </Box>
      </Tooltip>
    );
  }
  return (
    <Box component="span" sx={{ display: 'inline-flex', alignItems: 'center', gap: 0.5 }}>
      {icon}
      {humanize(priority)}
    </Box>
  );
}
