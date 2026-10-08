import Box from '@mui/material/Box';
import Link from '@mui/material/Link';
import MenuItem from '@mui/material/MenuItem';
import Paper from '@mui/material/Paper';
import TextField from '@mui/material/TextField';
import Typography from '@mui/material/Typography';
import axios from 'axios';
import { useEffect, useState } from 'react';
import { useAppSelector } from '../app/hooks';
import PageHeader from '../components/PageHeader';
import { formatDateTime, humanize } from '../utils/format';
import { getPreferredPageSize, PAGE_SIZES, setPreferredPageSize } from '../utils/preferences';

const API_ORIGIN = import.meta.env.VITE_API_BASE_URL ?? '';

function Row({ label, children }: { label: string; children: React.ReactNode }) {
  return (
    <Box sx={{ display: 'grid', gridTemplateColumns: '180px 1fr', py: 0.75, borderTop: 1, borderColor: 'divider', fontSize: 14 }}>
      <Box sx={{ color: 'text.secondary' }}>{label}</Box>
      <Box>{children}</Box>
    </Box>
  );
}

export default function SettingsPage() {
  const auth = useAppSelector((s) => s.auth);
  const [pageSize, setPageSize] = useState(getPreferredPageSize());
  const [health, setHealth] = useState<string>('checking…');
  const [info, setInfo] = useState<{ cloud?: string; region?: string }>({});

  useEffect(() => {
    axios
      .get(`${API_ORIGIN}/actuator/health`)
      .then((r) => setHealth(r.data.status))
      .catch(() => setHealth('unreachable'));
    axios
      .get(`${API_ORIGIN}/actuator/info`)
      .then((r) => setInfo(r.data.app ?? {}))
      .catch(() => undefined);
  }, []);

  return (
    <>
      <PageHeader title="Settings" />
      <Box sx={{ display: 'grid', gap: 3, maxWidth: 640 }}>
        <Paper sx={{ px: 2, pt: 1.5, pb: 0.5 }}>
          <Typography variant="h2" sx={{ mb: 1 }}>
            Preferences
          </Typography>
          <Row label="Rows per page">
            <TextField
              select
              value={pageSize}
              onChange={(e) => {
                const size = Number(e.target.value);
                setPageSize(size);
                setPreferredPageSize(size);
              }}
              sx={{ width: 100 }}
            >
              {PAGE_SIZES.map((n) => (
                <MenuItem key={n} value={n}>
                  {n}
                </MenuItem>
              ))}
            </TextField>
          </Row>
        </Paper>

        <Paper sx={{ px: 2, pt: 1.5, pb: 0.5 }}>
          <Typography variant="h2" sx={{ mb: 1 }}>
            Session
          </Typography>
          <Row label="Signed in as">{auth.user?.email}</Row>
          <Row label="Roles">{auth.user?.roles.map(humanize).join(', ')}</Row>
          <Row label="Access token expires">
            {auth.expiresAt ? formatDateTime(new Date(auth.expiresAt).toISOString()) : '–'}
            <Typography variant="body2" color="text.secondary">
              Renewed automatically while you use the app.
            </Typography>
          </Row>
        </Paper>

        <Paper sx={{ px: 2, pt: 1.5, pb: 0.5 }}>
          <Typography variant="h2" sx={{ mb: 1 }}>
            System
          </Typography>
          <Row label="API status">
            <Box component="span" sx={{ color: health === 'UP' ? 'success.main' : health === 'unreachable' ? 'error.main' : undefined }}>
              {health}
            </Box>
          </Row>
          <Row label="Served from">
            <span className="mono">
              {info.cloud ?? '–'} / {info.region ?? '–'}
            </span>
          </Row>
          <Row label="API documentation">
            <Link href={`${API_ORIGIN}/swagger-ui.html`} target="_blank" rel="noreferrer">
              Swagger UI
            </Link>
            {' · '}
            <Link href={`${API_ORIGIN}/v3/api-docs`} target="_blank" rel="noreferrer">
              OpenAPI JSON
            </Link>
          </Row>
          <Row label="Frontend version">
            <span className="mono">{__APP_VERSION__}</span>
          </Row>
        </Paper>
      </Box>
    </>
  );
}
