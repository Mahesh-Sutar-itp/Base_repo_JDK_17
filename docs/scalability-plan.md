# Sizing for 2,500 requests/second and scalability plan

Status: plan, based on load test r11171916 (17 Nov 2026). The sizing below has **not been tested yet**;
section 5 is the test that confirms it and then finds where the system breaks.

## 1. Target and constraints

| Given | What it means |
|---|---|
| 10,000 requests in total, over 5 minutes | 33 req/s on average. The whole test is small; what matters is the peak. |
| Peak of 2,500 requests/second | At 2,500 req/s, 10,000 requests last 4 seconds: the peak is a **burst**. Auto scaling (3-4 minutes) cannot react to it, so the capacity must already be running and warm. |
| Budget $100 in total for the POC | Hourly lab costs matter; per-request costs of a 10,000-request test are about $0.02 (section 4). |
| More database instances allowed; burstable instances, bursting on | Read replicas are planned as the next tier (section 6). The database is db.t3.micro (the only class the organization's SCP allows) in **Unlimited** mode: in the last test its credit balance reached 0 and `CPUSurplusCreditBalance` went above 0, which only happens when bursting beyond earned credits is on. |
| Increase gradually to find the breaking point | Repeat the burst test with a higher peak each time (section 5). |

Pass criteria for the burst: **p95 < 500 ms measured at the load balancer** (ALB `TargetResponseTime`, i.e. the
app's answer time, without the internet or CloudFront) **and < 1% failed requests** after up to 3 retries of a 503.

Product lists change rarely, so a short delay before a change shows (up to the 30-60 s cache time) is accepted.

**Traffic mix** (k6 shoppers): 45% product list, 15% product search, 15% store info, 10% billing types (all
public and cached in each app task for 30 s), 15% customer profile (signed in, reads the database), plus admins
creating, changing and deleting products at a low rate (3 product life cycles per minute, since lists rarely
change). Logins are not part of the peak: each costs a BCrypt hash, roughly
10-20 logins/s per vCPU, so a login-heavy peak needs its own sizing.

## 2. What the last load test showed

Load test r11171916: 5,000 concurrent requests for 20 minutes, app tasks of 1 vCPU / 2 GB (3 at start, auto
scaling to 30), db.t3.micro behind RDS Proxy. Per minute, from `infra/aws/loadgen/report.sh`:

| Minute (UTC) | req/s | p95 ms | p99 ms | app 5xx/min | healthy tasks | app CPU % | DB CPU % | wait for a DB connection (max) |
|---|---|---|---|---|---|---|---|---|
| 13:46 | 440 | 275 | 467 | 0 | 3 | 39 | 7 | 1 ms |
| 13:49 | 2,420 | 1,452 | 2,198 | 0 | 3 | 78 | 17 | 2 ms |
| 13:50 | 2,735 | 1,249 | 1,942 | 0 | 3 | 60 | 18 | 0 ms |
| 13:51 | 3,414 | 1,220 | 2,547 | 0 | 7 | 29 | 20 | 0 ms |
| 13:52 | 4,759 | 1,676 | 2,493 | 0 | 18 | 58 | 30 | 21 ms |
| 13:53 | 5,003 | 1,894 | 2,698 | 0 | 28 | 80 | 35 | 4,424 ms |
| 13:54-14:06 | 4,200-5,400 | 2,300-6,400 | 4,300-20,000 | 0-3,700 | 14-30 | 15-40 | 27-36 | 0.3-6.7 s |

1. **App: about 800 req/s per 1-vCPU task at 80-100% CPU.** Three tasks served 2,420 req/s with no errors, but p95
   was 1.2-1.8 s because their CPU was saturated. At 2,500 req/s the problem is not throughput but having enough
   warm tasks before the peak.
2. **Auto scaling is too slow for a burst.** Going from 3 to 28 healthy tasks took about 3 minutes. The requests
   target (4,000/min per task) also asked for 30 tasks while their CPU sat at 15-40%.
3. **The breaking point was the database connection path, at about 4,500-5,000 req/s.** RDS Proxy holds only ~10
   database connections on db.t3.micro (`max_connections` is set from instance memory). Up to ~3,400 req/s
   (2,400 queries/s) a connection was free within 2 ms; at 4,800 req/s the wait was 21 ms, from 5,000 req/s
   seconds. Requests then timed out in the app's pool after 5 s with 503 (up to 3,700 per minute) and p99 reached
   20 s. Database CPU stayed at 30-36%: the limit was connections, not CPU.
4. **The test kept the read cache cold.** ~0.9 database queries per request although ~85% of requests are
   cacheable: every product write clears the product-list cache of the task that takes it, and the test's 50 admin
   users wrote non-stop. The new k6 model writes 3 product life cycles per minute, matching a catalogue that
   rarely changes.
5. **Busy tasks were replaced by the health check** (healthy tasks fell from 30 to 14). Fixed since: the ALB checks
   liveness on a separate port without the database (PRs #12, #13). Not yet re-tested under load.
6. Responses are small: 2.6 KB per request at the ALB.

## 3. Sizing for the target

Applied by `infra/aws/lab-up-loadtest.cmd`.

| Tier | Setting | Why |
|---|---|---|
| CloudFront + WAF | unchanged | Managed; scale automatically. The load generator's IP is excluded from the login rate limit. |
| Load balancer | unchanged: least outstanding requests, health check = liveness on port 8081 | 2,500 req/s is small for an ALB. Busy tasks are no longer replaced. |
| App (ECS Fargate) | **8 tasks x 1 vCPU / 2 GB, running before the test** (4 per zone); auto scaling 8-16 | 2,500 / 8 = ~310 req/s per task, about 40% CPU at the measured ~800 req/s per task, so the burst does not queue. With one zone lost, 4 tasks would run at ~80%. The 1-minute **warm-up** of the k6 burst model lets the JVMs compile the hot code first (new tasks ran at 100% CPU in the last test). |
| Auto scaling | requests target 18,000/min (300 req/s) per task (was 4,000); CPU target 50% | Irrelevant for a 4-second burst, but keeps longer tests on a sensible number of tasks. |
| DB connections | 3 per task (24 in total) to RDS Proxy | The proxy shares its ~10 database connections. |
| Database | **1 x db.t3.micro, Unlimited bursting, Multi-AZ standby**, RDS Proxy | The burst needs at most ~2,300 queries/s even with a cold cache, which the last test handled with < 2 ms waits; with a warm cache far fewer. The standby is for failover, not capacity. Read replicas are the next tier (section 6). |

**Why Fargate and not burstable EC2 for the app.** Fargate has no burst credits; it is billed per vCPU and GB.
The trainee account allows EC2 only up to t3.medium. A burst of a few seconds would suit burstable EC2 well, but
the instances have to be running anyway, cost about the same per vCPU as Fargate, and add an Auto Scaling group,
a capacity provider and patching. Fargate stays for the POC.

## 4. Budget ($100 for the whole POC)

| Item | Cost |
|---|---|
| Lab at normal size (2 x 0.5 vCPU, single-AZ DB) | ~$0.19/hour |
| Lab at load-test size (8 x 1 vCPU, Multi-AZ DB, proxy, NAT, ALB, WAF) | ~$0.55/hour |
| k6 load generator, 3 tasks x 4 vCPU / 8 GB, only while a test runs | ~$0.60/hour |
| One burst run (~16,000 requests incl. warm-up): CloudFront + WAF request fees | ~$0.03 |
| A read replica (db.t3.micro), if added | ~$0.02/hour each |
| **A test day: 3 hours at load-test size, 1 hour of k6, ~10 burst runs** | **~$2.50** |
| Lab schedule, weekdays 14:00-23:00 at normal size | ~$1.70/day, ~$38/month |

- **What has been spent so far is not visible yet**: the budget and Cost Explorer filter on the `Project` tag,
  which must first be activated as a cost allocation tag (cloud team). Until then they show $0, and the account
  total includes other teams. This is the first open point.
- The scheduled lab is the biggest cost line. With $100 in total, run the timetable only on days the lab is used
  (repository variable `LAB_SCHEDULE_PAUSED=true` on the others) and keep a long sustained test (`LOAD_MODEL=rate`,
  ~4 million requests, ~$8 in request fees) to one run at most.
- The AWS budget alert is set to $100 (alerts at $30, $50 and $90, plus a forecast alert).

## 5. Test plan: burst runs with a rising peak

1. Pause the lab schedule if the tests may run past 23:00 IST.
2. Start the lab at load-test size from a lab that is off: `lab-up-loadtest.cmd <image_tag>`.
3. Start the k6 tasks (`terraform output loadgen_run_command`, repeated for INSTANCE 1 and 2 with their execution
   segments). Each run takes ~6.5 minutes: 1 minute of warm-up (not counted), then the 5-minute window with the
   burst 2.5 minutes in. Set the peak per run in the `--overrides` environment, e.g.
   `{"name":"PEAK_RATE","value":"5000"},{"name":"TOTAL_REQUESTS","value":"20000"}`.
4. Run the ladder, one run at a time, stopping at the first failure:

   | Run | PEAK_RATE (req/s) | TOTAL_REQUESTS | Burst length | k6 tasks |
   |---|---|---|---|---|
   | 1 | 500 | 10,000 | ~13 s | 1 |
   | 2 | 1,000 | 10,000 | ~7 s | 1 |
   | 3 (the target) | 2,500 | 10,000 | ~3.5 s | 3 |
   | 4 | 5,000 | 20,000 | ~4 s | 3 |
   | 5 | 7,500 | 30,000 | ~4.5 s | 6 |
   | 6 | 10,000 | 40,000 | ~4.5 s | 6 |

   Burst length = 1 s ramp up + hold + 1 s ramp down; with 6 k6 tasks use six execution segments
   (`0:1/6`, `1/6:2/6`, ...). `TOTAL_REQUESTS` counts the 5-minute window only, not the warm-up.

5. Judge each run:

   | Signal | Where | Pass |
   |---|---|---|
   | **p95 at the load balancer** in the minute with the burst | `bash infra/aws/loadgen/report.sh <start> <end>`, column p95 | **< 500 ms** |
   | failed requests | `K6_SUMMARY` line: `failed_after_retries{scenario:burst}` | < 1% |
   | 5xx from the ALB itself | report: 5xx_alb | 0 |
   | `dropped_iterations` | `K6_SUMMARY` | 0 (otherwise answers were too slow, or the generator is full) |
   | `retried_503` | `K6_SUMMARY` | ideally 0; a few are fine if they pass on retry |

   The ALB only gives one-minute figures. The burst minute is still ~90% burst traffic (e.g. ~6,900 of ~7,500
   requests at 2,500 req/s), so its p95 is the burst's p95. If the burst crosses a minute boundary, take the
   higher of the two minutes. The `K6_SUMMARY` lines are in CloudWatch Logs `/ecs/trebol-poc/k6`, one per k6 task;
   k6's own `http_req_duration{scenario:burst}` p95 adds CloudFront and the NAT hop, so if it passes, the ALB
   figure passes too. The same report shows the cause of a failure: CPU, database connection waits, healthy tasks.
6. The breaking point is the first run that fails. Expected (to be confirmed): run 3 (2,500 req/s) passes; the
   connection path to the single database fails first, somewhere above ~4,500 req/s; with 8-16 warm tasks the app
   has room for ~6,000-12,000 req/s.
7. Results so far:

   | Run (UTC) | Setup | Requests | Burst p95 at ALB | k6 burst p95 / p99 | Failed | App CPU (avg / busiest) | DB connection wait | Result |
   |---|---|---|---|---|---|---|---|---|
   | b5k1841, 17 Nov 18:41 | 8 x 1 vCPU, 1 x db.t3.micro (single-AZ), 2 k6 tasks; TOTAL_REQUESTS=5000, PEAK_RATE=2500, BG_RATE=5 | 4,999 counted (3,424 in the burst) + 6,000 warm-up | 68 ms (p99 101 ms) | 79-82 / 112 ms | 0 | 3% / 20% | 0 ms | **pass** |

   Note on b5k1841: with only 5,000 requests the burst lasted ~2.4 s and held the full 2,500 req/s for ~0.4 s
   (1 s ramps). Run 3 (10,000 requests) holds the peak ~1.8 s and is the real check of the target.
8. Optional, once: `LOAD_MODEL=rate` sustained steps (30 minutes, 250 to 5,000 req/s) to measure capacity without
   the burst; costs ~$8 in request fees.

## 6. Scalability plan beyond 2,500 req/s

Each tier keeps the previous one's changes.

### Tier 1: up to ~5,000 req/s, no new database instances

| Change | Effect | Effort |
|---|---|---|
| **Cache public GETs in CloudFront** (product list/search, store info, billing types; 30 s TTL, query string in the cache key, only requests without `Authorization`) | ~85% of shopper requests never reach the ALB, app or database | Terraform: cache policy and cache behaviours in `cloudfront.tf` |
| **Longer cache time for product lists** (e.g. 60 s, in the app and in CloudFront); keep clearing on write | Fewer cache misses; with rare product changes the clear-on-write costs almost nothing | Agreed: lists change rarely, a short delay is accepted |
| **Cache a signed-in user's profile and permissions briefly** in the app | Fewer queries for the 15% signed-in traffic | Small app change |
| App 12-16 tasks (allowed by the load-test profile) | ~10,000 req/s of app capacity | none |

### Tier 2: up to ~10,000 req/s, more database instances

**Read replicas (allowed; build when a test shows the database path breaking where it matters).**

| Part | Design |
|---|---|
| Terraform | `db_read_replicas` (0-2): db.t3.micro replicas of the primary in the other zone. Replicas need automated backups on the primary (`backup_retention_period` >= 1, today 0). Security group rule app -> replicas on 3306. |
| Connection path | **RDS Proxy cannot front MariaDB read replicas**: for RDS (not Aurora) a proxy targets only the writer, and read-only proxy endpoints exist only for Aurora ([AWS docs](https://docs.aws.amazon.com/AmazonRDS/latest/UserGuide/rds-proxy.html)). So the app connects to the replicas directly with its own small pool (2 per task). |
| App | A routing data source: transactions marked `@Transactional(readOnly = true)` go to a replica, everything else to the proxy (primary). Mark the read paths that dominate: product list/search, profile, store info. Single-record lookups (e.g. by barcode) stay on the primary, so an admin who creates a product can read it back at once. |
| Replica lag | MariaDB replication is asynchronous (normally well under a second). Reads routed to a replica can be that much behind, the same order as the 30 s cache. |
| Limits | db.t3.micro has few connections: 8 tasks x 2 = 16 per replica fits, 16+ tasks need fewer connections per task or a larger class. Each replica adds ~$0.02/hour. |
| Effect | Database reads spread over 2-3 instances; the primary and its proxy keep writes and lookups. Expected to move the database limit to roughly 2-3x the single-instance figure, to be measured with the same burst ladder. |

Other Tier 2 changes:

| Change | Effect | Note |
|---|---|---|
| Larger database class: db.t3.medium (~$0.07/h) | `max_connections` grows with memory (4 GB vs 1 GB): several times more proxy connections, more CPU and memory (db.t3.micro had ~110 MB free at peak) | **SCP exception needed** |
| Aurora MySQL with reader instances | RDS Proxy then has a read-only endpoint, so no routing pools in the app | Aurora must be allowed; migration from MariaDB 10.11 to be tested |
| Shared cache (ElastiCache for Valkey) instead of one cache per task | One cache miss per item instead of one per task | ~$0.03/h for a small node |
| App 24-32 tasks | ~20,000 req/s of app capacity | check the account's Fargate vCPU quota first |
| Login capacity | BCrypt sign-ins scale with app CPU only: ~1 vCPU per 10-20 logins/s | measure with a login-heavy run |

### Tier 3: beyond ~10,000 req/s

- Aurora with several readers (auto scaling replicas); short writes, and asynchronous ones through a queue (SQS)
  where the business allows (e.g. order processing).
- CloudFront caching of everything cacheable; consider per-user caching of signed-in pages.
- ALB capacity reservation (LCU reservation) ahead of known spikes such as a sale.
- Raise service quotas (Fargate vCPU, ALB, proxy connections) ahead of time; Graviton or Fargate Spot for cost.

## 7. Open points

- Cloud team: **activate the `Project` cost allocation tag** so the POC's spend against the $100 is visible.
- Cloud team, for Tier 2: SCP exception for a larger RDS class or Aurora; the account's Fargate vCPU quota.

Decided: p95 is measured at the load balancer; product lists change rarely, so a 30-60 s cache delay is fine.
