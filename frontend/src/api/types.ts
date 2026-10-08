// Mirrors the backend DTOs (backend/src/main/java/com/trackflow/tms/dto).

export type RoleName = 'ADMIN' | 'PROJECT_MANAGER' | 'DEVELOPER' | 'USER';
export type TicketStatus = 'OPEN' | 'IN_PROGRESS' | 'IN_REVIEW' | 'CLOSED';
export type TicketPriority = 'LOW' | 'MEDIUM' | 'HIGH' | 'CRITICAL';
export type TicketType = 'BUG' | 'TASK' | 'STORY';
export type CloudProvider = 'AWS' | 'AZURE';
export type DeploymentEnvironment = 'DEV' | 'QA' | 'PROD';
export type DeploymentAction = 'PLAN' | 'APPLY' | 'DESTROY';
export type DeploymentStatus =
  | 'QUEUED'
  | 'NETWORK_CREATED'
  | 'CLUSTER_CREATED'
  | 'DATABASE_CREATED'
  | 'BACKEND_DEPLOYED'
  | 'FRONTEND_DEPLOYED'
  | 'COMPLETED'
  | 'PLANNED'
  | 'FAILED'
  | 'DESTROYING'
  | 'DESTROYED';

export const TICKET_STATUSES: TicketStatus[] = ['OPEN', 'IN_PROGRESS', 'IN_REVIEW', 'CLOSED'];
export const TICKET_PRIORITIES: TicketPriority[] = ['LOW', 'MEDIUM', 'HIGH', 'CRITICAL'];
export const TICKET_TYPES: TicketType[] = ['BUG', 'TASK', 'STORY'];
export const ROLES: RoleName[] = ['ADMIN', 'PROJECT_MANAGER', 'DEVELOPER', 'USER'];
export const TERMINAL_DEPLOYMENT_STATUSES: DeploymentStatus[] = ['COMPLETED', 'PLANNED', 'FAILED', 'DESTROYED'];

export interface Page<T> {
  content: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

export interface UserRef {
  id: number;
  fullName: string;
  email: string;
}

export interface AuthUser {
  id: number;
  email: string;
  fullName: string;
  roles: RoleName[];
}

export interface AuthResponse {
  accessToken: string;
  tokenType: string;
  expiresIn: number;
  user: AuthUser;
}

/** RFC 7807 body returned by every error. */
export interface Problem {
  title: string;
  status: number;
  detail?: string;
  instance?: string;
  correlationId?: string;
  errors?: { field: string; message: string }[];
}

export interface User {
  id: number;
  email: string;
  fullName: string;
  enabled: boolean;
  roles: RoleName[];
  lastLoginAt?: string;
  createdAt: string;
}

export interface Role {
  name: RoleName;
  description: string;
  userCount: number;
}

export interface Project {
  id: number;
  key: string;
  name: string;
  description?: string;
  owner: UserRef;
  memberCount: number;
  totalTickets: number;
  openTickets: number;
  createdAt: string;
  canManage: boolean;
}

export interface Member {
  id: number;
  fullName: string;
  email: string;
  roles: RoleName[];
  owner: boolean;
}

export interface TicketSummary {
  id: number;
  key: string;
  title: string;
  type: TicketType;
  priority: TicketPriority;
  status: TicketStatus;
  projectId: number;
  projectKey: string;
  assignee?: UserRef;
  reporter: UserRef;
  dueDate?: string;
  createdAt: string;
  updatedAt: string;
  closedAt?: string;
}

export interface TicketDetail extends TicketSummary {
  description?: string;
  projectName: string;
  version: number;
  allowedTransitions: TicketStatus[];
  permissions: { canEdit: boolean; canAssign: boolean; canDelete: boolean; canComment: boolean };
}

export interface TicketFilters {
  projectId?: number;
  status?: TicketStatus[];
  priority?: TicketPriority[];
  type?: TicketType[];
  assigneeId?: number;
  unassigned?: boolean;
  reporterId?: number;
  q?: string;
}

export interface Comment {
  id: number;
  ticketId: number;
  author: UserRef;
  body: string;
  createdAt: string;
  editedAt?: string;
  canModify: boolean;
}

export interface HistoryEntry {
  id: number;
  changeType: 'CREATED' | 'ASSIGNED' | 'UPDATED' | 'STATUS_CHANGED';
  field?: string;
  oldValue?: string;
  newValue?: string;
  changedBy?: UserRef;
  changedAt: string;
}

export interface TimelineEntry {
  kind: 'COMMENT' | 'CHANGE';
  at: string;
  actor?: UserRef;
  comment?: Comment;
  change?: HistoryEntry;
}

export interface Activity {
  id: number;
  action: string;
  entityType: string;
  entityId: number;
  projectId?: number;
  summary: string;
  actor?: UserRef;
  createdAt: string;
}

export interface Dashboard {
  scope: 'GLOBAL' | 'PROJECTS' | 'PERSONAL';
  projectId?: number;
  totals: {
    total: number;
    open: number;
    closed: number;
    unassigned: number;
    overdue: number;
    createdLast7Days: number;
    closedLast7Days: number;
  };
  byStatus: Record<TicketStatus, number>;
  byPriority: Record<TicketPriority, number>;
  byType: Record<TicketType, number>;
  productivity: {
    userId: number;
    fullName: string;
    openAssigned: number;
    closedInPeriod: number;
    avgHoursToClose?: number;
    closedPerWeek: { weekStart: string; closed: number }[];
  }[];
  recentActivity: Activity[];
}

export interface Deployment {
  id: number;
  cloudProvider: CloudProvider;
  region: string;
  clusterCount: number;
  environment: DeploymentEnvironment;
  action: DeploymentAction;
  status: DeploymentStatus;
  runner: string;
  externalRunUrl?: string;
  errorMessage?: string;
  requestedBy: UserRef;
  createdAt: string;
  startedAt?: string;
  finishedAt?: string;
}

export interface DeploymentDetail {
  deployment: Deployment;
  expectedSteps: DeploymentStatus[];
  events: { id: number; status: DeploymentStatus; message?: string; occurredAt: string }[];
}

export interface DeploymentOptions {
  regions: Record<CloudProvider, string[]>;
  environments: DeploymentEnvironment[];
  applyEnvironments: DeploymentEnvironment[];
  minClusters: number;
  maxClusters: number;
  runner: string;
}
