// k6 load test for the TrackFlow API.
//
// One step = a constant arrival rate (RATE requests per second) for DURATION.
// Every iteration is one API request, picked with weights that mimic real use:
//   35% ticket list (random page, sometimes filtered)   10% dashboard
//   20% ticket detail                                    10% projects
//   15% ticket timeline (comments + history)              5% text search
//                                                          5% add a comment (write)
// Users log in once in setup() (the auth endpoints are rate limited per IP),
// and their tokens are reused for the whole step.
//
//   k6 run -e BASE_URL=http://localhost:8080 -e RATE=50 -e DURATION=60s trackflow.js
import http from 'k6/http';
import { check } from 'k6';

const BASE = (__ENV.BASE_URL || 'http://localhost:8080') + '/api/v1';
const RATE = Number(__ENV.RATE || 20);
const DURATION = __ENV.DURATION || '60s';

export const options = {
  scenarios: {
    step: {
      executor: 'constant-arrival-rate',
      rate: RATE,
      timeUnit: '1s',
      duration: DURATION,
      preAllocatedVUs: Math.min(Math.max(RATE * 2, 20), 600),
      maxVUs: 1500,
    },
  },
  summaryTrendStats: ['avg', 'med', 'p(90)', 'p(95)', 'p(99)', 'max'],
  discardResponseBodies: false,
};

const USERS = ['admin@trackflow.dev', 'pm@trackflow.dev', 'dev@trackflow.dev', 'user@trackflow.dev'];

export function setup() {
  const sessions = USERS.map((email) => {
    const res = http.post(`${BASE}/auth/login`, JSON.stringify({ email, password: 'Password@123' }),
      { headers: { 'Content-Type': 'application/json' } });
    check(res, { 'login ok': (r) => r.status === 200 });
    return { email, token: res.json('accessToken') };
  });
  const admin = { headers: { Authorization: `Bearer ${sessions[0].token}` } };
  const ids = [];
  for (let page = 0; page < 10; page++) {
    const res = http.get(`${BASE}/tickets?size=100&page=${page}`, admin);
    (res.json('content') || []).forEach((t) => ids.push(t.id));
  }
  return { sessions, ids };
}

const pick = (arr) => arr[Math.floor(Math.random() * arr.length)];
const STATUSES = ['OPEN', 'IN_PROGRESS', 'IN_REVIEW', 'CLOSED'];
const PRIORITIES = ['LOW', 'MEDIUM', 'HIGH', 'CRITICAL'];
const WORDS = ['login', 'dashboard', 'export', 'search', 'deploy'];

export default function (data) {
  const session = pick(data.sessions);
  const params = { headers: { Authorization: `Bearer ${session.token}`, 'Content-Type': 'application/json' } };
  const id = pick(data.ids);
  const r = Math.random();
  let res;
  if (r < 0.35) {
    const filter = Math.random() < 0.5 ? `&status=${pick(STATUSES)}&priority=${pick(PRIORITIES)}` : '';
    params.tags = { name: 'list' };
    res = http.get(`${BASE}/tickets?size=20&page=${Math.floor(Math.random() * 20)}${filter}`, params);
  } else if (r < 0.55) {
    params.tags = { name: 'detail' };
    res = http.get(`${BASE}/tickets/${id}`, params);
  } else if (r < 0.70) {
    params.tags = { name: 'timeline' };
    res = http.get(`${BASE}/tickets/${id}/timeline`, params);
  } else if (r < 0.80) {
    params.tags = { name: 'dashboard' };
    res = http.get(`${BASE}/dashboard`, params);
  } else if (r < 0.90) {
    params.tags = { name: 'projects' };
    res = http.get(`${BASE}/projects`, params);
  } else if (r < 0.95) {
    params.tags = { name: 'search' };
    res = http.get(`${BASE}/tickets?q=${pick(WORDS)}&size=20`, params);
  } else {
    params.tags = { name: 'comment' };
    res = http.post(`${BASE}/tickets/${id}/comments`, JSON.stringify({ body: 'k6 load test comment' }), params);
  }
  // 403/404 are valid answers for tickets a role may not see; only 5xx and timeouts are failures.
  check(res, { 'no server error': (x) => x.status > 0 && x.status < 500 });
}

export function handleSummary(data) {
  const m = data.metrics;
  const v = (name, stat) => (m[name] && m[name].values[stat] !== undefined ? m[name].values[stat] : null);
  const result = {
    rate: RATE,
    duration: DURATION,
    requests: v('http_reqs', 'count'),
    rps: v('http_reqs', 'rate'),
    p50: v('http_req_duration', 'med'),
    p95: v('http_req_duration', 'p(95)'),
    p99: v('http_req_duration', 'p(99)'),
    max: v('http_req_duration', 'max'),
    failed: v('http_req_failed', 'rate'),
    serverErrors: 1 - (v('checks', 'rate') ?? 1),
    dropped: v('dropped_iterations', 'count') || 0,
    vusMax: v('vus_max', 'max'),
  };
  return {
    stdout: JSON.stringify(result) + '\n',
    [`/results/step-${RATE}.json`]: JSON.stringify(result, null, 2),
  };
}
