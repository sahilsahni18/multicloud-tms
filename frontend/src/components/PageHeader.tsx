import Box from '@mui/material/Box';
import Link from '@mui/material/Link';
import Typography from '@mui/material/Typography';
import { Fragment } from 'react';
import { Link as RouterLink } from 'react-router-dom';

export interface Crumb {
  label: string;
  to?: string;
}

interface Props {
  title: React.ReactNode;
  breadcrumbs?: Crumb[];
  description?: React.ReactNode;
  actions?: React.ReactNode;
}

/** Breadcrumbs, a large title and actions on the right, as on Jira pages. */
export default function PageHeader({ title, breadcrumbs, description, actions }: Props) {
  return (
    <Box sx={{ mb: 3 }}>
      {breadcrumbs && breadcrumbs.length > 0 && (
        <Typography variant="body2" color="text.secondary" sx={{ mb: 1 }}>
          {breadcrumbs.map((crumb, i) => (
            <Fragment key={i}>
              {i > 0 && <Box component="span" sx={{ mx: 0.75 }}>/</Box>}
              {crumb.to ? (
                <Link component={RouterLink} to={crumb.to} color="inherit">
                  {crumb.label}
                </Link>
              ) : (
                crumb.label
              )}
            </Fragment>
          ))}
        </Typography>
      )}
      <Box sx={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', gap: 2 }}>
        <Typography variant="h1">{title}</Typography>
        {actions && <Box sx={{ display: 'flex', gap: 1, flexShrink: 0, alignItems: 'center' }}>{actions}</Box>}
      </Box>
      {description && (
        <Typography variant="body2" color="text.secondary" sx={{ mt: 0.5 }}>
          {description}
        </Typography>
      )}
    </Box>
  );
}
