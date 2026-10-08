import type { RoleName } from '../api/types';

export interface NavItem {
  label: string;
  path: string;
  /** Empty = every signed-in user. */
  roles: RoleName[];
  section: 'main' | 'admin' | 'account';
}

/**
 * Single source of truth for menus and route guards. The backend enforces
 * the same rules with @PreAuthorize; this only decides what to show.
 */
export const NAV_ITEMS: NavItem[] = [
  { label: 'Dashboard', path: '/', roles: [], section: 'main' },
  { label: 'Tickets', path: '/tickets', roles: [], section: 'main' },
  { label: 'Projects', path: '/projects', roles: [], section: 'main' },
  { label: 'Reports', path: '/reports', roles: ['ADMIN', 'PROJECT_MANAGER'], section: 'main' },
  { label: 'Users', path: '/admin/users', roles: ['ADMIN'], section: 'admin' },
  { label: 'Roles', path: '/admin/roles', roles: ['ADMIN'], section: 'admin' },
  { label: 'Deployments', path: '/admin/deployments', roles: ['ADMIN'], section: 'admin' },
  { label: 'Profile', path: '/profile', roles: [], section: 'account' },
  { label: 'Settings', path: '/settings', roles: [], section: 'account' },
];

export function canSee(item: Pick<NavItem, 'roles'>, userRoles: RoleName[]): boolean {
  return item.roles.length === 0 || item.roles.some((r) => userRoles.includes(r));
}

export function navFor(userRoles: RoleName[]): NavItem[] {
  return NAV_ITEMS.filter((item) => canSee(item, userRoles));
}

/** Highest role first, for the label next to the user's name. */
export function primaryRole(roles: RoleName[]): RoleName | undefined {
  return (['ADMIN', 'PROJECT_MANAGER', 'DEVELOPER', 'USER'] as RoleName[]).find((r) => roles.includes(r));
}
