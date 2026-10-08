import Box from '@mui/material/Box';
import Link from '@mui/material/Link';
import MenuItem from '@mui/material/MenuItem';
import Paper from '@mui/material/Paper';
import Table from '@mui/material/Table';
import TableBody from '@mui/material/TableBody';
import TableCell from '@mui/material/TableCell';
import TableHead from '@mui/material/TableHead';
import TableRow from '@mui/material/TableRow';
import TextField from '@mui/material/TextField';
import Typography from '@mui/material/Typography';
import { useState } from 'react';
import { Link as RouterLink, useNavigate } from 'react-router-dom';
import { useGetDashboardQuery, useGetProjectsQuery, useGetTicketsQuery } from '../api/api';
import { TICKET_PRIORITIES, TICKET_STATUSES, type Dashboard, type TicketPriority } from '../api/types';
import { useCurrentUser } from '../app/hooks';
import { ErrorBanner, Loading } from '../components/Feedback';
import { PriorityIcon, StatusLabel, TypeIcon } from '../components/Labels';
import { LOZENGE, STATUS_TONE } from '../components/tones';
import PageHeader from '../components/PageHeader';
import UserAvatar from '../components/UserAvatar';
import { colors } from '../theme';
import { timeAgo } from '../utils/format';

const SCOPE_TEXT: Record<Dashboard['scope'], string> = {
  GLOBAL: 'All projects',
  PROJECTS: 'Projects you manage or belong to',
  PERSONAL: '',
};

const PRIORITY_BAR: Record<TicketPriority, string> = {
  CRITICAL: '#C9372C',
  HIGH: '#E2483D',
  MEDIUM: '#E2B203',
  LOW: '#0C66E4',
};

/** A dashboard panel ("gadget") with a title bar. */
function Gadget({ title, action, children }: { title: string; action?: React.ReactNode; children: React.ReactNode }) {
  return (
    <Paper sx={{ mb: 2, overflow: 'hidden' }}>
      <Box sx={{ display: 'flex', alignItems: 'center', px: 2, py: 1.25, borderBottom: 1, borderColor: 'divider' }}>
        <Typography sx={{ fontWeight: 600, flex: 1 }}>{title}</Typography>
        {action}
      </Box>
      {children}
    </Paper>
  );
}

function Stat({ label, value, to }: { label: string; value: number; to?: string }) {
  const content = (
    <>
      <Typography sx={{ fontSize: 24, fontWeight: 500, lineHeight: 1.2 }}>{value}</Typography>
      <Typography variant="body2" color="text.secondary">
        {label}
      </Typography>
    </>
  );
  return (
    <Box sx={{ px: 2, py: 1.5, boxShadow: '1px 0 0 #DCDFE4, 0 1px 0 #DCDFE4' }}>
      {to ? (
        <Box component={RouterLink} to={to} sx={{ color: 'inherit', textDecoration: 'none', '&:hover p:first-of-type': { color: colors.blue } }}>
          {content}
        </Box>
      ) : (
        content
      )}
    </Box>
  );
}

function Bars({ rows, onSelect }: {
  rows: { key: string; label: React.ReactNode; value: number; color: string }[];
  onSelect: (key: string) => void;
}) {
  const total = Math.max(1, rows.reduce((sum, r) => sum + r.value, 0));
  return (
    <Box sx={{ p: 2 }}>
      {rows.map((row) => (
        <Box
          key={row.key}
          onClick={() => onSelect(row.key)}
          sx={{
            display: 'grid',
            gridTemplateColumns: '120px 1fr 64px',
            alignItems: 'center',
            gap: 1.5,
            py: 0.75,
            cursor: 'pointer',
            borderRadius: '3px',
            '&:hover': { bgcolor: colors.hover },
          }}
        >
          <Box>{row.label}</Box>
          <Box sx={{ height: 8, bgcolor: colors.column, borderRadius: 4 }}>
            <Box sx={{ height: 8, width: `${(row.value / total) * 100}%`, bgcolor: row.color, borderRadius: 4 }} />
          </Box>
          <Typography variant="body2" sx={{ textAlign: 'right' }}>
            {row.value} <Box component="span" sx={{ color: 'text.secondary' }}>({Math.round((row.value / total) * 100)}%)</Box>
          </Typography>
        </Box>
      ))}
    </Box>
  );
}

