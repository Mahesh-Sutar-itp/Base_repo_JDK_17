#!/usr/bin/env bash
# Per-minute view of a load test across every tier, from CloudWatch (kept 15 days).
# Shows where the system breaks: the first minute where latency, errors or a resource jumps.
#
# Usage (AWS CloudShell, or Git Bash on the laptop with AWS_PROFILE set; the lab must be on,
# because the load balancer is looked up by name):
#   bash report.sh <start> <end>        times in UTC, e.g. 2026-11-18T09:00:00Z 2026-11-18T09:40:00Z
#
# Columns
#   rps        requests per second at the ALB (all traffic that got past CloudFront and WAF)
#   p95/p99    app response time in ms, seen by the ALB (CloudFront adds the internet leg)
#   5xx_app    5xx answers from the app per minute (503 = DB pool busy, the client retries)
#   5xx_alb    5xx made by the ALB itself (no healthy target, timeouts)
#   healthy    tasks passing the health check
#   cpu/cpumax ECS service CPU %, average and busiest task
#   db_cpu     database CPU % (db.t3 baseline is 10%; above it the instance spends burst credits)
#   surplus    CPUSurplusCreditBalance: > 0 means it is bursting beyond its credits (unlimited mode)
#   px_db      database connections held by RDS Proxy (db.t3.micro allows ~10)
#   px_cli     app connections to RDS Proxy
#   borrow     longest wait for a database connection at the proxy, ms
#   qps        queries per second through RDS Proxy
set -euo pipefail

START=${1:?start time (UTC), e.g. 2026-11-18T09:00:00Z}
END=${2:?end time (UTC)}
REGION=${AWS_REGION:-us-east-1}
PROJECT=${PROJECT:-trebol-poc}
PY=$(command -v python3 || command -v python)

LB=$(aws elbv2 describe-load-balancers --names "$PROJECT-alb" --region "$REGION" \
  --query 'LoadBalancers[0].LoadBalancerArn' --output text)
LB_DIM=${LB#*:loadbalancer/}
TG=$(aws elbv2 describe-target-groups --names "$PROJECT-app-tg" --region "$REGION" \
  --query 'TargetGroups[0].TargetGroupArn' --output text)
TG_DIM=${TG##*:}

Q=$(mktemp)
trap 'rm -f "$Q"' EXIT
m() { # id namespace metric stat dims-json
  printf '{"Id":"%s","MetricStat":{"Metric":{"Namespace":"%s","MetricName":"%s","Dimensions":%s},"Period":60,"Stat":"%s"}}' "$1" "$2" "$3" "$5" "$4"
}
ALB="[{\"Name\":\"LoadBalancer\",\"Value\":\"$LB_DIM\"}]"
ALBTG="[{\"Name\":\"LoadBalancer\",\"Value\":\"$LB_DIM\"},{\"Name\":\"TargetGroup\",\"Value\":\"$TG_DIM\"}]"
ECS="[{\"Name\":\"ClusterName\",\"Value\":\"$PROJECT\"},{\"Name\":\"ServiceName\",\"Value\":\"$PROJECT-backend\"}]"
DB="[{\"Name\":\"DBInstanceIdentifier\",\"Value\":\"$PROJECT-db\"}]"
PX="[{\"Name\":\"ProxyName\",\"Value\":\"$PROJECT-proxy\"}]"
{
  echo "["
  m req AWS/ApplicationELB RequestCount Sum "$ALB"; echo ","
  m p95 AWS/ApplicationELB TargetResponseTime p95 "$ALB"; echo ","
  m p99 AWS/ApplicationELB TargetResponseTime p99 "$ALB"; echo ","
  m t5xx AWS/ApplicationELB HTTPCode_Target_5XX_Count Sum "$ALB"; echo ","
  m e5xx AWS/ApplicationELB HTTPCode_ELB_5XX_Count Sum "$ALB"; echo ","
  m healthy AWS/ApplicationELB HealthyHostCount Average "$ALBTG"; echo ","
  m cpu AWS/ECS CPUUtilization Average "$ECS"; echo ","
  m cpumax AWS/ECS CPUUtilization Maximum "$ECS"; echo ","
  m dbcpu AWS/RDS CPUUtilization Average "$DB"; echo ","
  m surplus AWS/RDS CPUSurplusCreditBalance Maximum "$DB"; echo ","
  m pxdb AWS/RDS DatabaseConnections Maximum "$PX"; echo ","
  m pxcli AWS/RDS ClientConnections Maximum "$PX"; echo ","
  m borrow AWS/RDS DatabaseConnectionsBorrowLatency Maximum "$PX"; echo ","
  m qpm AWS/RDS QueryRequests Sum "$PX"
  echo "]"
} > "$Q"

QFILE=$Q
command -v cygpath >/dev/null && QFILE=$(cygpath -w "$Q") # Git Bash: the AWS CLI needs a Windows path
aws cloudwatch get-metric-data --metric-data-queries "file://$QFILE" \
  --start-time "$START" --end-time "$END" --region "$REGION" --output json |
"$PY" -c '
import json, sys, collections
from datetime import datetime, timezone
rows = collections.defaultdict(dict)
for r in json.load(sys.stdin)["MetricDataResults"]:
    for t, v in zip(r["Timestamps"], r["Values"]):
        # the CLI prints times in the local time zone; show UTC like the arguments
        utc = datetime.fromisoformat(t).astimezone(timezone.utc).strftime("%Y-%m-%dT%H:%M")
        rows[utc][r["Id"]] = v
cols = [("rps", lambda r: r.get("req", 0) / 60), ("p95", lambda r: r.get("p95", 0) * 1000),
        ("p99", lambda r: r.get("p99", 0) * 1000), ("5xx_app", lambda r: r.get("t5xx", 0)),
        ("5xx_alb", lambda r: r.get("e5xx", 0)), ("healthy", lambda r: r.get("healthy", 0)),
        ("cpu", lambda r: r.get("cpu", 0)), ("cpumax", lambda r: r.get("cpumax", 0)),
        ("db_cpu", lambda r: r.get("dbcpu", 0)), ("surplus", lambda r: r.get("surplus", 0)),
        ("px_db", lambda r: r.get("pxdb", 0)), ("px_cli", lambda r: r.get("pxcli", 0)),
        ("borrow", lambda r: r.get("borrow", 0) / 1000), ("qps", lambda r: r.get("qpm", 0) / 60)]
print("minute (UTC)     " + " ".join(f"{c:>8}" for c, _ in cols))
for t in sorted(rows):
    print(t.replace("T", " ") + " " + " ".join(f"{f(rows[t]):8.0f}" for _, f in cols))
'
