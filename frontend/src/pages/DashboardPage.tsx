import Box from '@mui/material/Box';
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
import { useGetDashboardQuery, useGetProjectsQuery } from '../api/api';
import { TICKET_PRIORITIES, TICKET_STATUSES, type Dashboard } from '../api/types';
import { useCurrentUser } from '../app/hooks';
import { ErrorBanner, Loading } from '../components/Feedback';
import PageHeader from '../components/PageHeader';
import { colors } from '../theme';
import { humanize, timeAgo } from '../utils/format';

const SCOPE_TEXT: Record<Dashboard['scope'], string> = {
  GLOBAL: 'All projects',
  PROJECTS: 'Projects you manage or belong to',
  PERSONAL: '',
};

function Stat({ label, value, to }: { label: string; value: number; to?: string }) {
  const content = (
    <>
      <Typography sx={{ fontSize: 22, fontWeight: 600, lineHeight: 1.2 }}>{value}</Typography>
      <Typography variant="body2" color="text.secondary">
        {label}
      </Typography>
    </>
  );
  return (
    <Box sx={{ bgcolor: 'background.paper', px: 2, py: 1.25 }}>
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

function Bars({ title, rows, onSelect }: {
  title: string;
  rows: { key: string; label: string; value: number }[];
  onSelect: (key: string) => void;
}) {
  const max = Math.max(1, ...rows.map((r) => r.value));
  return (
    <Paper sx={{ p: 2, flex: 1, minWidth: 280 }}>
      <Typography variant="h3" sx={{ mb: 1.5 }}>
        {title}
      </Typography>
      {rows.map((row) => (
        <Box
          key={row.key}
          onClick={() => onSelect(row.key)}
          sx={{ display: 'grid', gridTemplateColumns: '96px 1fr 32px', alignItems: 'center', gap: 1, py: 0.5, cursor: 'pointer', '&:hover': { color: colors.blue } }}
        >
          <Typography variant="body2">{row.label}</Typography>
          <Box sx={{ height: 8, bgcolor: colors.subtle, borderRadius: 1 }}>
            <Box sx={{ height: 8, width: `${(row.value / max) * 100}%`, bgcolor: colors.blue, borderRadius: 1, opacity: 0.8 }} />
          </Box>
          <Typography variant="body2" sx={{ textAlign: 'right' }}>
            {row.value}
          </Typography>
        </Box>
      ))}
    </Paper>
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

  return (
    <>
      <PageHeader
        title="Dashboard"
        description={data && (data.scope === 'PERSONAL' ? personalText : SCOPE_TEXT[data.scope])}
        actions={
          <TextField
            select
            label="Project"
            value={projectId}
            onChange={(e) => setProjectId(e.target.value === '' ? '' : Number(e.target.value))}
            sx={{ minWidth: 200 }}
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
          {/* 1px gaps over a grey background draw the grid lines, however the tiles wrap. */}
          <Paper
            sx={{
              display: 'grid',
              gridTemplateColumns: 'repeat(auto-fill, minmax(140px, 1fr))',
              gap: '1px',
              bgcolor: 'divider',
              overflow: 'hidden',
              mb: 2,
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

          <Box sx={{ display: 'flex', gap: 2, flexWrap: 'wrap', mb: 2 }}>
            <Bars
              title="By status"
              rows={TICKET_STATUSES.map((s) => ({ key: s, label: humanize(s), value: data.byStatus[s] ?? 0 }))}
              onSelect={(s) => navigate(`/tickets?status=${s}${projectParam}`)}
            />
            <Bars
              title="By priority"
              rows={[...TICKET_PRIORITIES].reverse().map((p) => ({ key: p, label: humanize(p), value: data.byPriority[p] ?? 0 }))}
              onSelect={(p) => navigate(`/tickets?priority=${p}${projectParam}`)}
            />
          </Box>

          {data.productivity.length > 0 && (
            <Paper sx={{ mb: 2 }}>
              <Typography variant="h3" sx={{ px: 2, pt: 1.5, pb: 1 }}>
                Team (last 4 weeks)
              </Typography>
              <Table size="small">
                <TableHead>
                  <TableRow>
                    <TableCell>Person</TableCell>
                    <TableCell align="right">Open now</TableCell>
                    <TableCell align="right">Closed</TableCell>
                    <TableCell>Closed per week</TableCell>
                    <TableCell align="right">Avg. time to close</TableCell>
                  </TableRow>
                </TableHead>
                <TableBody>
                  {data.productivity.map((row) => (
                    <TableRow key={row.userId}>
                      <TableCell>{row.fullName}</TableCell>
                      <TableCell align="right">{row.openAssigned}</TableCell>
                      <TableCell align="right">{row.closedInPeriod}</TableCell>
                      <TableCell className="mono" title={row.closedPerWeek.map((w) => `${w.weekStart}: ${w.closed}`).join('\n')}>
                        {row.closedPerWeek.map((w) => w.closed).join('  ')}
                      </TableCell>
                      <TableCell align="right">{row.avgHoursToClose == null ? '–' : formatHours(row.avgHoursToClose)}</TableCell>
                    </TableRow>
                  ))}
                </TableBody>
              </Table>
            </Paper>
          )}

          <Paper>
            <Typography variant="h3" sx={{ px: 2, pt: 1.5, pb: 1 }}>
              Recent activity
            </Typography>
            {data.recentActivity.length === 0 && (
              <Typography variant="body2" color="text.secondary" sx={{ px: 2, pb: 2 }}>
                Nothing yet.
              </Typography>
            )}
            {data.recentActivity.map((a) => (
              <Box
                key={a.id}
                sx={{ display: 'flex', gap: 1, px: 2, py: 0.75, borderTop: 1, borderColor: 'divider', fontSize: 14 }}
              >
                <Box sx={{ fontWeight: 500, whiteSpace: 'nowrap' }}>{a.actor?.fullName ?? 'System'}</Box>
                <Box sx={{ flex: 1, minWidth: 0, overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>
                  {a.entityType === 'TICKET' ? (
                    <RouterLink to={`/tickets/${a.entityId}`}>{a.summary}</RouterLink>
                  ) : (
                    a.summary
                  )}
                </Box>
                <Box sx={{ color: 'text.secondary', whiteSpace: 'nowrap' }} title={a.createdAt}>
                  {timeAgo(a.createdAt)}
                </Box>
              </Box>
            ))}
          </Paper>
        </>
      )}
    </>
  );
}

function formatHours(hours: number): string {
  return hours < 48 ? `${hours} h` : `${Math.round(hours / 24)} d`;
}
