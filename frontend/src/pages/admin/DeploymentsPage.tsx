import Alert from '@mui/material/Alert';
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
import TextField from '@mui/material/TextField';
import Typography from '@mui/material/Typography';
import { useState } from 'react';
import { Link as RouterLink, useNavigate } from 'react-router-dom';
import { useCreateDeploymentMutation, useGetDeploymentOptionsQuery, useGetDeploymentsQuery } from '../../api/api';
import { errorMessage } from '../../api/http';
import {
  TERMINAL_DEPLOYMENT_STATUSES,
  type CloudProvider,
  type DeploymentAction,
  type DeploymentEnvironment,
} from '../../api/types';
import { ErrorBanner, TableMessage } from '../../components/Feedback';
import { DeploymentStatusLabel } from '../../components/Labels';
import PageHeader from '../../components/PageHeader';
import { formatDateTime, humanize } from '../../utils/format';

const CLOUD_NAMES: Record<CloudProvider, string> = { AWS: 'AWS', AZURE: 'Azure' };

function DeployForm() {
  const navigate = useNavigate();
  const { data: options } = useGetDeploymentOptionsQuery();
  const [cloud, setCloud] = useState<CloudProvider>('AWS');
  const [region, setRegion] = useState('');
  const [clusters, setClusters] = useState(1);
  const [environment, setEnvironment] = useState<DeploymentEnvironment>('DEV');
  const [action, setAction] = useState<DeploymentAction>('APPLY');
  const [confirm, setConfirm] = useState('');
  const [deploy, { isLoading, error }] = useCreateDeploymentMutation();

  if (!options) return null;
  const regions = options.regions[cloud] ?? [];
  const selectedRegion = regions.includes(region) ? region : regions[0] ?? '';
  const applyAllowed = options.applyEnvironments.includes(environment);
  const effectiveAction: DeploymentAction = applyAllowed ? action : 'PLAN';
  const needsConfirm = environment === 'PROD';

  async function submit(e: React.FormEvent) {
    e.preventDefault();
    const result = await deploy({
      cloudProvider: cloud,
      region: selectedRegion,
      clusterCount: clusters,
      environment,
      action: effectiveAction,
      confirmEnvironment: needsConfirm ? confirm : undefined,
    })
      .unwrap()
      .catch(() => null);
    if (result) navigate(`/admin/deployments/${result.deployment.id}`);
  }

  const clusterChoices = Array.from({ length: options.maxClusters - options.minClusters + 1 }, (_, i) => options.minClusters + i);

  return (
    <Paper component="form" onSubmit={submit} sx={{ p: 2, mb: 3 }}>
      <Typography variant="h2" sx={{ mb: 1.5 }}>
        New deployment
      </Typography>
      {error && (
        <Alert severity="error" variant="outlined" sx={{ mb: 1.5 }}>
          {errorMessage(error)}
        </Alert>
      )}
      <Box sx={{ display: 'grid', gridTemplateColumns: 'repeat(5, minmax(120px, 1fr))', gap: 1.5, alignItems: 'start' }}>
        <TextField select label="Cloud provider" value={cloud} onChange={(e) => setCloud(e.target.value as CloudProvider)}>
          {(Object.keys(options.regions) as CloudProvider[]).map((c) => (
            <MenuItem key={c} value={c}>
              {CLOUD_NAMES[c]}
            </MenuItem>
          ))}
        </TextField>
        <TextField select label="Region" value={selectedRegion} onChange={(e) => setRegion(e.target.value)}>
          {regions.map((r) => (
            <MenuItem key={r} value={r}>
              {r}
            </MenuItem>
          ))}
        </TextField>
        <TextField select label="Clusters" value={clusters} onChange={(e) => setClusters(Number(e.target.value))}>
          {clusterChoices.map((n) => (
            <MenuItem key={n} value={n}>
              {n}
            </MenuItem>
          ))}
        </TextField>
        <TextField
          select
          label="Environment"
          value={environment}
          onChange={(e) => setEnvironment(e.target.value as DeploymentEnvironment)}
        >
          {options.environments.map((env) => (
            <MenuItem key={env} value={env}>
              {env === 'PROD' ? 'Production' : env === 'QA' ? 'QA' : 'Dev'}
            </MenuItem>
          ))}
        </TextField>
        <TextField
          select
          label="Action"
          value={effectiveAction}
          onChange={(e) => setAction(e.target.value as DeploymentAction)}
          helperText={applyAllowed ? undefined : 'Plan only for this environment'}
        >
          <MenuItem value="APPLY" disabled={!applyAllowed}>
            Apply (create)
          </MenuItem>
          <MenuItem value="PLAN">Plan only</MenuItem>
        </TextField>
      </Box>
      {needsConfirm && (
        <TextField
          label="Type PROD to confirm"
          value={confirm}
          onChange={(e) => setConfirm(e.target.value)}
          sx={{ mt: 1.5, width: 240 }}
        />
      )}
      <Box sx={{ display: 'flex', alignItems: 'center', gap: 2, mt: 2 }}>
        <Button type="submit" variant="contained" disabled={isLoading || !selectedRegion || (needsConfirm && confirm !== 'PROD')}>
          {effectiveAction === 'PLAN' ? 'Run plan' : 'Deploy'}
        </Button>
        <Typography variant="body2" color="text.secondary">
          Runner: <span className="mono">{options.runner}</span>
          {options.runner === 'SIMULATED' && ' (no cloud resources are created)'}
        </Typography>
      </Box>
    </Paper>
  );
}

