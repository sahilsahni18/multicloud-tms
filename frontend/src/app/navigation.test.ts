import { describe, expect, it } from 'vitest';
import { navFor, primaryRole } from './navigation';

const labels = (roles: Parameters<typeof navFor>[0]) => navFor(roles).map((i) => i.label);

describe('role-based navigation', () => {
  it('admin sees every menu', () => {
    expect(labels(['ADMIN'])).toEqual([
      'Dashboard', 'Tickets', 'Projects', 'Reports', 'Users', 'Roles', 'Deployments', 'Profile', 'Settings',
    ]);
  });

  it('project manager gets reports but no administration', () => {
    expect(labels(['PROJECT_MANAGER'])).toEqual(['Dashboard', 'Tickets', 'Projects', 'Reports', 'Profile', 'Settings']);
  });

  it('developer and user get the basics only', () => {
    const basics = ['Dashboard', 'Tickets', 'Projects', 'Profile', 'Settings'];
    expect(labels(['DEVELOPER'])).toEqual(basics);
    expect(labels(['USER'])).toEqual(basics);
  });

  it('several roles combine', () => {
    expect(labels(['DEVELOPER', 'PROJECT_MANAGER'])).toContain('Reports');
  });

  it('highest role wins for the label', () => {
    expect(primaryRole(['USER', 'DEVELOPER', 'PROJECT_MANAGER'])).toBe('PROJECT_MANAGER');
    expect(primaryRole([])).toBeUndefined();
  });
});
