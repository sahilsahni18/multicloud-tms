import { describe, expect, it } from 'vitest';
import type { DeploymentDetail, DeploymentStatus } from '../../api/types';
import { stepStates } from './deploymentSteps';

const APPLY: DeploymentStatus[] = [
  'QUEUED', 'NETWORK_CREATED', 'CLUSTER_CREATED', 'DATABASE_CREATED', 'BACKEND_DEPLOYED', 'FRONTEND_DEPLOYED', 'COMPLETED',
];

function detail(status: DeploymentStatus, seen: DeploymentStatus[], errorMessage?: string): DeploymentDetail {
  return {
    deployment: {
      id: 1, cloudProvider: 'AWS', region: 'us-east-1', clusterCount: 1, environment: 'DEV', action: 'APPLY', status,
      runner: 'SIMULATED', requestedBy: { id: 1, fullName: 'A', email: 'a@b.c' }, createdAt: '', errorMessage,
    },
    expectedSteps: APPLY,
    events: seen.map((s, i) => ({ id: i, status: s, occurredAt: `2026-10-08T10:00:0${i}Z` })),
  };
}

describe('deployment stepper', () => {
  it('marks done steps and the one in progress', () => {
    const states = stepStates(detail('CLUSTER_CREATED', ['QUEUED', 'NETWORK_CREATED', 'CLUSTER_CREATED']));
    expect(states.map((s) => s.state)).toEqual(['done', 'done', 'done', 'current', 'pending', 'pending', 'pending']);
  });

  it('marks the step that failed', () => {
    const states = stepStates(detail('FAILED', ['QUEUED', 'NETWORK_CREATED', 'FAILED'], 'EKS quota exceeded'));
    expect(states[2]).toMatchObject({ status: 'CLUSTER_CREATED', state: 'failed', message: 'EKS quota exceeded' });
    expect(states[3].state).toBe('pending');
  });

  it('is all done when completed', () => {
    expect(stepStates(detail('COMPLETED', APPLY)).every((s) => s.state === 'done')).toBe(true);
  });
});
