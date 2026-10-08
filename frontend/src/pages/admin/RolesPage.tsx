import Paper from '@mui/material/Paper';
import Table from '@mui/material/Table';
import TableBody from '@mui/material/TableBody';
import TableCell from '@mui/material/TableCell';
import TableHead from '@mui/material/TableHead';
import TableRow from '@mui/material/TableRow';
import Typography from '@mui/material/Typography';
import { Link as RouterLink } from 'react-router-dom';
import { useGetRolesQuery } from '../../api/api';
import { ErrorBanner, TableMessage } from '../../components/Feedback';
import { RoleLabel } from '../../components/Labels';
import PageHeader from '../../components/PageHeader';

/** The permission matrix as enforced by the API (PRD section 2). */
const MATRIX: [string, string, string, string, string][] = [
  ['Manage users and roles', 'Yes', '–', '–', '–'],
  ['Create / update projects', 'Yes', 'Own', '–', '–'],
  ['Delete projects', 'Yes', '–', '–', '–'],
  ['View projects', 'All', 'Own + member', 'Member', 'Member'],
  ['Create tickets', 'Yes', 'Yes', 'Yes', 'Yes'],
  ['View tickets', 'All', 'Their projects', 'Assigned + project', 'Own'],
  ['Assign tickets', 'Yes', 'Their projects', '–', '–'],
  ['Edit ticket fields', 'Yes', 'Their projects', 'Assigned', 'Own, while open'],
  ['Change status', 'Yes', 'Their projects', 'Assigned (not close)', '–'],
  ['Close / reopen', 'Yes', 'Their projects', '–', 'Reopen own (14 days)'],
  ['Comment', 'Yes', 'Yes', 'Yes', 'Own tickets'],
  ['Edit / delete comments', 'Any', 'Own', 'Own', 'Own'],
  ['Dashboard', 'Global', 'Their projects', 'Personal', 'Personal'],
  ['Reports, CSV export', 'Yes', 'Yes', '–', '–'],
  ['Deployment portal', 'Yes', '–', '–', '–'],
];

export default function RolesPage() {
  const { data, error } = useGetRolesQuery();
  return (
    <>
      <PageHeader title="Roles" description="Roles are fixed; assign them to people on the Users page." />
      <ErrorBanner error={error} />
      <Paper sx={{ mb: 3 }}>
        <Table size="small">
          <TableHead>
            <TableRow>
              <TableCell sx={{ width: 180 }}>Role</TableCell>
              <TableCell>Description</TableCell>
              <TableCell align="right" sx={{ width: 120 }}>
                Active users
              </TableCell>
            </TableRow>
          </TableHead>
          <TableBody>
            {!data && !error && <TableMessage colSpan={3}>Loading…</TableMessage>}
            {data?.map((r) => (
              <TableRow key={r.name}>
                <TableCell>
                  <RoleLabel role={r.name} />
                </TableCell>
                <TableCell>{r.description}</TableCell>
                <TableCell align="right">
                  <RouterLink to={`/admin/users`}>{r.userCount}</RouterLink>
                </TableCell>
              </TableRow>
            ))}
          </TableBody>
        </Table>
      </Paper>

      <Typography variant="h2" sx={{ mb: 1 }}>
        Permissions
      </Typography>
      <Paper>
        <Table size="small">
          <TableHead>
            <TableRow>
              <TableCell>Capability</TableCell>
              <TableCell>Admin</TableCell>
              <TableCell>Project manager</TableCell>
              <TableCell>Developer</TableCell>
              <TableCell>User</TableCell>
            </TableRow>
          </TableHead>
          <TableBody>
            {MATRIX.map(([capability, ...cells]) => (
              <TableRow key={capability}>
                <TableCell>{capability}</TableCell>
                {cells.map((cell, i) => (
                  <TableCell key={i} sx={{ color: cell === '–' ? 'text.secondary' : undefined }}>
                    {cell}
                  </TableCell>
                ))}
              </TableRow>
            ))}
          </TableBody>
        </Table>
      </Paper>
    </>
  );
}
