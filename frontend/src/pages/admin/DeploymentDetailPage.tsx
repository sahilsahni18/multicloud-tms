import Box from '@mui/material/Box';
import Button from '@mui/material/Button';
import Link from '@mui/material/Link';
import Paper from '@mui/material/Paper';
import Typography from '@mui/material/Typography';
import { useEffect, useState } from 'react';
import { Link as RouterLink, useParams } from 'react-router-dom';
import { useDestroyDeploymentMutation, useGetDeploymentQuery } from '../../api/api';
import { TERMINAL_DEPLOYMENT_STATUSES, type DeploymentStatus } from '../../api/types';
import ConfirmDialog from '../../components/ConfirmDialog';
import { ErrorBanner, Loading } from '../../components/Feedback';
import { DeploymentStatusLabel } from '../../components/Labels';
import { colors } from '../../theme';
import { formatDateTime, humanize } from '../../utils/format';
import { stepStates, type StepState } from './deploymentSteps';

const STEP_TEXT: Partial<Record<DeploymentStatus, string>> = {
  QUEUED: 'Queued',
  NETWORK_CREATED: 'Network created',
  CLUSTER_CREATED: 'Cluster created',
  DATABASE_CREATED: 'Database created',
  BACKEND_DEPLOYED: 'Backend deployed',
  FRONTEND_DEPLOYED: 'Frontend deployed',
  COMPLETED: 'Completed',
  PLANNED: 'Plan finished',
  DESTROYING: 'Destroying',
  DESTROYED: 'Destroyed',
};

function Marker({ state }: { state: StepState }) {
  const styles: Record<StepState, object> = {
    done: { bgcolor: colors.blue, borderColor: colors.blue },
    current: { borderColor: colors.blue, borderWidth: 2 },
    pending: { borderColor: colors.border },
    failed: { bgcolor: '#cf222e', borderColor: '#cf222e' },
  };
  return (
    <Box
      sx={{ width: 12, height: 12, borderRadius: '50%', border: 1, flexShrink: 0, mt: '4px', bgcolor: '#fff', ...styles[state] }}
    />
  );
}

export default function DeploymentDetailPage() {
  const id = Number(useParams().id);
  // Poll while the deployment is running; stop once it reaches a final state.
  const [polling, setPolling] = useState(1500);
  const { data, error, isLoading } = useGetDeploymentQuery(id, { pollingInterval: polling });
  const [destroy, destroyState] = useDestroyDeploymentMutation();
  const [confirmDestroy, setConfirmDestroy] = useState(false);
  const status = data?.deployment.status;
  useEffect(() => {
    if (status) setPolling(TERMINAL_DEPLOYMENT_STATUSES.includes(status) ? 0 : 1500);
  }, [status]);

  if (isLoading) return <Loading />;
  if (!data) return <ErrorBanner error={error} fallback="Deployment not found" />;

  const d = data.deployment;
  const canDestroy = d.action === 'APPLY' && (d.status === 'COMPLETED' || d.status === 'FAILED');

  return (
    <>
      <Typography variant="body2" color="text.secondary" sx={{ mb: 0.5 }}>
        <Link component={RouterLink} to="/admin/deployments" color="inherit">
          Deployments
        </Link>{' '}
        / #{d.id}
      </Typography>
      <Box sx={{ display: 'flex', alignItems: 'flex-start', gap: 2, pb: 1.5, mb: 2, borderBottom: 1, borderColor: 'divider' }}>
        <Box sx={{ flex: 1 }}>
          <Typography variant="h1">
            {d.cloudProvider === 'AZURE' ? 'Azure' : 'AWS'} <span className="mono">{d.region}</span> · {d.environment} ·{' '}
            {d.clusterCount} cluster{d.clusterCount > 1 ? 's' : ''}
          </Typography>
          <Typography variant="body2" color="text.secondary" sx={{ mt: 0.25 }}>
            {humanize(d.action)} requested by {d.requestedBy.fullName} on {formatDateTime(d.createdAt)} · runner{' '}
            <span className="mono">{d.runner}</span>
            {d.externalRunUrl && (
              <>
                {' · '}
                <Link href={d.externalRunUrl} target="_blank" rel="noreferrer">
                  pipeline run
                </Link>
              </>
            )}
          </Typography>
        </Box>
        <DeploymentStatusLabel status={d.status} />
        {canDestroy && (
          <Button color="error" onClick={() => setConfirmDestroy(true)}>
            Destroy
          </Button>
        )}
      </Box>

      <ErrorBanner error={destroyState.error} />

      <Paper sx={{ p: 2, mb: 3, maxWidth: 720 }}>
        {stepStates(data).map((step, i, all) => (
          <Box key={step.status} sx={{ display: 'flex', gap: 1.5, position: 'relative', pb: i < all.length - 1 ? 1.75 : 0 }}>
            {i < all.length - 1 && (
              <Box sx={{ position: 'absolute', left: 5.5, top: 18, bottom: 0, width: '1px', bgcolor: colors.border }} />
            )}
            <Marker state={step.state} />
            <Box sx={{ flex: 1 }}>
              <Box sx={{ display: 'flex', gap: 1, alignItems: 'baseline' }}>
                <Typography sx={{ fontSize: 14, fontWeight: step.state === 'pending' ? 400 : 600, color: step.state === 'pending' ? 'text.secondary' : undefined }}>
                  {STEP_TEXT[step.status] ?? humanize(step.status)}
                </Typography>
                {step.state === 'current' && (
                  <Typography variant="body2" color="primary">
                    in progress…
                  </Typography>
                )}
                {step.at && (
                  <Typography variant="body2" color="text.secondary" sx={{ ml: 'auto' }}>
                    {formatDateTime(step.at)}
                  </Typography>
                )}
              </Box>
              {step.message && (
                <Typography variant="body2" color={step.state === 'failed' ? 'error' : 'text.secondary'}>
                  {step.message}
                </Typography>
              )}
            </Box>
          </Box>
        ))}
      </Paper>

      <Typography variant="h2" sx={{ mb: 1 }}>
        Event log
      </Typography>
      <Paper sx={{ p: 1.5, bgcolor: colors.subtle }}>
        {data.events.map((e) => (
          <Box key={e.id} className="mono" sx={{ py: 0.25, whiteSpace: 'pre-wrap' }}>
            {e.occurredAt.replace('T', ' ').slice(0, 19)}Z  {e.status.padEnd(18)} {e.message ?? ''}
          </Box>
        ))}
      </Paper>

      <ConfirmDialog
        open={confirmDestroy}
        title="Destroy this environment?"
        message={`Runs tofu destroy for ${d.environment} in ${d.region}. Every resource it created is deleted.`}
        confirmLabel="Destroy"
        danger
        busy={destroyState.isLoading}
        onClose={() => setConfirmDestroy(false)}
        onConfirm={async () => {
          await destroy(d.id).unwrap().catch(() => undefined);
          setConfirmDestroy(false);
          setPolling(1500);
        }}
      />
    </>
  );
}
