@echo off
REM Start a practice session: creates everything that is billed by the hour.
REM Run from infra\aws. Takes a few minutes now; about 20-30 minutes once the database and CloudFront are added.
terraform apply -var="lab_on=true"
