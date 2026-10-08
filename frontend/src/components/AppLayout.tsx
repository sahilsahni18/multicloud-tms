import Box from '@mui/material/Box';
import Button from '@mui/material/Button';
import InputBase from '@mui/material/InputBase';
import Typography from '@mui/material/Typography';
import { useState } from 'react';
import { NavLink, Outlet, useNavigate } from 'react-router-dom';
import { navFor, primaryRole, type NavItem } from '../app/navigation';
import { useCurrentUser } from '../app/hooks';
import { signOut } from '../app/store';
import { colors } from '../theme';
import { humanize } from '../utils/format';

const SIDEBAR_WIDTH = 208;

const SECTION_TITLES: Record<NavItem['section'], string | null> = {
  main: null,
  admin: 'Administration',
  account: 'Account',
};

function SidebarLink({ item }: { item: NavItem }) {
  return (
    <Box
      component={NavLink}
      to={item.path}
      end={item.path === '/'}
      sx={{
        display: 'block',
        px: 1.5,
        py: 0.625,
        mx: 1,
        borderRadius: 1,
        fontSize: 14,
        color: 'text.primary',
        textDecoration: 'none',
        '&:hover': { bgcolor: '#eaeef2' },
        '&.active': { bgcolor: colors.blueBg, color: colors.blue, fontWeight: 600 },
      }}
    >
      {item.label}
    </Box>
  );
}

export default function AppLayout() {
  const user = useCurrentUser();
  const navigate = useNavigate();
  const [search, setSearch] = useState('');
  const items = navFor(user?.roles ?? []);
  const role = primaryRole(user?.roles ?? []);

  const sections = (['main', 'admin', 'account'] as const)
    .map((section) => ({ section, items: items.filter((i) => i.section === section) }))
    .filter((s) => s.items.length > 0);

  return (
    <Box sx={{ display: 'flex', flexDirection: 'column', height: '100%' }}>
      <Box
        component="header"
        sx={{
          height: 48,
          flexShrink: 0,
          display: 'flex',
          alignItems: 'center',
          gap: 2,
          px: 2,
          borderBottom: 1,
          borderColor: 'divider',
        }}
      >
        <Typography
          component={NavLink}
          to="/"
          sx={{ fontWeight: 700, fontSize: 15, color: colors.blue, textDecoration: 'none', width: SIDEBAR_WIDTH - 16 }}
        >
          TrackFlow
        </Typography>
        <Box
          component="form"
          onSubmit={(e: React.FormEvent) => {
            e.preventDefault();
            navigate(`/tickets?q=${encodeURIComponent(search.trim())}`);
          }}
          sx={{ flex: 1, maxWidth: 360 }}
        >
          <InputBase
            value={search}
            onChange={(e) => setSearch(e.target.value)}
            placeholder="Search tickets or jump to a key (TMS-4)"
            inputProps={{ 'aria-label': 'Search tickets' }}
            sx={{
              width: '100%',
              fontSize: 13,
              px: 1,
              py: 0.25,
              border: 1,
              borderColor: 'divider',
              borderRadius: 1,
              bgcolor: colors.subtle,
            }}
          />
        </Box>
        <Box sx={{ ml: 'auto', display: 'flex', alignItems: 'center', gap: 1.5 }}>
          <Typography variant="body2">
            {user?.fullName}
            {role && (
              <Box component="span" sx={{ color: 'text.secondary', ml: 0.75 }}>
                · {humanize(role)}
              </Box>
            )}
          </Typography>
          <Button
            onClick={async () => {
              await signOut();
              navigate('/login');
            }}
          >
            Sign out
          </Button>
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
            py: 1.5,
            overflowY: 'auto',
          }}
        >
          {sections.map(({ section, items: sectionItems }) => (
            <Box key={section} sx={{ mb: 2 }}>
              {SECTION_TITLES[section] && (
                <Typography sx={{ px: 2.5, pb: 0.5, fontSize: 12, fontWeight: 600, color: 'text.secondary' }}>
                  {SECTION_TITLES[section]}
                </Typography>
              )}
              {sectionItems.map((item) => (
                <SidebarLink key={item.path} item={item} />
              ))}
            </Box>
          ))}
        </Box>

        <Box component="main" sx={{ flex: 1, overflow: 'auto', px: 3, py: 2.5 }}>
          <Box sx={{ maxWidth: 1200 }}>
            <Outlet />
          </Box>
        </Box>
      </Box>
    </Box>
  );
}
