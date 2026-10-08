const dateTime = new Intl.DateTimeFormat(undefined, {
  year: 'numeric',
  month: 'short',
  day: 'numeric',
  hour: '2-digit',
  minute: '2-digit',
});
const dateOnly = new Intl.DateTimeFormat(undefined, { year: 'numeric', month: 'short', day: 'numeric' });

export function formatDateTime(iso?: string | null): string {
  return iso ? dateTime.format(new Date(iso)) : '';
}

/** Dates without a time (due dates) are calendar days: parse as local midnight so they never shift a day. */
export function formatDate(iso?: string | null): string {
  if (!iso) return '';
  return iso.length === 10 ? dateOnly.format(new Date(`${iso}T00:00:00`)) : dateOnly.format(new Date(iso));
}

/** "3 min ago", "2 h ago", "5 d ago", then the date. */
export function timeAgo(iso?: string | null, now: number = Date.now()): string {
  if (!iso) return '';
  const seconds = Math.round((now - new Date(iso).getTime()) / 1000);
  if (seconds < 60) return 'just now';
  const minutes = Math.round(seconds / 60);
  if (minutes < 60) return `${minutes} min ago`;
  const hours = Math.round(minutes / 60);
  if (hours < 24) return `${hours} h ago`;
  const days = Math.round(hours / 24);
  if (days < 30) return `${days} d ago`;
  return formatDate(iso);
}

/** IN_PROGRESS -> "In progress", PROJECT_MANAGER -> "Project manager". */
export function humanize(value?: string | null): string {
  if (!value) return '';
  const words = value.toLowerCase().replace(/_/g, ' ');
  return words.charAt(0).toUpperCase() + words.slice(1);
}
