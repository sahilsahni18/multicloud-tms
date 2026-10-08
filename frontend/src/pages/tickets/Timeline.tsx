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
import type { Comment, HistoryEntry } from '../../api/types';
import { ErrorBanner, Loading } from '../../components/Feedback';
import { colors } from '../../theme';
import { formatDate, formatDateTime, humanize, timeAgo } from '../../utils/format';

function describeChange(h: HistoryEntry): React.ReactNode {
  const show = (v?: string) => {
    if (!v) return 'none';
    if (h.field === 'dueDate') return formatDate(v);
    return h.field === 'status' || h.field === 'priority' || h.field === 'type' ? humanize(v) : v;
  };
  switch (h.changeType) {
    case 'CREATED':
      return 'created the ticket';
    case 'ASSIGNED':
      return h.newValue ? (
        <>
          assigned it to <b>{h.newValue}</b>
        </>
      ) : (
        'removed the assignee'
      );
    case 'STATUS_CHANGED':
      return (
        <>
          moved it from <b>{show(h.oldValue)}</b> to <b>{show(h.newValue)}</b>
        </>
      );
    default:
      return h.field === 'description' ? (
        'edited the description'
      ) : (
        <>
          changed {h.field === 'dueDate' ? 'due date' : h.field} from <b>{show(h.oldValue)}</b> to <b>{show(h.newValue)}</b>
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
    <Box sx={{ border: 1, borderColor: 'divider', borderRadius: 1, my: 1 }}>
      <Box
        sx={{
          display: 'flex',
          gap: 1,
          alignItems: 'baseline',
          px: 1.5,
          py: 0.75,
          bgcolor: colors.subtle,
          borderBottom: 1,
          borderColor: 'divider',
          fontSize: 13,
        }}
      >
        <b>{comment.author.fullName}</b>
        <Box component="span" sx={{ color: 'text.secondary' }} title={formatDateTime(comment.createdAt)}>
          commented {timeAgo(comment.createdAt)}
          {comment.editedAt && ' (edited)'}
        </Box>
        {comment.canModify && !editing && (
          <Box sx={{ ml: 'auto', display: 'flex', gap: 1.5 }}>
            <Link component="button" variant="body2" onClick={() => setEditing(true)}>
              Edit
            </Link>
            <Link
              component="button"
              variant="body2"
              color="error"
              onClick={() => {
                if (window.confirm('Delete this comment?')) remove({ id: comment.id, ticketId: comment.ticketId });
              }}
            >
              Delete
            </Link>
          </Box>
        )}
      </Box>
      {editing ? (
        <Box sx={{ p: 1.5, display: 'grid', gap: 1 }}>
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
        <Typography sx={{ px: 1.5, py: 1, whiteSpace: 'pre-wrap', fontSize: 14 }}>{comment.body}</Typography>
      )}
    </Box>
  );
}

/** Comments and field changes in one list, oldest first, with the comment box at the end. */
export default function Timeline({ ticketId, canComment }: { ticketId: number; canComment: boolean }) {
  const { data, error, isLoading } = useGetTimelineQuery(ticketId);
  const [addComment, addState] = useAddCommentMutation();
  const [body, setBody] = useState('');

  return (
    <Box>
      <Typography variant="h3" sx={{ mb: 1 }}>
        Activity
      </Typography>
      <ErrorBanner error={error} />
      {isLoading && <Loading />}
      {data?.map((entry) =>
        entry.kind === 'COMMENT' && entry.comment ? (
          <CommentItem key={`c${entry.comment.id}`} comment={entry.comment} />
        ) : entry.change ? (
          <Box
            key={`h${entry.change.id}`}
            sx={{ fontSize: 13, color: 'text.secondary', py: 0.5, pl: 1.5, borderLeft: 2, borderColor: 'divider', ml: 1 }}
          >
            <Box component="span" sx={{ color: 'text.primary', fontWeight: 500 }}>
              {entry.change.changedBy?.fullName ?? 'System'}
            </Box>{' '}
            {describeChange(entry.change)}{' '}
            <span title={formatDateTime(entry.change.changedAt)}>· {timeAgo(entry.change.changedAt)}</span>
          </Box>
        ) : null,
      )}

      {canComment && (
        <Box
          component="form"
          sx={{ mt: 2, display: 'grid', gap: 1 }}
          onSubmit={async (e: React.FormEvent) => {
            e.preventDefault();
            const ok = await addComment({ ticketId, body: body.trim() })
              .unwrap()
              .then(() => true)
              .catch(() => false);
            if (ok) setBody('');
          }}
        >
          {addState.error && <Typography color="error" variant="body2">{errorMessage(addState.error)}</Typography>}
          <TextField
            placeholder="Leave a comment"
            value={body}
            onChange={(e) => setBody(e.target.value)}
            multiline
            minRows={3}
          />
          <Box>
            <Button type="submit" variant="contained" disabled={!body.trim() || addState.isLoading}>
              Comment
            </Button>
          </Box>
        </Box>
      )}
    </Box>
  );
}
