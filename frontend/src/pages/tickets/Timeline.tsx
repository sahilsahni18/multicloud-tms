import Box from '@mui/material/Box';
import Button from '@mui/material/Button';
import Link from '@mui/material/Link';
import TextField from '@mui/material/TextField';
import Typography from '@mui/material/Typography';
import { useState } from 'react';
import {
  useAddCommentMutation,
  useDeleteCommentMutation,
  useEditCommentMutation,
  useGetTimelineQuery,
} from '../../api/api';
import { errorMessage } from '../../api/http';
import type { Comment, HistoryEntry, TicketStatus } from '../../api/types';
import { useCurrentUser } from '../../app/hooks';
import { ErrorBanner, Loading } from '../../components/Feedback';
import { StatusLabel } from '../../components/Labels';
import UserAvatar from '../../components/UserAvatar';
import { colors } from '../../theme';
import { formatDate, formatDateTime, humanize, timeAgo } from '../../utils/format';

function describeChange(h: HistoryEntry): React.ReactNode {
  const show = (v?: string) => {
    if (!v) return 'None';
    if (h.field === 'dueDate') return formatDate(v);
    return h.field === 'priority' || h.field === 'type' ? humanize(v) : v;
  };
  switch (h.changeType) {
    case 'CREATED':
      return 'created the ticket';
    case 'ASSIGNED':
      return h.newValue ? (
        <>
          assigned the ticket to <b>{h.newValue}</b>
        </>
      ) : (
        'unassigned the ticket'
      );
    case 'STATUS_CHANGED':
      return (
        <>
          changed the status{' '}
          {h.oldValue && <StatusLabel status={h.oldValue as TicketStatus} />}
          <Box component="span" sx={{ mx: 0.75, color: 'text.secondary' }}>→</Box>
          {h.newValue && <StatusLabel status={h.newValue as TicketStatus} />}
        </>
      );
    default:
      return h.field === 'description' ? (
        'updated the description'
      ) : (
        <>
          changed the {h.field === 'dueDate' ? 'due date' : h.field} from <b>{show(h.oldValue)}</b> to{' '}
          <b>{show(h.newValue)}</b>
        </>
      );
  }
}

function CommentItem({ comment }: { comment: Comment }) {
  const [editing, setEditing] = useState(false);
  const [body, setBody] = useState(comment.body);
  const [edit, editState] = useEditCommentMutation();
  const [remove] = useDeleteCommentMutation();

  return (
    <Box sx={{ display: 'flex', gap: 1.5, py: 1.5 }}>
      <UserAvatar name={comment.author.fullName} size={32} />
      <Box sx={{ flex: 1, minWidth: 0 }}>
        <Box sx={{ display: 'flex', gap: 1, alignItems: 'baseline', fontSize: 14 }}>
          <Box component="span" sx={{ fontWeight: 600 }}>
            {comment.author.fullName}
          </Box>
          <Box component="span" sx={{ color: 'text.secondary', fontSize: 13 }} title={formatDateTime(comment.createdAt)}>
            {timeAgo(comment.createdAt)}
            {comment.editedAt && ' · edited'}
          </Box>
        </Box>
        {editing ? (
          <Box sx={{ mt: 1, display: 'grid', gap: 1 }}>
            {editState.error && <Typography color="error" variant="body2">{errorMessage(editState.error)}</Typography>}
            <TextField value={body} onChange={(e) => setBody(e.target.value)} multiline minRows={3} autoFocus />
            <Box sx={{ display: 'flex', gap: 1 }}>
              <Button
                variant="contained"
                disabled={!body.trim() || editState.isLoading}
                onClick={async () => {
                  const ok = await edit({ id: comment.id, ticketId: comment.ticketId, body: body.trim() })
                    .unwrap()
                    .then(() => true)
                    .catch(() => false);
                  if (ok) setEditing(false);
                }}
              >
                Save
              </Button>
              <Button
                variant="text"
                sx={{ bgcolor: 'transparent' }}
                onClick={() => {
                  setBody(comment.body);
                  setEditing(false);
                }}
              >
                Cancel
              </Button>
            </Box>
          </Box>
        ) : (
          <>
            <Typography sx={{ mt: 0.5, whiteSpace: 'pre-wrap', fontSize: 14, wordBreak: 'break-word' }}>{comment.body}</Typography>
            {comment.canModify && (
              <Box sx={{ display: 'flex', gap: 1.5, mt: 0.5 }}>
                <Link component="button" variant="body2" color="text.secondary" onClick={() => setEditing(true)} sx={{ fontWeight: 500 }}>
                  Edit
                </Link>
                <Link
                  component="button"
                  variant="body2"
                  color="text.secondary"
                  sx={{ fontWeight: 500 }}
                  onClick={() => {
                    if (window.confirm('Delete this comment?')) remove({ id: comment.id, ticketId: comment.ticketId });
                  }}
                >
                  Delete
                </Link>
              </Box>
            )}
          </>
        )}
      </Box>
    </Box>
  );
}

