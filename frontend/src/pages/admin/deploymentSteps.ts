import { TERMINAL_DEPLOYMENT_STATUSES, type DeploymentDetail, type DeploymentStatus } from '../../api/types';

export type StepState = 'done' | 'current' | 'pending' | 'failed';

/** Works out each expected step's state from the events received so far. */
export function stepStates(
  detail: DeploymentDetail,
): { status: DeploymentStatus; state: StepState; at?: string; message?: string }[] {
  const seen = new Map(detail.events.map((e) => [e.status, e]));
  const failed = detail.deployment.status === 'FAILED';
  const terminal = TERMINAL_DEPLOYMENT_STATUSES.includes(detail.deployment.status);
  let firstPending = true;
  return detail.expectedSteps.map((status) => {
    const event = seen.get(status);
    if (event) return { status, state: 'done', at: event.occurredAt, message: event.message };
    if (firstPending && !terminal) {
      firstPending = false;
      return { status, state: 'current' };
    }
    if (firstPending && failed) {
      firstPending = false;
      return { status, state: 'failed', message: detail.deployment.errorMessage };
    }
    return { status, state: 'pending' };
  });
}
