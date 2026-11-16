@echo off
REM Start the lab at load-test / demo size: database with a standby copy in the second zone (Multi-AZ).
REM The organization's SCP only allows creating db.t3.micro so far; ask the cloud team before
REM changing db_instance_class here. Stop with lab-down.cmd as usual.
REM Run from infra\aws.
terraform apply -var="lab_on=true" -var="db_multi_az=true"
