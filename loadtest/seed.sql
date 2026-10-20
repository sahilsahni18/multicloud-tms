-- Load-test data on top of the demo seed: 3,000 tickets (1,500 per project),
-- 3 comments and a CREATED history row per ticket. Run once against a test
-- database after the backend has applied its Flyway migrations.
SET SESSION cte_max_recursion_depth = 100000;

INSERT INTO tickets (project_id, ticket_number, title, description, type, priority, status,
                     reporter_id, assignee_id, closed_at, created_at, created_by)
WITH RECURSIVE seq (n) AS (SELECT 1 UNION ALL SELECT n + 1 FROM seq WHERE n < 1500)
SELECT p.id,
       p.next_ticket_number + s.n - 1,
       CONCAT('Load test ', LOWER(p.project_key), ' ticket ', s.n, ' ',
              ELT(1 + s.n % 5, 'login', 'dashboard', 'export', 'search', 'deploy')),
       'Generated for load testing.',
       ELT(1 + s.n % 3, 'BUG', 'TASK', 'STORY'),
       ELT(1 + s.n % 4, 'LOW', 'MEDIUM', 'HIGH', 'CRITICAL'),
       ELT(1 + s.n % 4, 'OPEN', 'IN_PROGRESS', 'IN_REVIEW', 'CLOSED'),
       2 + s.n % 4,
       IF(s.n % 5 = 0, NULL, 3 + s.n % 2),
       IF(s.n % 4 = 3, NOW(6), NULL),
       NOW(6) - INTERVAL s.n MINUTE,
       2
FROM seq s
CROSS JOIN projects p;

UPDATE projects SET next_ticket_number = next_ticket_number + 1500;

INSERT INTO comments (ticket_id, author_id, body, created_at, created_by)
SELECT t.id, 2 + (t.id + k.k) % 3, CONCAT('Load test comment ', k.k), t.created_at + INTERVAL k.k MINUTE, 2
FROM tickets t
CROSS JOIN (SELECT 1 AS k UNION ALL SELECT 2 UNION ALL SELECT 3) k
WHERE t.title LIKE 'Load test %';

INSERT INTO ticket_history (ticket_id, changed_by, change_type, changed_at)
SELECT t.id, t.reporter_id, 'CREATED', t.created_at
FROM tickets t
WHERE t.title LIKE 'Load test %';
