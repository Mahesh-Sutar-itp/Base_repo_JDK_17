@echo off
REM Start the lab at load-test size, sized for 2,500 requests/second (see docs/scalability-plan.md):
REM   - database with a standby copy in the second zone (Multi-AZ)
REM   - app tasks of 1 vCPU / 2 GB (Spring Boot on 0.5 vCPU hit 100% CPU at 500 users; JIT and GC need
REM     a full core)
REM   - 8 tasks from the start (pre-scaled: a burst to 2,500 req/s arrives faster than auto scaling,
REM     which needs ~3-4 minutes to add a task). Load test r11171916 measured ~800 req/s per task at
REM     ~80-100% CPU on the browse mix, so 8 tasks hold 2,500 req/s at ~40% CPU, with one zone's
REM     worth of headroom.
REM   - auto scaling up to 16 tasks, aiming at 18,000 requests/min (300 req/s) per task; the 50% CPU
REM     target still adds tasks if the mix gets heavier (e.g. many logins: BCrypt costs CPU).
REM     The old 4,000/min target scaled to 30 tasks at ~2,000 req/s while their CPU sat at 15-40%.
REM The organization's SCP only allows creating db.t3.micro so far; ask the cloud team before
REM changing db_instance_class here. Stop with lab-down.cmd as usual.
REM Run from infra\aws. Start from a lab that is OFF: the task size is applied when the task
REM definition is created (Terraform ignores later container changes, see ecs.tf).
REM
REM Usage:  lab-up-loadtest.cmd [image_tag]   (same meaning as in lab-up.cmd)
REM   - k6 load generator enabled (needs loadgen.auto.tfvars, see loadgen.tf)
set LOADTEST_VARS=-var="lab_on=true" -var="db_multi_az=true" -var="app_cpu=1024" -var="app_memory=2048" -var="app_desired_count=8" -var="autoscaling_max_tasks=16" -var="autoscaling_requests_per_target=18000" -var="loadgen_enabled=true"
if "%~1"=="" (
  terraform apply %LOADTEST_VARS%
) else (
  terraform apply %LOADTEST_VARS% -var="app_image_tag=%~1"
)
