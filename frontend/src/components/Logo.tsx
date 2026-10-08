import Box from '@mui/material/Box';
import { colors } from '../theme';

/** Product mark: blue square with a white "T", plus the wordmark. */
export default function Logo({ size = 24, withText = true }: { size?: number; withText?: boolean }) {
  return (
    <Box component="span" sx={{ display: 'inline-flex', alignItems: 'center', gap: 1 }}>
      <svg width={size} height={size} viewBox="0 0 16 16" aria-hidden="true">
        <rect width="16" height="16" rx="3.5" fill={colors.blue} />
        <path d="M4 5h8M8 5v7" stroke="#fff" strokeWidth="2" strokeLinecap="round" />
      </svg>
      {withText && (
        <Box component="span" sx={{ fontWeight: 700, fontSize: size * 0.7, color: colors.text, letterSpacing: '-0.01em' }}>
          TrackFlow
        </Box>
      )}
    </Box>
  );
}
