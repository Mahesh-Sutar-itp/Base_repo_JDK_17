// Load test for the Trebol POC, run by ECS Fargate tasks (see ../loadgen.tf, docs/scalability-plan.md).
//
// Three load models (LOAD_MODEL):
//
// LOAD_MODEL=burst (default): the POC target. About TOTAL_REQUESTS (10,000) requests in a 5-minute
//   window, with one short burst at PEAK_RATE (2,500 req/s) in the middle:
//     warm-up    WARMUP_S (60 s) at WARMUP_RATE (100 req/s), NOT counted: lets the JVMs compile the hot
//                code paths, as real traffic would before a peak (set WARMUP_S=0 to skip)
//     background BG_RATE (10 req/s) for the whole 5-minute window
//     admins     ADMIN_RATE (0.05) product life cycles per second (5 requests each) for the window
//     burst      at BURST_AT_S (150 s) into the window: 1 s ramp to PEAK_RATE, hold, 1 s ramp down;
//                the hold is worked out so the window adds up to TOTAL_REQUESTS
//   Each part is its own k6 scenario, so the summary gives the burst's own p95 and error rate
//   (thresholds on {scenario:burst}). The pass test is the p95 at the load balancer (report.sh, the
//   minute with the burst, which is ~90% burst traffic); k6's figure adds CloudFront and the NAT hop,
//   so it is a stricter cross-check. Repeat with a higher PEAK_RATE / TOTAL_REQUESTS to find the
//   breaking point.
//
// LOAD_MODEL=rate: REQUESTS PER SECOND, in sustained steps, to measure capacity.
//   k6 starts RATE_STEPS[i] shopper requests per second no matter how slow the answers get, so a slow
//   system shows up as rising latency, errors and "dropped_iterations" (k6 ran out of VUs).
//   Default steps 250 -> 5,000 req/s, each STEP_RAMP_S (30 s) ramp + STEP_HOLD_S (150 s) hold = 30 min.
//   Admins write at a fixed, low ADMIN_RATE (product life cycles per second, 5 requests each):
//   every product write clears the product-list cache of the task that took it, so non-stop admin
//   writes (the old model) kept that cache cold and sent ~0.9 DB queries per request to the database.
//   Read results per minute from CloudWatch with report.sh (the k6 summary is over the whole run).
//
// LOAD_MODEL=vus: CONCURRENT REQUESTS (the earlier model), described below.
//
// Concurrent-requests model: CONCURRENT REQUESTS, not unique users.
// Every virtual user (VU) sends its next request as soon as the previous one is answered (no think
// time), so each VU always has exactly one request in flight: N VUs = N concurrent requests.
// No per-VU sign-up: a small pool of shopper accounts is created and logged in once, in setup(),
// and the VUs share their tokens. A fixed share of the VUs (ADMIN_VUS) runs the product life cycle.
//
// Load steps (PEAK_CONCURRENCY = 5000 by default), 20 minutes in total:
//   0-2 min   ramp to 20% of peak    then hold 3 min
//   5-7 min   ramp to 50% of peak    then hold 3 min
//   10-13 min ramp to 100% of peak   then hold 6 min
//   19-20 min ramp down
//
// Busy server: a 503 is retried after its Retry-After (max 3 times), like a real client would.
// Only a request that still fails after the retries counts as failed.
//
// Several tasks share the load with k6 execution segments (K6_ARGS, set per task at run time), so
// the peak is the TOTAL over all tasks. INSTANCE keeps account names unique across tasks.
//
// Environment: BASE_URL (required), ADMIN_PASSWORD (required), SHOPPER_ACCOUNTS, RUN_ID, INSTANCE,
//   burst model: TOTAL_REQUESTS, PEAK_RATE, BG_RATE, BURST_AT_S, WARMUP_S, WARMUP_RATE, ADMIN_RATE
//   rate model:  LOAD_MODEL=rate, RATE_STEPS (comma list, req/s), STEP_RAMP_S, STEP_HOLD_S, ADMIN_RATE
//   vus model:   LOAD_MODEL=vus, PEAK_CONCURRENCY, ADMIN_VUS

import http from 'k6/http';
import { check, sleep, fail } from 'k6';
import exec from 'k6/execution';
import { Counter, Rate } from 'k6/metrics';

