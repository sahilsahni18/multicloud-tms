import PersonOutlineIcon from '@mui/icons-material/PersonOutline';
import Box from '@mui/material/Box';
import Tooltip from '@mui/material/Tooltip';

const PALETTE = ['#0C66E4', '#1F845A', '#6E5DC6', '#C9372C', '#A54800', '#227D9B', '#943D73', '#5B7F24'];

function colorFor(name: string): string {
  let hash = 0;
  for (const ch of name) hash = (hash * 31 + ch.charCodeAt(0)) | 0;
  return PALETTE[Math.abs(hash) % PALETTE.length];
}

function initials(name: string): string {
  const parts = name.trim().split(/\s+/);
  return ((parts[0]?.[0] ?? '') + (parts.length > 1 ? parts[parts.length - 1][0] : '')).toUpperCase();
}

interface Props {
  name?: string | null;
  size?: number;
  /** Show the name next to the circle. */
  withName?: boolean;
  tooltip?: boolean;
}

/** Initials in a coloured circle; a grey outline for "unassigned". */
export default function UserAvatar({ name, size = 24, withName, tooltip = true }: Props) {
  const circle = name ? (
    <Box
      component="span"
      sx={{
        width: size,
        height: size,
        borderRadius: '50%',
        bgcolor: colorFor(name),
        color: '#fff',
        fontSize: size * 0.42,
        fontWeight: 600,
        display: 'inline-flex',
        alignItems: 'center',
        justifyContent: 'center',
        flexShrink: 0,
        verticalAlign: 'middle',
      }}
    >
      {initials(name)}
    </Box>
  ) : (
    <Box
      component="span"
      sx={{
        width: size,
        height: size,
        borderRadius: '50%',
        bgcolor: '#DCDFE4',
        color: '#626F86',
        display: 'inline-flex',
        alignItems: 'center',
        justifyContent: 'center',
        flexShrink: 0,
        verticalAlign: 'middle',
      }}
    >
      <PersonOutlineIcon sx={{ fontSize: size * 0.7 }} />
    </Box>
  );

  if (withName) {
    return (
      <Box component="span" sx={{ display: 'inline-flex', alignItems: 'center', gap: 1, minWidth: 0 }}>
        {circle}
        <Box component="span" sx={{ overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap', color: name ? undefined : 'text.secondary' }}>
          {name ?? 'Unassigned'}
        </Box>
      </Box>
    );
  }
  return tooltip ? <Tooltip title={name ?? 'Unassigned'}>{circle}</Tooltip> : circle;
}

/** Square project avatar with the first letter of the key. */
export function ProjectAvatar({ projectKey, size = 24 }: { projectKey: string; size?: number }) {
  return (
    <Box
      component="span"
      sx={{
        width: size,
        height: size,
        borderRadius: '4px',
        bgcolor: colorFor(projectKey),
        color: '#fff',
        fontSize: size * 0.5,
        fontWeight: 700,
        display: 'inline-flex',
        alignItems: 'center',
        justifyContent: 'center',
        flexShrink: 0,
      }}
    >
      {projectKey[0]}
    </Box>
  );
}
