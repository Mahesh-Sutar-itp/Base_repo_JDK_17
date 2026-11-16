@echo off
REM Start a practice session: creates everything that is billed by the hour.
REM Run from infra\aws. Takes about 30-35 minutes (database, RDS Proxy, CloudFront).
REM
REM Usage:  lab-up.cmd [image_tag]
REM   image_tag = the git short SHA the Deploy workflow last pushed to ECR (see its run summary).
REM   Without it the default in variables.tf is used, which may be old or already expired from ECR.
if "%~1"=="" (
  terraform apply -var="lab_on=true"
) else (
  terraform apply -var="lab_on=true" -var="app_image_tag=%~1"
)