const BASE = __ENV.BASE_URL;
const ADMIN_PASSWORD = __ENV.ADMIN_PASSWORD;
const MODEL = (__ENV.LOAD_MODEL || 'burst').toLowerCase();
// vus model
const PEAK = Number(__ENV.PEAK_CONCURRENCY || 5000);
const ADMINS = Number(__ENV.ADMIN_VUS || 50);
// rate model
const RATE_STEPS = (__ENV.RATE_STEPS || '250,500,1000,1500,2000,2500,3000,3500,4000,5000')
  .split(',').map((s) => Number(s.trim())).filter((n) => n > 0);
const STEP_RAMP_S = Number(__ENV.STEP_RAMP_S || 30);
const STEP_HOLD_S = Number(__ENV.STEP_HOLD_S || 150);
// burst model
const TOTAL_REQUESTS = Number(__ENV.TOTAL_REQUESTS || 10000);
const PEAK_RATE_BURST = Number(__ENV.PEAK_RATE || 2500);
const BG_RATE = Number(__ENV.BG_RATE || 10);
const WINDOW_S = 300;
const BURST_AT_S = Number(__ENV.BURST_AT_S || 150);
const WARMUP_S = Number(__ENV.WARMUP_S ?? 60);
const WARMUP_RATE = Number(__ENV.WARMUP_RATE || 100);
// Both arrival-rate models. Product lists change rarely in this shop, so the default is 3 product life
// cycles per minute (12 writes/min over all tasks: each task's list cache is cleared about once per
// 30 s TTL anyway). ADMIN_RATE=2 gives a write-heavy variant.
const ADMIN_RATE = Number(__ENV.ADMIN_RATE || 0.05);
const ACCOUNTS = Number(__ENV.SHOPPER_ACCOUNTS || 20);
const RUN_ID = __ENV.RUN_ID || Date.now().toString(36);
const INSTANCE = __ENV.INSTANCE || '0';
const MAX_RETRIES = 3;

const retried503 = new Counter('retried_503');
const failedAfterRetries = new Rate('failed_after_retries');
const unexpected = new Counter('unexpected_status');

// 503 is an expected, retryable answer here, so k6's own http_req_failed does not count it;
// failed_after_retries is the real error rate.
http.setResponseCallback(http.expectedStatuses({ min: 200, max: 399 }, 503));

function steps(peak) {
  const p = (f) => Math.max(1, Math.round(peak * f));
  return [
    { duration: '2m', target: p(0.2) },
    { duration: '3m', target: p(0.2) },
    { duration: '2m', target: p(0.5) },
    { duration: '3m', target: p(0.5) },
    { duration: '3m', target: peak },
    { duration: '6m', target: peak },
    { duration: '1m', target: 0 },
  ];
}

// Rate model: ramp to each step, hold it, then stop
function rateStages() {
  const s = [];
  for (const r of RATE_STEPS) {
    s.push({ duration: `${STEP_RAMP_S}s`, target: r }, { duration: `${STEP_HOLD_S}s`, target: r });
  }
  s.push({ duration: '30s', target: 0 });
  return s;
}
const PEAK_RATE = Math.max(...RATE_STEPS);
const RATE_TOTAL_S = RATE_STEPS.length * (STEP_RAMP_S + STEP_HOLD_S) + 30;

// Burst model: the burst's hold time makes the 5-minute window add up to TOTAL_REQUESTS.
// A 1 s ramp up + hold + 1 s ramp down sends PEAK_RATE x (hold + 1) requests.
const BURST_REQUESTS = Math.max(PEAK_RATE_BURST, TOTAL_REQUESTS - BG_RATE * WINDOW_S - ADMIN_RATE * 5 * WINDOW_S);
const BURST_HOLD_MS = Math.max(200, Math.round((BURST_REQUESTS / PEAK_RATE_BURST - 1) * 1000));