export default function DeploymentsPage() {
  const [page, setPage] = useState(0);
  const { data, error } = useGetDeploymentsQuery(
    { page, size: 20, sort: 'createdAt,desc' },
    { pollingInterval: 5000, skipPollingIfUnfocused: true },
  );

  return (
    <>
      <PageHeader title="Deployments" description="Provision an environment with OpenTofu and roll out the app." />
      <DeployForm />

      <Typography variant="h2" sx={{ mb: 1 }}>
        History
      </Typography>
      <ErrorBanner error={error} />
      <Paper>
        <Table size="small">
          <TableHead>
            <TableRow>
              <TableCell sx={{ width: 60 }}>ID</TableCell>
              <TableCell>Cloud</TableCell>
              <TableCell>Region</TableCell>
              <TableCell align="right">Clusters</TableCell>
              <TableCell>Env.</TableCell>
              <TableCell>Action</TableCell>
              <TableCell>Status</TableCell>
              <TableCell>Requested by</TableCell>
              <TableCell>Started</TableCell>
              <TableCell>Finished</TableCell>
            </TableRow>
          </TableHead>
          <TableBody>
            {!data && !error && <TableMessage colSpan={10}>Loading…</TableMessage>}
            {data?.content.length === 0 && <TableMessage colSpan={10}>No deployments yet.</TableMessage>}
            {data?.content.map((d) => (
              <TableRow key={d.id} hover>
                <TableCell className="mono">
                  <Link component={RouterLink} to={`/admin/deployments/${d.id}`}>
                    #{d.id}
                  </Link>
                </TableCell>
                <TableCell>{CLOUD_NAMES[d.cloudProvider]}</TableCell>
                <TableCell className="mono">{d.region}</TableCell>
                <TableCell align="right">{d.clusterCount}</TableCell>
                <TableCell>{d.environment}</TableCell>
                <TableCell>{humanize(d.action)}</TableCell>
                <TableCell>
                  <DeploymentStatusLabel status={d.status} />
                </TableCell>
                <TableCell>{d.requestedBy.fullName}</TableCell>
                <TableCell sx={{ whiteSpace: 'nowrap' }}>{formatDateTime(d.startedAt ?? d.createdAt)}</TableCell>
                <TableCell sx={{ whiteSpace: 'nowrap', color: 'text.secondary' }}>
                  {TERMINAL_DEPLOYMENT_STATUSES.includes(d.status) ? formatDateTime(d.finishedAt) : 'running'}
                </TableCell>
              </TableRow>
            ))}
          </TableBody>
        </Table>
        {data && data.totalPages > 1 && (
          <TablePagination
            component="div"
            count={data.totalElements}
            page={page}
            rowsPerPage={20}
            rowsPerPageOptions={[20]}
            onPageChange={(_e, p) => setPage(p)}
          />
        )}
      </Paper>
    </>
  );
}
