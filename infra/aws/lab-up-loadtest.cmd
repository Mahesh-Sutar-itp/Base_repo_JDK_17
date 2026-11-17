@echo off
REM Start the lab at load-test / demo size:
REM   - database with a standby copy in the second zone (Multi-AZ)
REM   - bigger app tasks: 1 vCPU / 2 GB instead of 0.5 vCPU / 1 GB (Spring Boot on 0.5 vCPU hit 100% CPU
REM     at 500 users; JIT and GC need a full core). Same price per vCPU, so 3 x 1 vCPU = 6 x 0.5 vCPU.
REM   - at least 3 tasks from the start (pre-scaled: the test is planned, so do not wait for auto scaling)
REM The organization's SCP only allows creating db.t3.micro so far; ask the cloud team before
REM changing db_instance_class here. Stop with lab-down.cmd as usual.
REM Run from infra\aws. Start from a lab that is OFF: the task size is applied when the task
REM definition is created (Terraform ignores later container changes, see ecs.tf).
REM
REM Usage:  lab-up-loadtest.cmd [image_tag]   (same meaning as in lab-up.cmd)
set LOADTEST_VARS=-var="lab_on=true" -var="db_multi_az=true" -var="app_cpu=1024" -var="app_memory=2048" -var="app_desired_count=3"
if "%~1"=="" (
  terraform apply %LOADTEST_VARS%
) else (
  terraform apply %LOADTEST_VARS% -var="app_image_tag=%~1"
)