function burstScenarios() {
  const s = {
    background: {
      executor: 'constant-arrival-rate', exec: 'shopper', rate: BG_RATE, timeUnit: '1s',
      startTime: `${WARMUP_S}s`, duration: `${WINDOW_S}s`, preAllocatedVUs: 20, maxVUs: 100,
    },
    // every VU the burst can need is created before it starts (creating VUs mid-burst is slow);
    // PEAK_RATE VUs allow an average answer time of 1 s, beyond that k6 reports dropped_iterations
    burst: {
      executor: 'ramping-arrival-rate', exec: 'shopper', startRate: 0, timeUnit: '1s',
      startTime: `${WARMUP_S + BURST_AT_S}s`, preAllocatedVUs: PEAK_RATE_BURST, maxVUs: PEAK_RATE_BURST,
      stages: [
        { duration: '1s', target: PEAK_RATE_BURST },
        { duration: `${BURST_HOLD_MS}ms`, target: PEAK_RATE_BURST },
        { duration: '1s', target: 0 },
      ],
    },
    admins: {
      executor: 'constant-arrival-rate', exec: 'admin', rate: Math.max(1, Math.round(ADMIN_RATE * 60)),
      timeUnit: '1m', startTime: `${WARMUP_S}s`, duration: `${WINDOW_S}s`, preAllocatedVUs: 5, maxVUs: 30,
    },
  };
  if (WARMUP_S > 0) {
    s.warmup = {
      executor: 'constant-arrival-rate', exec: 'shopper', rate: WARMUP_RATE, timeUnit: '1s',
      duration: `${WARMUP_S}s`, preAllocatedVUs: 50, maxVUs: 300,
    };
  }
  return s;
}

const SCENARIOS = {
  vus: () => ({
    shoppers: { executor: 'ramping-vus', exec: 'shopper', startVUs: 0, stages: steps(PEAK - ADMINS), gracefulRampDown: '30s' },
    admins: { executor: 'ramping-vus', exec: 'admin', startVUs: 0, stages: steps(ADMINS), gracefulRampDown: '30s' },
  }),
  rate: () => ({
    // maxVUs = peak rate allows an average answer time of 1 s at the top step; beyond that k6 drops
    // iterations (reported as dropped_iterations), which is itself a sign of the breaking point
    shoppers: {
      executor: 'ramping-arrival-rate', exec: 'shopper', startRate: 0, timeUnit: '1s',
      preAllocatedVUs: Math.ceil(PEAK_RATE * 0.2), maxVUs: PEAK_RATE, stages: rateStages(),
    },
    admins: {
      executor: 'constant-arrival-rate', exec: 'admin', rate: Math.max(1, Math.round(ADMIN_RATE * 60)),
      timeUnit: '1m', duration: `${RATE_TOTAL_S}s`, preAllocatedVUs: 10, maxVUs: 60,
    },
  }),
  burst: burstScenarios,
};
if (!SCENARIOS[MODEL]) throw new Error(`LOAD_MODEL must be burst, rate or vus (got "${MODEL}")`);

// Burst model: judged on the burst and the counted window; the warm-up is reported but not judged
const THRESHOLDS = MODEL === 'burst'
  ? {
    'http_req_duration{scenario:burst}': ['p(95)<500'],
    'failed_after_retries{scenario:burst}': ['rate<0.01'],
    'http_req_duration{scenario:background}': ['p(95)<500'],
    'failed_after_retries{scenario:background}': ['rate<0.01'],
    'http_req_duration{scenario:admins}': ['p(95)<500'],
    'http_req_duration{scenario:warmup}': ['p(95)<60000'], // reported only
  }
  : {
    'http_req_duration': ['p(95)<500'],
    'http_req_duration{type:read}': ['p(95)<500'],
    'http_req_duration{type:write}': ['p(95)<500'],
    'failed_after_retries': ['rate<0.01'],
  };

export const options = {
  setupTimeout: '3m',
  scenarios: SCENARIOS[MODEL](),
  thresholds: THRESHOLDS,
  summaryTrendStats: ['avg', 'med', 'p(90)', 'p(95)', 'p(99)', 'max'],
};

const JSON_HDR = { 'Content-Type': 'application/json' };

function req(method, path, body, token, name, type, expected) {
  const headers = Object.assign({}, body ? JSON_HDR : {}, token ? { Authorization: token } : {});
  let res;
  for (let attempt = 0; attempt <= MAX_RETRIES; attempt++) {
    res = http.request(method, `${BASE}${path}`, body ? JSON.stringify(body) : null, {
      headers, tags: { name, type },
    });
    if (res.status !== 503 || attempt === MAX_RETRIES) break;
    retried503.add(1, { name });
    sleep(Number(res.headers['Retry-After'] || 2));
  }
  const ok = check(res, { [`${name} -> ${expected}`]: (r) => r.status === expected });
  failedAfterRetries.add(!ok, { name, type });
  if (!ok) unexpected.add(1, { name, status: String(res.status) });
  return res;
}