function AssignedToMe({ projectId }: { projectId?: number }) {
  const user = useCurrentUser();
  const { data } = useGetTicketsQuery({
    assigneeId: user?.id,
    projectId,
    status: ['OPEN', 'IN_PROGRESS', 'IN_REVIEW'],
    size: 8,
    sort: 'priority,desc',
  });
  return (
    <Gadget
      title="Assigned to me"
      action={
        <Link component={RouterLink} to="/tickets?who=assigned&status=OPEN&status=IN_PROGRESS&status=IN_REVIEW" variant="body2">
          View all
        </Link>
      }
    >
      {data?.content.length === 0 && (
        <Typography variant="body2" color="text.secondary" sx={{ px: 2, py: 2 }}>
          Nothing assigned to you right now.
        </Typography>
      )}
      {data?.content.map((t) => (
        <Box
          key={t.id}
          component={RouterLink}
          to={`/tickets/${t.id}`}
          sx={{
            display: 'flex',
            alignItems: 'center',
            gap: 1.25,
            px: 2,
            py: 1,
            color: 'inherit',
            textDecoration: 'none',
            borderBottom: 1,
            borderColor: 'divider',
            '&:last-of-type': { borderBottom: 0 },
            '&:hover': { bgcolor: colors.hover },
          }}
        >
          <TypeIcon type={t.type} />
          <Box sx={{ color: colors.muted, fontSize: 13, fontWeight: 500, width: 64, flexShrink: 0 }}>{t.key}</Box>
          <Box sx={{ flex: 1, minWidth: 0, overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>{t.title}</Box>
          <PriorityIcon priority={t.priority} />
          <StatusLabel status={t.status} />
        </Box>
      ))}
    </Gadget>
  );
}

export default function DashboardPage() {
  const user = useCurrentUser();
  const navigate = useNavigate();
  const [projectId, setProjectId] = useState<number | ''>('');
  const { data: projects } = useGetProjectsQuery({});
  const { data, error, isLoading } = useGetDashboardQuery({ projectId: projectId || undefined });

  const personalText = user?.roles.includes('DEVELOPER') ? 'Tickets assigned to you' : 'Tickets you reported';
  const projectParam = projectId ? `&projectId=${projectId}` : '';
  const firstName = user?.fullName.split(' ')[0];

  return (
    <>
      <PageHeader
        title={`Welcome back, ${firstName}`}
        description={data && (data.scope === 'PERSONAL' ? personalText : SCOPE_TEXT[data.scope])}
        actions={
          <TextField
            select
            label="Project"
            value={projectId}
            onChange={(e) => setProjectId(e.target.value === '' ? '' : Number(e.target.value))}
            sx={{ minWidth: 220 }}
          >
            <MenuItem value="">All projects</MenuItem>
            {projects?.content.map((p) => (
              <MenuItem key={p.id} value={p.id}>
                {p.key} – {p.name}
              </MenuItem>
            ))}
          </TextField>
        }
      />
      <ErrorBanner error={error} />
      {isLoading && <Loading />}
      {data && (
        <>
          {/* Each tile draws its right and bottom edge; the outer ones are clipped by the border. */}
          <Paper
            sx={{
              display: 'grid',
              gridTemplateColumns: 'repeat(auto-fill, minmax(150px, 1fr))',
              overflow: 'hidden',
              mb: 3,
            }}
          >
            <Stat label="Total tickets" value={data.totals.total} to={`/tickets?${projectParam.slice(1)}`} />
            <Stat label="Open" value={data.totals.open} to={`/tickets?status=OPEN&status=IN_PROGRESS&status=IN_REVIEW${projectParam}`} />
            <Stat label="Closed" value={data.totals.closed} to={`/tickets?status=CLOSED${projectParam}`} />
            <Stat label="Unassigned" value={data.totals.unassigned} to={`/tickets?who=unassigned&status=OPEN&status=IN_PROGRESS&status=IN_REVIEW${projectParam}`} />
            <Stat label="Overdue" value={data.totals.overdue} />
            <Stat label="Created (7 days)" value={data.totals.createdLast7Days} />
            <Stat label="Closed (7 days)" value={data.totals.closedLast7Days} />
          </Paper>

          <Box sx={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(420px, 1fr))', gap: 2, alignItems: 'start' }}>
            <Box>
              <AssignedToMe projectId={projectId || undefined} />

              <Gadget title="Activity stream">
                {data.recentActivity.length === 0 && (
                  <Typography variant="body2" color="text.secondary" sx={{ px: 2, py: 2 }}>
                    Nothing yet.
                  </Typography>
                )}
                {data.recentActivity.map((a) => (
                  <Box key={a.id} sx={{ display: 'flex', gap: 1.25, px: 2, py: 1, borderBottom: 1, borderColor: 'divider', '&:last-of-type': { borderBottom: 0 } }}>
                    <UserAvatar name={a.actor?.fullName ?? 'System'} size={24} />
                    <Box sx={{ flex: 1, minWidth: 0, fontSize: 14 }}>
                      <Box component="span" sx={{ fontWeight: 600 }}>
                        {a.actor?.fullName ?? 'System'}
                      </Box>{' '}
                      {a.entityType === 'TICKET' ? (
                        <Link component={RouterLink} to={`/tickets/${a.entityId}`}>
                          {a.summary}
                        </Link>
                      ) : (
                        a.summary
                      )}
                      <Typography variant="body2" color="text.secondary" title={a.createdAt}>
                        {timeAgo(a.createdAt)}
                      </Typography>
                    </Box>
                  </Box>
                ))}
              </Gadget>
            </Box>

            <Box>
              <Gadget title="Tickets by status">
                <Bars
                  rows={TICKET_STATUSES.map((s) => ({
                    key: s,
                    label: <StatusLabel status={s} />,
                    value: data.byStatus[s] ?? 0,
                    color: LOZENGE[STATUS_TONE[s]].color,
                  }))}
                  onSelect={(s) => navigate(`/tickets?status=${s}${projectParam}`)}
                />
              </Gadget>
              <Gadget title="Tickets by priority">
                <Bars
                  rows={[...TICKET_PRIORITIES].reverse().map((p) => ({
                    key: p,
                    label: <PriorityIcon priority={p} withLabel />,
                    value: data.byPriority[p] ?? 0,
                    color: PRIORITY_BAR[p],
                  }))}
                  onSelect={(p) => navigate(`/tickets?priority=${p}${projectParam}`)}
                />
              </Gadget>

              {data.productivity.length > 0 && (
                <Gadget title="Team workload (last 4 weeks)">
                  <Table size="small">
                    <TableHead>
                      <TableRow>
                        <TableCell>Assignee</TableCell>
                        <TableCell align="right">Open</TableCell>
                        <TableCell align="right">Closed</TableCell>
                        <TableCell align="right">Avg. to close</TableCell>
                      </TableRow>
                    </TableHead>
                    <TableBody>
                      {data.productivity.map((row) => (
                        <TableRow key={row.userId}>
                          <TableCell>
                            <UserAvatar name={row.fullName} withName />
                          </TableCell>
                          <TableCell align="right">{row.openAssigned}</TableCell>
                          <TableCell align="right" title={row.closedPerWeek.map((w) => `${w.weekStart}: ${w.closed}`).join('\n')}>
                            {row.closedInPeriod}
                          </TableCell>
                          <TableCell align="right">{row.avgHoursToClose == null ? '–' : formatHours(row.avgHoursToClose)}</TableCell>
                        </TableRow>
                      ))}
                    </TableBody>
                  </Table>
                </Gadget>
              )}
            </Box>
          </Box>
        </>
      )}
    </>
  );
}

function formatHours(hours: number): string {
  return hours < 48 ? `${hours} h` : `${Math.round(hours / 24)} d`;
}
