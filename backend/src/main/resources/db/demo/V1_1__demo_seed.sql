-- =====================================================================
-- Demo data for local / dev / QA. NOT loaded in prod: this folder is only
-- on the Flyway path when the `demo-data` profile is active.
--
-- All demo users share the password:  Password@123
-- Dates are relative to "now" so dashboards look live on any day.
-- =====================================================================

SET @now = UTC_TIMESTAMP(6);
SET @pw  = '$2a$12$qnetB5lTBlIeBRBAVaLmMuln7uN8ECqkCBbKuB8ZIcJxwSxN7pQXG';

-- ---------------------------------------------------------------------
-- Users (one per role, plus a second developer for productivity charts)
-- ---------------------------------------------------------------------
INSERT INTO users (id, email, password_hash, full_name) VALUES
    (1, 'admin@trackflow.dev', @pw, 'Alex Admin'),
    (2, 'pm@trackflow.dev',    @pw, 'Morgan Manager'),
    (3, 'dev@trackflow.dev',   @pw, 'Sam Developer'),
    (4, 'dev2@trackflow.dev',  @pw, 'Jordan Developer'),
    (5, 'user@trackflow.dev',  @pw, 'Casey User');

INSERT INTO user_roles (user_id, role_id)
SELECT u.id, r.id
FROM (SELECT 1 AS id, 'ADMIN' AS role
      UNION ALL SELECT 2, 'PROJECT_MANAGER'
      UNION ALL SELECT 3, 'DEVELOPER'
      UNION ALL SELECT 4, 'DEVELOPER'
      UNION ALL SELECT 5, 'USER') u
JOIN roles r ON r.name = u.role;

-- ---------------------------------------------------------------------
-- Projects and members
-- ---------------------------------------------------------------------
INSERT INTO projects (id, project_key, name, description, owner_id, next_ticket_number, created_by) VALUES
    (1, 'TMS', 'TrackFlow Platform', 'The ticket management application itself.', 2, 9, 2),
    (2, 'OPS', 'Cloud Operations',   'Infrastructure, CI/CD and multi-cloud deployment.', 1, 4, 1);

INSERT INTO project_members (project_id, user_id, created_by) VALUES
    (1, 2, 2), (1, 3, 2), (1, 4, 2), (1, 5, 2),
    (2, 1, 1), (2, 2, 1), (2, 3, 1), (2, 4, 1);

