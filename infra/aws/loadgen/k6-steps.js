// Step load test for the Trebol POC, run by ECS Fargate tasks (see ../loadgen.tf).
//
// Load model: CONCURRENT REQUESTS, not unique users.
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
// Environment: BASE_URL (required), ADMIN_PASSWORD (required), PEAK_CONCURRENCY, ADMIN_VUS,
//              SHOPPER_ACCOUNTS, RUN_ID, INSTANCE

import http from 'k6/http';
import { check, sleep, fail } from 'k6';
import exec from 'k6/execution';
import { Counter, Rate } from 'k6/metrics';

const BASE = __ENV.BASE_URL;
const ADMIN_PASSWORD = __ENV.ADMIN_PASSWORD;
const PEAK = Number(__ENV.PEAK_CONCURRENCY || 5000);
const ADMINS = Number(__ENV.ADMIN_VUS || 50);
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

export const options = {
  setupTimeout: '3m',
  scenarios: {
    shoppers: { executor: 'ramping-vus', exec: 'shopper', startVUs: 0, stages: steps(PEAK - ADMINS), gracefulRampDown: '30s' },
    admins: { executor: 'ramping-vus', exec: 'admin', startVUs: 0, stages: steps(ADMINS), gracefulRampDown: '30s' },
  },
  thresholds: {
    'http_req_duration': ['p(95)<500'],
    'http_req_duration{type:read}': ['p(95)<500'],
    'http_req_duration{type:write}': ['p(95)<500'],
    'failed_after_retries': ['rate<0.01'],
  },
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
  const keep = /^(http_reqs|iterations|vus_max|http_req_duration|failed_after_retries|retried_503|unexpected_status|checks)/;
  const metrics = {};
  for (const [k, v] of Object.entries(data.metrics)) if (keep.test(k)) metrics[k] = v.values;
  const thresholds = {};
  for (const [k, v] of Object.entries(data.metrics)) if (v.thresholds) thresholds[k] = Object.values(v.thresholds).every((t) => t.ok);
  return { stdout: `K6_SUMMARY ${JSON.stringify({ instance: INSTANCE, run: RUN_ID, metrics, thresholds })}\n` };
}
