import AdminPanelSettingsOutlinedIcon from '@mui/icons-material/AdminPanelSettingsOutlined';
import AssessmentOutlinedIcon from '@mui/icons-material/AssessmentOutlined';
import CloudUploadOutlinedIcon from '@mui/icons-material/CloudUploadOutlined';
import FolderOutlinedIcon from '@mui/icons-material/FolderOutlined';
import FormatListBulletedIcon from '@mui/icons-material/FormatListBulleted';
import GroupOutlinedIcon from '@mui/icons-material/GroupOutlined';
import SearchIcon from '@mui/icons-material/Search';
import SpaceDashboardOutlinedIcon from '@mui/icons-material/SpaceDashboardOutlined';
import ViewKanbanOutlinedIcon from '@mui/icons-material/ViewKanbanOutlined';
import Box from '@mui/material/Box';
import Button from '@mui/material/Button';
import ButtonBase from '@mui/material/ButtonBase';
import Divider from '@mui/material/Divider';
import InputBase from '@mui/material/InputBase';
import Menu from '@mui/material/Menu';
import MenuItem from '@mui/material/MenuItem';
import Typography from '@mui/material/Typography';
import { useState } from 'react';
import { NavLink, Outlet, useNavigate } from 'react-router-dom';
import { navFor, primaryRole, type NavItem } from '../app/navigation';
import { useCurrentUser } from '../app/hooks';
import { signOut } from '../app/store';
import NewTicketDialog from '../pages/tickets/NewTicketDialog';
import { colors } from '../theme';
import { humanize } from '../utils/format';
import Logo from './Logo';
import UserAvatar from './UserAvatar';

const SIDEBAR_WIDTH = 232;

const ICONS: Record<string, React.ReactNode> = {
  '/': <SpaceDashboardOutlinedIcon fontSize="small" />,
  '/board': <ViewKanbanOutlinedIcon fontSize="small" />,
  '/tickets': <FormatListBulletedIcon fontSize="small" />,
  '/projects': <FolderOutlinedIcon fontSize="small" />,
  '/reports': <AssessmentOutlinedIcon fontSize="small" />,
  '/admin/users': <GroupOutlinedIcon fontSize="small" />,
  '/admin/roles': <AdminPanelSettingsOutlinedIcon fontSize="small" />,
  '/admin/deployments': <CloudUploadOutlinedIcon fontSize="small" />,
};

const SECTION_TITLES: Partial<Record<NavItem['section'], string>> = {
  planning: 'Planning',
  admin: 'Administration',
};

function SidebarLink({ item }: { item: NavItem }) {
  return (
    <Box
      component={NavLink}
      to={item.path}
      end={item.path === '/'}
      sx={{
        position: 'relative',
        display: 'flex',
        alignItems: 'center',
        gap: 1.5,
        px: 1.5,
        height: 36,
        mx: 1,
        borderRadius: '3px',
        fontSize: 14,
        color: colors.muted,
        textDecoration: 'none',
        '& svg': { color: colors.muted },
        '&:hover': { bgcolor: colors.hover, color: colors.text },
        '&.active': {
          bgcolor: colors.blueBg,
          color: colors.blue,
          fontWeight: 500,
          '& svg': { color: colors.blue },
          '&::before': {
            content: '""',
            position: 'absolute',
            left: 0,
            top: 8,
            bottom: 8,
            width: 3,
            borderRadius: '0 2px 2px 0',
            bgcolor: colors.blue,
          },
        },
      }}
    >
      {ICONS[item.path]}
      {item.label}
    </Box>
  );
}