-- ---------------------------------------------------------------------
-- Tickets
-- ---------------------------------------------------------------------
INSERT INTO tickets (id, project_id, ticket_number, title, description, type, priority, status,
                     reporter_id, assignee_id, due_date, closed_at, created_at, created_by) VALUES
    (1,  1, 1, 'Login page with JWT authentication',
         'Users sign in with email and password and receive an access token.',
         'STORY', 'HIGH', 'CLOSED', 2, 3, NULL, @now - INTERVAL 9 DAY, @now - INTERVAL 12 DAY, 2),
    (2,  1, 2, 'Refresh token not rotated after use',
         'Calling /auth/refresh twice with the same token succeeds both times.',
         'BUG', 'CRITICAL', 'CLOSED', 5, 4, NULL, @now - INTERVAL 6 DAY, @now - INTERVAL 10 DAY, 5),
    (3,  1, 3, 'Add ticket filters to list page',
         'Filter by project, status, priority and assignee; filters combine.',
         'TASK', 'MEDIUM', 'IN_REVIEW', 2, 3, DATE(@now + INTERVAL 2 DAY), NULL, @now - INTERVAL 6 DAY, 2),
    (4,  1, 4, 'Dashboard counts include deleted tickets',
         'Open ticket total is higher than the list shows.',
         'BUG', 'HIGH', 'IN_PROGRESS', 5, 4, DATE(@now + INTERVAL 1 DAY), NULL, @now - INTERVAL 4 DAY, 5),
    (5,  1, 5, 'Ticket timeline merges comments and history',
         'Show comments and field changes in one chronological list.',
         'STORY', 'MEDIUM', 'OPEN', 2, 3, DATE(@now + INTERVAL 5 DAY), NULL, @now - INTERVAL 3 DAY, 2),
    (6,  1, 6, 'Typo on registration page',
         '"Pasword" should read "Password".',
         'BUG', 'LOW', 'OPEN', 5, NULL, NULL, NULL, @now - INTERVAL 2 DAY, 5),
    (7,  1, 7, 'Swagger docs for auth endpoints',
         'Document request/response bodies and the bearer scheme.',
         'TASK', 'MEDIUM', 'CLOSED', 2, 3, NULL, @now - INTERVAL 2 DAY, @now - INTERVAL 8 DAY, 2),
    (8,  1, 8, '500 error when filtering by assignee',
         'Selecting "Unassigned" in the assignee filter returns HTTP 500.',
         'BUG', 'CRITICAL', 'OPEN', 5, NULL, NULL, NULL, @now - INTERVAL 1 DAY, 5),
    (9,  2, 1, 'Create OpenTofu module for EKS',
         'Reusable module: cluster, one managed node group, IRSA.',
         'TASK', 'HIGH', 'IN_PROGRESS', 1, 4, DATE(@now + INTERVAL 3 DAY), NULL, @now - INTERVAL 5 DAY, 1),
    (10, 2, 2, 'Configure Traffic Manager health probes',
         'Priority routing across 4 regions, probe /actuator/health every 10 s.',
         'TASK', 'MEDIUM', 'OPEN', 1, NULL, NULL, NULL, @now - INTERVAL 2 DAY, 1),
    (11, 2, 3, 'Budget alerts on AWS and Azure',
         'US$5 alert on both clouds, email to the team.',
         'STORY', 'LOW', 'CLOSED', 1, 3, NULL, @now - INTERVAL 5 DAY, @now - INTERVAL 7 DAY, 1);

-- ---------------------------------------------------------------------
-- Ticket history: created, assigned, status transitions
-- ---------------------------------------------------------------------
INSERT INTO ticket_history (ticket_id, changed_by, change_type, changed_at)
SELECT id, reporter_id, 'CREATED', created_at FROM tickets;

INSERT INTO ticket_history (ticket_id, changed_by, change_type, field_name, old_value, new_value, changed_at)
SELECT t.id, p.owner_id, 'ASSIGNED', 'assignee', NULL, u.full_name, t.created_at + INTERVAL 1 HOUR
FROM tickets t
JOIN projects p ON p.id = t.project_id
JOIN users u ON u.id = t.assignee_id;

INSERT INTO ticket_history (ticket_id, changed_by, change_type, field_name, old_value, new_value, changed_at) VALUES
    (1,  3, 'STATUS_CHANGED', 'status', 'OPEN',        'IN_PROGRESS', @now - INTERVAL 11 DAY),
    (1,  3, 'STATUS_CHANGED', 'status', 'IN_PROGRESS', 'IN_REVIEW',   @now - INTERVAL 10 DAY),
    (1,  2, 'STATUS_CHANGED', 'status', 'IN_REVIEW',   'CLOSED',      @now - INTERVAL 9 DAY),
    (2,  4, 'STATUS_CHANGED', 'status', 'OPEN',        'IN_PROGRESS', @now - INTERVAL 9 DAY),
    (2,  4, 'STATUS_CHANGED', 'status', 'IN_PROGRESS', 'IN_REVIEW',   @now - INTERVAL 7 DAY),
    (2,  2, 'STATUS_CHANGED', 'status', 'IN_REVIEW',   'CLOSED',      @now - INTERVAL 6 DAY),
    (3,  3, 'STATUS_CHANGED', 'status', 'OPEN',        'IN_PROGRESS', @now - INTERVAL 5 DAY),
    (3,  3, 'STATUS_CHANGED', 'status', 'IN_PROGRESS', 'IN_REVIEW',   @now - INTERVAL 1 DAY),
    (4,  4, 'STATUS_CHANGED', 'status', 'OPEN',        'IN_PROGRESS', @now - INTERVAL 3 DAY),
    (4,  2, 'UPDATED',        'priority', 'MEDIUM',    'HIGH',        @now - INTERVAL 3 DAY),
    (7,  3, 'STATUS_CHANGED', 'status', 'OPEN',        'IN_PROGRESS', @now - INTERVAL 6 DAY),
    (7,  3, 'STATUS_CHANGED', 'status', 'IN_PROGRESS', 'IN_REVIEW',   @now - INTERVAL 3 DAY),
    (7,  2, 'STATUS_CHANGED', 'status', 'IN_REVIEW',   'CLOSED',      @now - INTERVAL 2 DAY),
    (9,  4, 'STATUS_CHANGED', 'status', 'OPEN',        'IN_PROGRESS', @now - INTERVAL 4 DAY),
    (11, 3, 'STATUS_CHANGED', 'status', 'OPEN',        'IN_PROGRESS', @now - INTERVAL 6 DAY),
    (11, 3, 'STATUS_CHANGED', 'status', 'IN_PROGRESS', 'IN_REVIEW',   @now - INTERVAL 5 DAY - INTERVAL 4 HOUR),
    (11, 1, 'STATUS_CHANGED', 'status', 'IN_REVIEW',   'CLOSED',      @now - INTERVAL 5 DAY);

