@echo off
REM End a practice session: removes everything that is billed by the hour.
REM Keeps the VPC, ECR images and secrets, so the next lab-up is quick. Database data is NOT kept:
REM Flyway recreates the schema and demo data on the next start.
terraform apply -var="lab_on=false"