/** Comments and field changes in one list, oldest first, with the comment box on top. */
export default function Timeline({ ticketId, canComment }: { ticketId: number; canComment: boolean }) {
  const me = useCurrentUser();
  const { data, error, isLoading } = useGetTimelineQuery(ticketId);
  const [addComment, addState] = useAddCommentMutation();
  const [body, setBody] = useState('');
  const [focused, setFocused] = useState(false);

  return (
    <Box>
      <Typography variant="h3" sx={{ mb: 1.5 }}>
        Activity
      </Typography>

      {canComment && (
        <Box
          component="form"
          sx={{ display: 'flex', gap: 1.5, mb: 1 }}
          onSubmit={async (e: React.FormEvent) => {
            e.preventDefault();
            const ok = await addComment({ ticketId, body: body.trim() })
              .unwrap()
              .then(() => true)
              .catch(() => false);
            if (ok) {
              setBody('');
              setFocused(false);
            }
          }}
        >
          <UserAvatar name={me?.fullName} size={32} />
          <Box sx={{ flex: 1, display: 'grid', gap: 1 }}>
            {addState.error && <Typography color="error" variant="body2">{errorMessage(addState.error)}</Typography>}
            <TextField
              placeholder="Add a comment…"
              value={body}
              onChange={(e) => setBody(e.target.value)}
              onFocus={() => setFocused(true)}
              multiline
              minRows={focused ? 3 : 1}
            />
            {(focused || body) && (
              <Box sx={{ display: 'flex', gap: 1 }}>
                <Button type="submit" variant="contained" disabled={!body.trim() || addState.isLoading}>
                  Save
                </Button>
                <Button
                  variant="text"
                  sx={{ bgcolor: 'transparent' }}
                  onClick={() => {
                    setBody('');
                    setFocused(false);
                  }}
                >
                  Cancel
                </Button>
              </Box>
            )}
          </Box>
        </Box>
      )}

      <ErrorBanner error={error} />
      {isLoading && <Loading />}
      {data &&
        [...data].reverse().map((entry) =>
          entry.kind === 'COMMENT' && entry.comment ? (
            <CommentItem key={`c${entry.comment.id}`} comment={entry.comment} />
          ) : entry.change ? (
            <Box key={`h${entry.change.id}`} sx={{ display: 'flex', gap: 1.5, py: 1, alignItems: 'flex-start' }}>
              <UserAvatar name={entry.change.changedBy?.fullName ?? 'System'} size={32} />
              <Box sx={{ fontSize: 14, pt: 0.75, lineHeight: '20px' }}>
                <Box component="span" sx={{ fontWeight: 600 }}>
                  {entry.change.changedBy?.fullName ?? 'System'}
                </Box>{' '}
                {describeChange(entry.change)}
                <Box component="span" sx={{ color: colors.muted, fontSize: 13, ml: 1 }} title={formatDateTime(entry.change.changedAt)}>
                  {timeAgo(entry.change.changedAt)}
                </Box>
              </Box>
            </Box>
          ) : null,
        )}
    </Box>
  );
}
