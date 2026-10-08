import { createApi, type BaseQueryFn } from '@reduxjs/toolkit/query/react';
import type { AxiosError, AxiosRequestConfig } from 'axios';
import { http } from './http';
import type {
  Activity,
  Comment,
  Dashboard,
  Deployment,
  DeploymentAction,
  DeploymentDetail,
  DeploymentEnvironment,
  DeploymentOptions,
  CloudProvider,
  Member,
  Page,
  Problem,
  Project,
  Role,
  RoleName,
  TicketDetail,
  TicketFilters,
  TicketPriority,
  TicketStatus,
  TicketSummary,
  TicketType,
  TimelineEntry,
  User,
  UserRef,
} from './types';

/** RTK Query on top of the shared axios instance, so every call gets token refresh. */
const axiosBaseQuery: BaseQueryFn<AxiosRequestConfig, unknown, { status?: number; data?: Problem }> = async (
  config,
) => {
  try {
    const result = await http.request({ paramsSerializer: { indexes: null }, ...config });
    return { data: result.data };
  } catch (e) {
    const error = e as AxiosError<Problem>;
    return { error: { status: error.response?.status, data: error.response?.data } };
  }
};

export interface PageParams {
  page?: number;
  size?: number;
  sort?: string;
}

export const api = createApi({
  reducerPath: 'api',
  baseQuery: axiosBaseQuery,
  tagTypes: ['Ticket', 'Project', 'User', 'Role', 'Deployment', 'Dashboard'],
  endpoints: (b) => ({
    // Dashboard & activity
    getDashboard: b.query<Dashboard, { projectId?: number; weeks?: number }>({
      query: (params) => ({ url: '/dashboard', params }),
      providesTags: ['Dashboard'],
    }),
    getActivity: b.query<Page<Activity>, { projectId?: number } & PageParams>({
      query: (params) => ({ url: '/activity', params }),
      providesTags: ['Dashboard'],
    }),

    // Projects
    getProjects: b.query<Page<Project>, { q?: string } & PageParams>({
      query: (params) => ({ url: '/projects', params: { size: 100, ...params } }),
      providesTags: ['Project'],
    }),
    getProject: b.query<Project, number>({
      query: (id) => ({ url: `/projects/${id}` }),
      providesTags: (_r, _e, id) => [{ type: 'Project', id }],
    }),
    createProject: b.mutation<Project, { key: string; name: string; description?: string }>({
      query: (data) => ({ url: '/projects', method: 'POST', data }),
      invalidatesTags: ['Project'],
    }),
    updateProject: b.mutation<Project, { id: number; name: string; description?: string }>({
      query: ({ id, ...data }) => ({ url: `/projects/${id}`, method: 'PUT', data }),
      invalidatesTags: ['Project'],
    }),
    deleteProject: b.mutation<void, number>({
      query: (id) => ({ url: `/projects/${id}`, method: 'DELETE' }),
      invalidatesTags: ['Project', 'Ticket', 'Dashboard'],
    }),
    getMembers: b.query<Member[], number>({
      query: (id) => ({ url: `/projects/${id}/members` }),
      providesTags: (_r, _e, id) => [{ type: 'Project', id }],
    }),
    addMember: b.mutation<Member[], { projectId: number; userId: number }>({
      query: ({ projectId, userId }) => ({ url: `/projects/${projectId}/members`, method: 'POST', data: { userId } }),
      invalidatesTags: ['Project'],
    }),
    removeMember: b.mutation<Member[], { projectId: number; userId: number }>({
      query: ({ projectId, userId }) => ({ url: `/projects/${projectId}/members/${userId}`, method: 'DELETE' }),
      invalidatesTags: ['Project'],
    }),

    // Tickets
    getTickets: b.query<Page<TicketSummary>, TicketFilters & PageParams>({
      query: (params) => ({ url: '/tickets', params }),
      providesTags: ['Ticket'],
    }),
    getTicket: b.query<TicketDetail, number>({
      query: (id) => ({ url: `/tickets/${id}` }),
      providesTags: (_r, _e, id) => [{ type: 'Ticket', id }],
    }),
    createTicket: b.mutation<
      TicketDetail,
      {
        projectId: number;
        title: string;
        description?: string;
        type: TicketType;
        priority: TicketPriority;
        assigneeId?: number;
        dueDate?: string;
      }
    >({
      query: (data) => ({ url: '/tickets', method: 'POST', data }),
      invalidatesTags: ['Ticket', 'Project', 'Dashboard'],
    }),
    updateTicket: b.mutation<
      TicketDetail,
      {
        id: number;
        title: string;
        description?: string;
        type: TicketType;
        priority: TicketPriority;
        dueDate?: string | null;
        version: number;
      }
    >({
      query: ({ id, ...data }) => ({ url: `/tickets/${id}`, method: 'PUT', data }),
      invalidatesTags: ['Ticket', 'Dashboard'],
    }),
    assignTicket: b.mutation<TicketDetail, { id: number; assigneeId: number | null }>({
      query: ({ id, assigneeId }) => ({ url: `/tickets/${id}/assignee`, method: 'PUT', data: { assigneeId } }),
      invalidatesTags: ['Ticket', 'Dashboard'],
    }),
    transitionTicket: b.mutation<TicketDetail, { id: number; status: TicketStatus }>({
      query: ({ id, status }) => ({ url: `/tickets/${id}/transitions`, method: 'POST', data: { status } }),
      invalidatesTags: ['Ticket', 'Project', 'Dashboard'],
    }),
    deleteTicket: b.mutation<void, number>({
      query: (id) => ({ url: `/tickets/${id}`, method: 'DELETE' }),
      invalidatesTags: ['Ticket', 'Project', 'Dashboard'],
    }),
    getTimeline: b.query<TimelineEntry[], number>({
      query: (id) => ({ url: `/tickets/${id}/timeline` }),
      providesTags: (_r, _e, id) => [{ type: 'Ticket', id }],
    }),
    addComment: b.mutation<Comment, { ticketId: number; body: string }>({
      query: ({ ticketId, body }) => ({ url: `/tickets/${ticketId}/comments`, method: 'POST', data: { body } }),
      invalidatesTags: (_r, _e, { ticketId }) => [{ type: 'Ticket', id: ticketId }],
    }),
    editComment: b.mutation<Comment, { id: number; ticketId: number; body: string }>({
      query: ({ id, body }) => ({ url: `/comments/${id}`, method: 'PUT', data: { body } }),
      invalidatesTags: (_r, _e, { ticketId }) => [{ type: 'Ticket', id: ticketId }],
    }),
    deleteComment: b.mutation<void, { id: number; ticketId: number }>({
      query: ({ id }) => ({ url: `/comments/${id}`, method: 'DELETE' }),
      invalidatesTags: (_r, _e, { ticketId }) => [{ type: 'Ticket', id: ticketId }],
    }),

    // Users & roles
    getUsers: b.query<Page<User>, { q?: string; role?: RoleName; enabled?: boolean } & PageParams>({
      query: (params) => ({ url: '/users', params }),
      providesTags: ['User'],
    }),
    lookupUsers: b.query<UserRef[], string>({
      query: (q) => ({ url: '/users/lookup', params: { q } }),
    }),
    createUser: b.mutation<User, { email: string; fullName: string; password: string; roles: RoleName[] }>({
      query: (data) => ({ url: '/users', method: 'POST', data }),
      invalidatesTags: ['User', 'Role'],
    }),
    updateUser: b.mutation<User, { id: number; email: string; fullName: string; enabled: boolean }>({
      query: ({ id, ...data }) => ({ url: `/users/${id}`, method: 'PUT', data }),
      invalidatesTags: ['User'],
    }),
    updateUserRoles: b.mutation<User, { id: number; roles: RoleName[] }>({
      query: ({ id, roles }) => ({ url: `/users/${id}/roles`, method: 'PUT', data: { roles } }),
      invalidatesTags: ['User', 'Role'],
    }),
    deleteUser: b.mutation<void, number>({
      query: (id) => ({ url: `/users/${id}`, method: 'DELETE' }),
      invalidatesTags: ['User', 'Role'],
    }),
    getRoles: b.query<Role[], void>({
      query: () => ({ url: '/roles' }),
      providesTags: ['Role'],
    }),
    updateProfile: b.mutation<User, { fullName: string }>({
      query: (data) => ({ url: '/users/me', method: 'PUT', data }),
    }),
    changePassword: b.mutation<void, { currentPassword: string; newPassword: string }>({
      query: (data) => ({ url: '/users/me/password', method: 'PUT', data }),
    }),

    // Deployments
    getDeploymentOptions: b.query<DeploymentOptions, void>({
      query: () => ({ url: '/deployments/options' }),
    }),
    getDeployments: b.query<Page<Deployment>, PageParams>({
      query: (params) => ({ url: '/deployments', params }),
      providesTags: ['Deployment'],
    }),
    getDeployment: b.query<DeploymentDetail, number>({
      query: (id) => ({ url: `/deployments/${id}` }),
      providesTags: (_r, _e, id) => [{ type: 'Deployment', id }],
    }),
    createDeployment: b.mutation<
      DeploymentDetail,
      {
        cloudProvider: CloudProvider;
        region: string;
        clusterCount: number;
        environment: DeploymentEnvironment;
        action: DeploymentAction;
        confirmEnvironment?: string;
      }
    >({
      query: (data) => ({ url: '/deployments', method: 'POST', data }),
      invalidatesTags: ['Deployment'],
    }),
    destroyDeployment: b.mutation<DeploymentDetail, number>({
      query: (id) => ({ url: `/deployments/${id}/destroy`, method: 'POST' }),
      invalidatesTags: ['Deployment'],
    }),
  }),
});

export const {
  useGetDashboardQuery,
  useGetActivityQuery,
  useGetProjectsQuery,
  useGetProjectQuery,
  useCreateProjectMutation,
  useUpdateProjectMutation,
  useDeleteProjectMutation,
  useGetMembersQuery,
  useAddMemberMutation,
  useRemoveMemberMutation,
  useGetTicketsQuery,
  useGetTicketQuery,
  useCreateTicketMutation,
  useUpdateTicketMutation,
  useAssignTicketMutation,
  useTransitionTicketMutation,
  useDeleteTicketMutation,
  useGetTimelineQuery,
  useAddCommentMutation,
  useEditCommentMutation,
  useDeleteCommentMutation,
  useGetUsersQuery,
  useLookupUsersQuery,
  useCreateUserMutation,
  useUpdateUserMutation,
  useUpdateUserRolesMutation,
  useDeleteUserMutation,
  useGetRolesQuery,
  useUpdateProfileMutation,
  useChangePasswordMutation,
  useGetDeploymentOptionsQuery,
  useGetDeploymentsQuery,
  useGetDeploymentQuery,
  useCreateDeploymentMutation,
  useDestroyDeploymentMutation,
} = api;
