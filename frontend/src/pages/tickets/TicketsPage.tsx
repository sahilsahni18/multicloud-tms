import Box from '@mui/material/Box';
import Button from '@mui/material/Button';
import Link from '@mui/material/Link';
import MenuItem from '@mui/material/MenuItem';
import Paper from '@mui/material/Paper';
import Table from '@mui/material/Table';
import TableBody from '@mui/material/TableBody';
import TableCell from '@mui/material/TableCell';
import TableHead from '@mui/material/TableHead';
import TablePagination from '@mui/material/TablePagination';
import TableRow from '@mui/material/TableRow';
import TableSortLabel from '@mui/material/TableSortLabel';
import TextField from '@mui/material/TextField';
import { useEffect, useMemo, useState } from 'react';
import { Link as RouterLink, useSearchParams } from 'react-router-dom';
import { useGetProjectsQuery, useGetTicketsQuery } from '../../api/api';
import { download, errorMessage } from '../../api/http';
import {
  TICKET_PRIORITIES,
  TICKET_STATUSES,
  type TicketFilters,
  type TicketPriority,
  type TicketStatus,
} from '../../api/types';
import { useCurrentUser, useHasRole } from '../../app/hooks';
import { ErrorBanner, TableMessage } from '../../components/Feedback';
import { PriorityText, StatusLabel, TypeText } from '../../components/Labels';
import PageHeader from '../../components/PageHeader';
import { humanize, timeAgo } from '../../utils/format';
import { getPreferredPageSize } from '../../utils/preferences';
import NewTicketDialog from './NewTicketDialog';

type Who = '' | 'assigned' | 'reported' | 'unassigned';

const SORTABLE: { key: string; label: string }[] = [
  { key: 'key', label: 'Key' },
  { key: 'title', label: 'Title' },
  { key: 'status', label: 'Status' },
  { key: 'priority', label: 'Priority' },
];

/** All filters live in the URL, so a filtered list can be bookmarked or shared. */
function useTicketQuery() {
  const [params, setParams] = useSearchParams();
  const update = (changes: Record<string, string | string[] | null>) => {
    const next = new URLSearchParams(params);
    Object.entries(changes).forEach(([key, value]) => {
      next.delete(key);
      if (Array.isArray(value)) value.forEach((v) => next.append(key, v));
      else if (value) next.set(key, value);
    });
    if (!('page' in changes)) next.delete('page');
    setParams(next, { replace: true });
  };
  return { params, update };
}

