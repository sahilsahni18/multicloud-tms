import { Route, Routes } from 'react-router-dom';
import AppLayout from './components/AppLayout';
import { GuestOnly, RequireAuth, RequireRole } from './features/auth/guards';
import LoginPage from './features/auth/LoginPage';
import RegisterPage from './features/auth/RegisterPage';
import DashboardPage from './pages/DashboardPage';
import NotFoundPage from './pages/NotFoundPage';
import ProfilePage from './pages/ProfilePage';
import ReportsPage from './pages/ReportsPage';
import SettingsPage from './pages/SettingsPage';
import DeploymentDetailPage from './pages/admin/DeploymentDetailPage';
import DeploymentsPage from './pages/admin/DeploymentsPage';
import RolesPage from './pages/admin/RolesPage';
import UsersPage from './pages/admin/UsersPage';
import ProjectDetailPage from './pages/projects/ProjectDetailPage';
import ProjectsPage from './pages/projects/ProjectsPage';
import TicketDetailPage from './pages/tickets/TicketDetailPage';
import TicketsPage from './pages/tickets/TicketsPage';

export default function App() {
  return (
    <Routes>
      <Route element={<GuestOnly />}>
        <Route path="/login" element={<LoginPage />} />
        <Route path="/register" element={<RegisterPage />} />
      </Route>

      <Route element={<RequireAuth />}>
        <Route element={<AppLayout />}>
          <Route index element={<DashboardPage />} />
          <Route path="tickets" element={<TicketsPage />} />
          <Route path="tickets/:id" element={<TicketDetailPage />} />
          <Route path="projects" element={<ProjectsPage />} />
          <Route path="projects/:id" element={<ProjectDetailPage />} />
          <Route path="profile" element={<ProfilePage />} />
          <Route path="settings" element={<SettingsPage />} />

          <Route element={<RequireRole roles={['ADMIN', 'PROJECT_MANAGER']} />}>
            <Route path="reports" element={<ReportsPage />} />
          </Route>

          <Route element={<RequireRole roles={['ADMIN']} />}>
            <Route path="admin/users" element={<UsersPage />} />
            <Route path="admin/roles" element={<RolesPage />} />
            <Route path="admin/deployments" element={<DeploymentsPage />} />
            <Route path="admin/deployments/:id" element={<DeploymentDetailPage />} />
          </Route>

          <Route path="*" element={<NotFoundPage />} />
        </Route>
      </Route>
    </Routes>
  );
}
