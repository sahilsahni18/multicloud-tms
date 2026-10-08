import Box from '@mui/material/Box';
import Button from '@mui/material/Button';
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
import { useGetDashboardQuery, useGetProjectsQuery, useGetTicketsQuery } from '../api/api';
import { download, errorMessage } from '../api/http';
import { TICKET_PRIORITIES, TICKET_STATUSES, type TicketPriority, type TicketStatus } from '../api/types';
import { ErrorBanner, TableMessage } from '../components/Feedback';
import { PriorityText, StatusLabel } from '../components/Labels';
import PageHeader from '../components/PageHeader';
import { formatDate, humanize } from '../utils/format';

export default function ReportsPage() {
  const [projectId, setProjectId] = useState<number | ''>('');
  const [status, setStatus] = useState<TicketStatus[]>([]);
  const [priority, setPriority] = useState<TicketPriority[]>([]);
  const [exportError, setExportError] = useState<string | null>(null);
  const filters = {
    projectId: projectId || undefined,
    status: status.length ? status : undefined,
    priority: priority.length ? priority : undefined,
  };
  const { data: projects } = useGetProjectsQuery({});
  const { data: preview, error } = useGetTicketsQuery({ ...filters, size: 10, sort: 'priority,desc' });
  const { data: dashboard } = useGetDashboardQuery({ projectId: projectId || undefined, weeks: 8 });

  async function exportCsv() {
    setExportError(null);
    try {
      await download('/tickets/export', { ...filters, sort: 'priority,desc' }, 'tickets.csv');
    } catch (e) {
      setExportError(errorMessage(e, 'Export failed'));
    }
  }

  return (
    <>
      <PageHeader title="Reports" description="Filter tickets and download them as CSV (up to 5000 rows)." />
      <Box sx={{ display: 'flex', gap: 1, mb: 2, alignItems: 'center', flexWrap: 'wrap' }}>
        <TextField
          select
          label="Project"
          value={projectId}
          onChange={(e) => setProjectId(e.target.value === '' ? '' : Number(e.target.value))}
          sx={{ width: 200 }}
        >
          <MenuItem value="">All projects</MenuItem>
          {projects?.content.map((p) => (
            <MenuItem key={p.id} value={p.id}>
              {p.key} – {p.name}
            </MenuItem>
          ))}
        </TextField>
        <TextField
          select
          label="Status"
          value={status}
          onChange={(e) => setStatus(e.target.value as unknown as TicketStatus[])}
          sx={{ width: 180 }}
          slotProps={{ select: { multiple: true, renderValue: (v) => (v as string[]).map(humanize).join(', ') } }}
        >
          {TICKET_STATUSES.map((s) => (
            <MenuItem key={s} value={s}>
              {humanize(s)}
            </MenuItem>
          ))}
        </TextField>
        <TextField
          select
          label="Priority"
          value={priority}
          onChange={(e) => setPriority(e.target.value as unknown as TicketPriority[])}
          sx={{ width: 160 }}
          slotProps={{ select: { multiple: true, renderValue: (v) => (v as string[]).map(humanize).join(', ') } }}
        >
          {TICKET_PRIORITIES.map((p) => (
            <MenuItem key={p} value={p}>
              {humanize(p)}
            </MenuItem>
          ))}
        </TextField>
        <Button variant="contained" onClick={exportCsv} disabled={!preview || preview.totalElements === 0}>
          Download CSV ({preview?.totalElements ?? 0} rows)
        </Button>
      </Box>
      <ErrorBanner error={error} />
      {exportError && <ErrorBanner error={{ detail: exportError }} />}

      <Typography variant="h2" sx={{ mb: 1 }}>
        Preview
      </Typography>
      <Paper sx={{ mb: 3 }}>
        <Table size="small">
          <TableHead>
            <TableRow>
              <TableCell sx={{ width: 90 }}>Key</TableCell>
              <TableCell>Title</TableCell>
              <TableCell>Status</TableCell>
              <TableCell>Priority</TableCell>
              <TableCell>Assignee</TableCell>
              <TableCell>Due</TableCell>
            </TableRow>
          </TableHead>
          <TableBody>
            {preview?.content.length === 0 && <TableMessage colSpan={6}>No tickets match.</TableMessage>}
            {preview?.content.map((t) => (
              <TableRow key={t.id}>
                <TableCell className="mono">{t.key}</TableCell>
                <TableCell>{t.title}</TableCell>
                <TableCell>
                  <StatusLabel status={t.status} />
                </TableCell>
                <TableCell>
                  <PriorityText priority={t.priority} />
                </TableCell>
                <TableCell>{t.assignee?.fullName ?? '–'}</TableCell>
                <TableCell>{formatDate(t.dueDate) || '–'}</TableCell>
              </TableRow>
            ))}
          </TableBody>
        </Table>
      </Paper>

      {dashboard && dashboard.productivity.length > 0 && (
        <>
          <Typography variant="h2" sx={{ mb: 1 }}>
            Closed per week (last 8 weeks)
          </Typography>
          <Paper>
            <Table size="small">
              <TableHead>
                <TableRow>
                  <TableCell>Person</TableCell>
                  {dashboard.productivity[0].closedPerWeek.map((w) => (
                    <TableCell key={w.weekStart} align="right">
                      {formatDate(w.weekStart).replace(/,? \d{4}$/, '')}
                    </TableCell>
                  ))}
                  <TableCell align="right">Total</TableCell>
                </TableRow>
              </TableHead>
              <TableBody>
                {dashboard.productivity.map((row) => (
                  <TableRow key={row.userId}>
                    <TableCell>{row.fullName}</TableCell>
                    {row.closedPerWeek.map((w) => (
                      <TableCell key={w.weekStart} align="right" sx={{ color: w.closed ? undefined : 'text.secondary' }}>
                        {w.closed}
                      </TableCell>
                    ))}
                    <TableCell align="right">
                      <b>{row.closedInPeriod}</b>
                    </TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          </Paper>
        </>
      )}
    </>
  );
}