export default function TicketsPage() {
  const user = useCurrentUser();
  const canExport = useHasRole('ADMIN', 'PROJECT_MANAGER');
  const { params, update } = useTicketQuery();
  const [creating, setCreating] = useState(false);
  const [exportError, setExportError] = useState<string | null>(null);

  const q = params.get('q') ?? '';
  const [search, setSearch] = useState(q);
  useEffect(() => setSearch(q), [q]);

  const projectId = params.get('projectId') ? Number(params.get('projectId')) : undefined;
  const status = params.getAll('status') as TicketStatus[];
  const priority = params.getAll('priority') as TicketPriority[];
  const who = (params.get('who') ?? '') as Who;
  const page = Number(params.get('page') ?? 0);
  const size = Number(params.get('size') ?? getPreferredPageSize());
  const sort = params.get('sort') ?? 'createdAt,desc';
  const [sortField, sortDir] = sort.split(',') as [string, 'asc' | 'desc'];

  const filters: TicketFilters = useMemo(
    () => ({
      q: q || undefined,
      projectId,
      status: status.length ? status : undefined,
      priority: priority.length ? priority : undefined,
      assigneeId: who === 'assigned' ? user?.id : undefined,
      reporterId: who === 'reported' ? user?.id : undefined,
      unassigned: who === 'unassigned' || undefined,
    }),
    // eslint-disable-next-line react-hooks/exhaustive-deps
    [params.toString(), user?.id],
  );

  const { data: projects } = useGetProjectsQuery({});
  const { data, error, isFetching } = useGetTicketsQuery({ ...filters, page, size, sort });
  const hasFilters = !!(q || projectId || status.length || priority.length || who);

  async function exportCsv() {
    setExportError(null);
    try {
      await download('/tickets/export', { ...filters, sort }, 'tickets.csv');
    } catch (e) {
      setExportError(errorMessage(e, 'Export failed'));
    }
  }

  return (
    <>
      <PageHeader
        title="Tickets"
        description={data ? `${data.totalElements} ticket${data.totalElements === 1 ? '' : 's'}` : ' '}
        actions={
          <>
            {canExport && <Button onClick={exportCsv}>Export CSV</Button>}
            <Button variant="contained" onClick={() => setCreating(true)}>
              New ticket
            </Button>
          </>
        }
      />

      <Box
        component="form"
        onSubmit={(e: React.FormEvent) => {
          e.preventDefault();
          update({ q: search.trim() || null });
        }}
        sx={{ display: 'flex', gap: 1, flexWrap: 'wrap', mb: 1.5, alignItems: 'center' }}
      >
        <TextField
          placeholder="Title or key, then Enter"
          value={search}
          onChange={(e) => setSearch(e.target.value)}
          onBlur={() => search.trim() !== q && update({ q: search.trim() || null })}
          sx={{ width: 240 }}
          slotProps={{ htmlInput: { 'aria-label': 'Search' } }}
        />
        <TextField
          select
          label="Project"
          value={projectId ?? ''}
          onChange={(e) => update({ projectId: e.target.value ? String(e.target.value) : null })}
          sx={{ width: 170 }}
        >
          <MenuItem value="">All</MenuItem>
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
          onChange={(e) => update({ status: e.target.value as unknown as string[] })}
          sx={{ width: 170 }}
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
          onChange={(e) => update({ priority: e.target.value as unknown as string[] })}
          sx={{ width: 150 }}
          slotProps={{ select: { multiple: true, renderValue: (v) => (v as string[]).map(humanize).join(', ') } }}
        >
          {[...TICKET_PRIORITIES].reverse().map((p) => (
            <MenuItem key={p} value={p}>
              {humanize(p)}
            </MenuItem>
          ))}
        </TextField>
        <TextField
          select
          label="People"
          value={who}
          onChange={(e) => update({ who: e.target.value || null })}
          sx={{ width: 160 }}
        >
          <MenuItem value="">Anyone</MenuItem>
          <MenuItem value="assigned">Assigned to me</MenuItem>
          <MenuItem value="reported">Reported by me</MenuItem>
          <MenuItem value="unassigned">Unassigned</MenuItem>
        </TextField>
        {hasFilters && (
          <Link
            component="button"
            type="button"
            variant="body2"
            onClick={() => update({ q: null, projectId: null, status: null, priority: null, who: null })}
          >
            Clear filters
          </Link>
        )}
      </Box>

      <ErrorBanner error={error} />
      {exportError && <ErrorBanner error={{ detail: exportError }} />}

      <Paper>
        <Table size="small">
          <TableHead>
            <TableRow>
              {SORTABLE.map((col) => (
                <TableCell key={col.key} sx={col.key === 'key' ? { width: 90 } : undefined}>
                  <TableSortLabel
                    active={sortField === col.key}
                    direction={sortField === col.key ? sortDir : 'asc'}
                    onClick={() =>
                      update({ sort: `${col.key},${sortField === col.key && sortDir === 'asc' ? 'desc' : 'asc'}` })
                    }
                  >
                    {col.label}
                  </TableSortLabel>
                </TableCell>
              ))}
              <TableCell>Type</TableCell>
              <TableCell>Assignee</TableCell>
              <TableCell>
                <TableSortLabel
                  active={sortField === 'updatedAt'}
                  direction={sortField === 'updatedAt' ? sortDir : 'desc'}
                  onClick={() => update({ sort: `updatedAt,${sortField === 'updatedAt' && sortDir === 'desc' ? 'asc' : 'desc'}` })}
                >
                  Updated
                </TableSortLabel>
              </TableCell>
            </TableRow>
          </TableHead>
          <TableBody sx={{ opacity: isFetching ? 0.6 : 1 }}>
            {data?.content.length === 0 && (
              <TableMessage colSpan={7}>{hasFilters ? 'No tickets match these filters.' : 'No tickets yet.'}</TableMessage>
            )}
            {!data && !error && <TableMessage colSpan={7}>Loading…</TableMessage>}
            {data?.content.map((t) => (
              <TableRow key={t.id} hover>
                <TableCell className="mono">
                  <Link component={RouterLink} to={`/tickets/${t.id}`}>
                    {t.key}
                  </Link>
                </TableCell>
                <TableCell>
                  <Link component={RouterLink} to={`/tickets/${t.id}`} color="inherit">
                    {t.title}
                  </Link>
                </TableCell>
                <TableCell>
                  <StatusLabel status={t.status} />
                </TableCell>
                <TableCell>
                  <PriorityText priority={t.priority} />
                </TableCell>
                <TableCell>
                  <TypeText type={t.type} />
                </TableCell>
                <TableCell sx={{ color: t.assignee ? undefined : 'text.secondary' }}>
                  {t.assignee?.fullName ?? 'Unassigned'}
                </TableCell>
                <TableCell sx={{ color: 'text.secondary', whiteSpace: 'nowrap' }} title={t.updatedAt}>
                  {timeAgo(t.updatedAt)}
                </TableCell>
              </TableRow>
            ))}
          </TableBody>
        </Table>
        {data && data.totalElements > 0 && (
          <TablePagination
            component="div"
            count={data.totalElements}
            page={page}
            rowsPerPage={size}
            rowsPerPageOptions={[20, 50, 100]}
            onPageChange={(_e, p) => update({ page: String(p) })}
            onRowsPerPageChange={(e) => update({ size: e.target.value, page: null })}
          />
        )}
      </Paper>

      <NewTicketDialog open={creating} onClose={() => setCreating(false)} defaultProjectId={projectId} />
    </>
  );
}