function login(name, password) {
  const res = req('POST', '/public/login', { name, password }, null, 'login', 'login', 200);
  return res.headers['Authorization'] || null;
}

function randomId() {
  return String(1000000000 + Math.floor(Math.random() * 8999999999));
}

// Runs once per task, before the load starts: admin token, test category and the shared shopper
// accounts. JWTs last 30 minutes, longer than the 20-minute test.
export function setup() {
  if (!BASE) fail('BASE_URL is not set');
  if (!ADMIN_PASSWORD) fail('ADMIN_PASSWORD is not set');
  const adminToken = login('admin', ADMIN_PASSWORD);
  if (!adminToken) fail('admin login failed');
  const category = `lt${RUN_ID}i${INSTANCE}`;
  req('POST', '/data/product_categories', { code: category, name: `Load test ${category}` }, adminToken, 'create_category', 'write', 201);

  const shopperTokens = [];
  for (let i = 0; i < ACCOUNTS; i++) {
    const user = `lt${RUN_ID}i${INSTANCE}s${i}`;
    const password = `Pw-${RUN_ID}-${randomId()}`;
    const reg = req('POST', '/public/register', {
      name: user, password,
      profile: { firstName: 'Load', lastName: 'Test', idNumber: randomId(), email: `${user}@example.com` },
    }, null, 'register', 'write', 200);
    if (reg.status !== 200) continue;
    const token = login(user, password);
    if (token) shopperTokens.push(token);
  }
  if (shopperTokens.length === 0) fail('no shopper account could be created');
  return { category, adminToken, shopperTokens };
}

// --- shoppers: browse back to back (one request in flight per VU) ------------------------------
export function shopper(data) {
  const token = data.shopperTokens[exec.vu.idInTest % data.shopperTokens.length];
  const r = Math.random();
  if (r < 0.45) req('GET', '/data/products', null, null, 'list_products', 'read', 200);
  else if (r < 0.60) req('GET', '/data/products?nameLike=lap', null, null, 'search_products', 'read', 200);
  else if (r < 0.75) req('GET', '/public/about', null, null, 'about', 'read', 200);
  else if (r < 0.85) req('GET', '/data/billing_types', null, null, 'billing_types', 'read', 200);
  else req('GET', '/account/profile', null, token, 'profile', 'read', 200);
}

// --- admins: product life cycle, back to back, with the shared admin token ----------------------
export function admin(data) {
  const barcode = `LT${RUN_ID}i${INSTANCE}v${exec.vu.idInTest}x${exec.vu.iterationInScenario}`;
  const product = {
    name: `Load test product ${barcode}`, barcode, description: 'Created by k6',
    price: 1000, currentStock: 10, criticalStock: 2, category: { code: data.category },
  };
  const q = `/data/products?barcode=${barcode}`;
  req('POST', '/data/products', product, data.adminToken, 'create_product', 'write', 201);
  req('GET', q, null, null, 'get_product', 'read', 200);
  req('PUT', q, Object.assign({}, product, { price: 1200, currentStock: 8 }), data.adminToken, 'replace_product', 'write', 204);
  req('PATCH', q, { price: 999 }, data.adminToken, 'patch_product', 'write', 204);
  req('DELETE', q, null, data.adminToken, 'delete_product', 'write', 204);
}

// One line per task in CloudWatch Logs ("K6_SUMMARY {...}"), easy to collect and combine
export function handleSummary(data) {
  const keep = /^(http_reqs|iterations|dropped_iterations|vus_max|http_req_duration|failed_after_retries|retried_503|unexpected_status|checks)/;
  const metrics = {};
  for (const [k, v] of Object.entries(data.metrics)) if (keep.test(k)) metrics[k] = v.values;
  const thresholds = {};
  for (const [k, v] of Object.entries(data.metrics)) if (v.thresholds) thresholds[k] = Object.values(v.thresholds).every((t) => t.ok);
  return { stdout: `K6_SUMMARY ${JSON.stringify({ instance: INSTANCE, run: RUN_ID, model: MODEL, metrics, thresholds })}\n` };
}