export default function AppLayout() {
  const user = useCurrentUser();
  const navigate = useNavigate();
  const [search, setSearch] = useState('');
  const [creating, setCreating] = useState(false);
  const [menuAnchor, setMenuAnchor] = useState<HTMLElement | null>(null);
  const items = navFor(user?.roles ?? []);
  const role = primaryRole(user?.roles ?? []);

  const sections = (['main', 'planning', 'admin'] as const)
    .map((section) => ({ section, items: items.filter((i) => i.section === section) }))
    .filter((s) => s.items.length > 0);
  const accountItems = items.filter((i) => i.section === 'account');

  return (
    <Box sx={{ display: 'flex', flexDirection: 'column', height: '100%' }}>
      <Box
        component="header"
        sx={{
          height: 56,
          flexShrink: 0,
          display: 'flex',
          alignItems: 'center',
          gap: 2,
          px: 2,
          borderBottom: 1,
          borderColor: 'divider',
          bgcolor: '#fff',
        }}
      >
        <Box component={NavLink} to="/" sx={{ textDecoration: 'none', width: SIDEBAR_WIDTH - 32, display: 'flex' }}>
          <Logo size={24} />
        </Box>
        <Button variant="contained" onClick={() => setCreating(true)} sx={{ px: 1.5 }}>
          Create
        </Button>

        <Box sx={{ ml: 'auto', display: 'flex', alignItems: 'center', gap: 1.5 }}>
          <Box
            component="form"
            onSubmit={(e: React.FormEvent) => {
              e.preventDefault();
              navigate(`/tickets?q=${encodeURIComponent(search.trim())}`);
            }}
            sx={{
              display: 'flex',
              alignItems: 'center',
              gap: 0.5,
              width: 260,
              px: 1,
              height: 32,
              border: 1,
              borderColor: '#8590A2',
              borderRadius: '3px',
              '&:focus-within': { borderColor: colors.blue, boxShadow: `inset 0 0 0 1px ${colors.blue}` },
            }}
          >
            <SearchIcon sx={{ fontSize: 18, color: colors.muted }} />
            <InputBase
              value={search}
              onChange={(e) => setSearch(e.target.value)}
              placeholder="Search"
              inputProps={{ 'aria-label': 'Search tickets' }}
              sx={{ flex: 1, fontSize: 14 }}
            />
          </Box>
          <ButtonBase
            aria-label="Account menu"
            onClick={(e) => setMenuAnchor(e.currentTarget)}
            sx={{ borderRadius: '50%', '&:hover': { boxShadow: `0 0 0 3px ${colors.hover}` } }}
          >
            <UserAvatar name={user?.fullName} size={32} tooltip={false} />
          </ButtonBase>
          <Menu
            anchorEl={menuAnchor}
            open={!!menuAnchor}
            onClose={() => setMenuAnchor(null)}
            anchorOrigin={{ vertical: 'bottom', horizontal: 'right' }}
            transformOrigin={{ vertical: 'top', horizontal: 'right' }}
          >
            <Box sx={{ px: 2, py: 1, minWidth: 220 }}>
              <Typography sx={{ fontWeight: 600 }}>{user?.fullName}</Typography>
              <Typography variant="body2" color="text.secondary">
                {user?.email}
              </Typography>
              {role && (
                <Typography variant="body2" color="text.secondary">
                  {humanize(role)}
                </Typography>
              )}
            </Box>
            <Divider />
            {accountItems.map((item) => (
              <MenuItem
                key={item.path}
                onClick={() => {
                  setMenuAnchor(null);
                  navigate(item.path);
                }}
              >
                {item.label}
              </MenuItem>
            ))}
            <Divider />
            <MenuItem
              onClick={async () => {
                setMenuAnchor(null);
                await signOut();
                navigate('/login');
              }}
            >
              Log out
            </MenuItem>
          </Menu>
        </Box>
      </Box>

      <Box sx={{ display: 'flex', flex: 1, minHeight: 0 }}>
        <Box
          component="nav"
          aria-label="Main"
          sx={{
            width: SIDEBAR_WIDTH,
            flexShrink: 0,
            borderRight: 1,
            borderColor: 'divider',
            bgcolor: colors.subtle,
            py: 2,
            overflowY: 'auto',
          }}
        >
          {sections.map(({ section, items: sectionItems }) => (
            <Box key={section} sx={{ mb: 2 }}>
              {SECTION_TITLES[section] && (
                <Typography
                  sx={{ px: 2.5, pb: 0.5, fontSize: 11, fontWeight: 700, color: colors.muted, textTransform: 'uppercase', letterSpacing: '0.02em' }}
                >
                  {SECTION_TITLES[section]}
                </Typography>
              )}
              {sectionItems.map((item) => (
                <SidebarLink key={item.path} item={item} />
              ))}
            </Box>
          ))}
        </Box>

        <Box component="main" sx={{ flex: 1, overflow: 'auto', px: 5, py: 3 }}>
          <Box sx={{ maxWidth: 1280 }}>
            <Outlet />
          </Box>
        </Box>
      </Box>

      <NewTicketDialog open={creating} onClose={() => setCreating(false)} />
    </Box>
  );
}