-- ---------------------------------------------------------------------
-- Comments
-- ---------------------------------------------------------------------
INSERT INTO comments (ticket_id, author_id, body, created_at, created_by) VALUES
    (2, 5, 'Reproduced on Chrome and Firefox. Second refresh should return 401.', @now - INTERVAL 10 DAY, 5),
    (2, 4, 'Fixed: old token is revoked on rotation; reuse revokes the whole family.', @now - INTERVAL 7 DAY, 4),
    (3, 3, 'Ready for review. Filters are kept in the URL so they survive reload.', @now - INTERVAL 1 DAY, 3),
    (4, 4, 'Root cause: the count query does not exclude soft-deleted rows.', @now - INTERVAL 3 DAY, 4),
    (8, 5, 'Happens every time, see screenshot in the description.', @now - INTERVAL 1 DAY, 5),
    (9, 2, 'Keep node count at 1 for the free tier.', @now - INTERVAL 4 DAY, 2);

-- ---------------------------------------------------------------------
-- Activity feed
-- ---------------------------------------------------------------------
INSERT INTO activity_logs (actor_id, action, entity_type, entity_id, project_id, summary, created_at)
SELECT p.owner_id, 'PROJECT_CREATED', 'PROJECT', p.id, p.id,
       CONCAT('Created project ', p.project_key, ' - ', p.name), @now - INTERVAL 14 DAY
FROM projects p;

INSERT INTO activity_logs (actor_id, action, entity_type, entity_id, project_id, summary, created_at)
SELECT t.reporter_id, 'TICKET_CREATED', 'TICKET', t.id, t.project_id,
       CONCAT('Created ', p.project_key, '-', t.ticket_number, ': ', t.title), t.created_at
FROM tickets t
JOIN projects p ON p.id = t.project_id;

INSERT INTO activity_logs (actor_id, action, entity_type, entity_id, project_id, summary, created_at)
SELECT h.changed_by,
       CASE h.change_type WHEN 'ASSIGNED' THEN 'TICKET_ASSIGNED'
                          WHEN 'STATUS_CHANGED' THEN 'STATUS_CHANGED'
                          ELSE 'TICKET_UPDATED' END,
       'TICKET', t.id, t.project_id,
       CONCAT(p.project_key, '-', t.ticket_number, ': ', h.field_name, ' ',
              COALESCE(h.old_value, '-'), ' -> ', h.new_value),
       h.changed_at
FROM ticket_history h
JOIN tickets t ON t.id = h.ticket_id
JOIN projects p ON p.id = t.project_id
WHERE h.change_type <> 'CREATED';
