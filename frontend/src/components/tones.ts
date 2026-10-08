import type { TicketStatus } from '../api/types';

/** Lozenge colours (text on background), Atlassian-style. */
export const LOZENGE = {
  grey: { color: '#44546F', bg: '#DCDFE4' },
  blue: { color: '#0055CC', bg: '#CCE0FF' },
  purple: { color: '#5E4DB2', bg: '#DFD8FD' },
  green: { color: '#216E4E', bg: '#BAF3DB' },
  red: { color: '#AE2E24', bg: '#FFD5D2' },
  orange: { color: '#A54800', bg: '#FEDEC8' },
};
export type LozengeTone = keyof typeof LOZENGE;

export const STATUS_TONE: Record<TicketStatus, LozengeTone> = {
  OPEN: 'grey',
  IN_PROGRESS: 'blue',
  IN_REVIEW: 'purple',
  CLOSED: 'green',
};
