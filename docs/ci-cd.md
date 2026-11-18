# CI/CD pipeline (Phase 5)

Three workflows in `.github/workflows/`:

| Workflow | When | What it does | AWS access |
|---|---|---|---|
| `ci.yml` | every pull request, every push to `main` | build, run the tests, JaCoCo coverage, SonarCloud analysis (once connected) | none |
| `deploy.yml` | after `ci.yml` passes on `main` (or run it by hand) | build the Docker image, Trivy scan, push to ECR, deploy to ECS if the lab is on, smoke test, roll back on failure | OIDC role, `main` branch only |
| `lab-schedule.yml` | Mon-Fri 14:00 IST (up), every day 23:00 IST (down), or by hand | `terraform apply -var lab_on=true/false`, the same as `lab-up.cmd` / `lab-down.cmd` | separate OIDC role, GitHub environment `lab` only |

```
pull request  ->  ci.yml (build + test + Sonar)                      no AWS, safe for forks
merge to main ->  ci.yml  ->  deploy.yml
                               image : build -> Trivy -> push to ECR (tag = first 7 chars of the commit)
                               deploy: lab on?  yes -> new task definition -> ECS rolling update
                                                       (circuit breaker + rollback) -> smoke test
                                                       -> roll back if the smoke test fails
                                       no  -> report "image pushed, nothing deployed"
```

## One-time setup

All of this is done in the GitHub repository: **Settings > Secrets and variables > Actions**.

### 1. AWS role (needed by `deploy.yml`)

Terraform creates the role (`infra/aws/github-oidc.tf`). Get its address:

```
cd infra\aws
terraform output github_actions_role_arn
```

Add a **repository variable** (Variables tab, not a secret; it is not sensitive) named `AWS_ROLE_ARN`
with that value (it looks like `arn:aws:iam::<ACCOUNT_ID>:role/trebol-poc-github-actions-role`).

No AWS access keys are ever stored in GitHub. The role can only be assumed by workflows running on the
`main` branch of this repository, and can only push to the ECR repository and update the ECS service.

### 2. SonarCloud (optional until you want the quality gate)

1. Go to https://sonarcloud.io and sign in with GitHub.
2. Create (or import) an **organization** for your GitHub account. The free plan covers public repositories.
3. Choose **Analyze new project**, select this repository, and pick **With GitHub Actions** as the method.
   SonarCloud then shows a project key and tells you to create a token.
4. In SonarCloud, turn **off** *Automatic Analysis* for the project (Administration > Analysis Method),
   because CI runs the analysis. Otherwise both run and clash.
5. In GitHub add:
   - Secret `SONAR_TOKEN`: the token SonarCloud generated.
   - Variable `SONAR_ORGANIZATION`: your SonarCloud organization key.
   - Variable `SONAR_PROJECT_KEY`: the project key.

Until all three exist the Sonar step is skipped and CI still passes. Once they exist, a failed quality gate
fails the build.

### 3. Lab schedule (needed by `lab-schedule.yml`)

The schedule runs Terraform, so it needs far more rights than the deploy role. It gets its own role
(`infra/aws/lab-scheduler.tf`) that only jobs in the GitHub environment `lab` can assume. The role can create
and delete the lab resources and read/write the Terraform state file. It cannot change IAM, other S3 data
or secrets.

1. From the laptop, create only the role and its policy (free, always on). `-target` keeps this safe whether
   the lab is on or off (a plain plan uses `lab_on=false` and would remove a running lab). Expect `2 to add`:
   ```
   cd infra\aws
   terraform plan -target=aws_iam_role_policy.lab_scheduler -out=tfplan
   terraform apply tfplan
   terraform output lab_scheduler_role_arn
   ```
2. In GitHub: **Settings > Environments > New environment**, name `lab`.
   - **Deployment branches and tags**: *Selected branches and tags*, add `main`.
   - Do **not** add required reviewers (a scheduled run would wait for approval).
   - **Environment secrets**: `OWNER_EMAIL` (same value as `owner.auto.tfvars`) and `ALARM_EMAIL` (same as
     `alarm.auto.tfvars`). Both are required: without `ALARM_EMAIL` Terraform would delete the alarm email
     subscription.
   - **Environment variables**: `LAB_ROLE_ARN` = the output of step 1.
3. Optional, repository variable (Settings > Secrets and variables > Actions > Variables):
   `LAB_SCHEDULE_PAUSED` = `true` stops the timetable; delete it or set `false` to resume.

GitHub sends an email when a scheduled run fails (to whoever last changed the schedule). GitHub also
disables schedules in public repositories after 60 days without commits; re-enable from the Actions tab.

## Day to day

- **Open a pull request**: `ci.yml` runs. Merge only when it is green.
- **Merge to main**: the image is built, scanned and pushed. If the lab is **off** (the normal state) nothing
  else happens, and the run summary prints the tag to use at the next lab-up:
  `terraform apply -var="lab_on=true" -var="app_image_tag=<tag>"`.
- **Lab on**: the same run also deploys the new image to ECS and smoke-tests it through CloudFront.
- **Run by hand**: Actions tab > Deploy > Run workflow.
- **Lab schedule**: up Mon-Fri at 14:00 IST (API ready around 14:20-14:30), down every day at 23:00 IST. GitHub
  runs schedules on a best-effort basis: they can start late (hours, at busy times) or be skipped, so check the
  Actions tab before relying on it. Up uses the image of the latest `main` commit (or the newest image in ECR)
  at the normal size; for a load test run `lab-up-loadtest.cmd` from the laptop instead, and set
  `LAB_SCHEDULE_PAUSED=true` if it must run past 23:00. Up/down by hand: Actions > Lab schedule > Run workflow.
  The public URL is not printed (public repo): `terraform output app_url`.
- **Schedule applies `main`**: a scheduled up builds the lab from whatever is on `main`. Changes to
  always-on resources the schedule role may not change (IAM roles and policies, the budget, ECR, secrets,
  the VPC) must be applied from the laptop first, e.g. `terraform apply -target=aws_budgets_budget.monthly`;
  otherwise the scheduled run fails with AccessDenied until you do.

## How the safety nets work

- **Scan before push**: Trivy fails the build on fixable CRITICAL or HIGH findings. Accepted findings are in
  `trebol-backend-monolith/.trivyignore`, each with a reason and an expiry date.
- **Immutable tags**: a commit's image is built once. Re-running the workflow for the same commit reuses it.
- **Circuit breaker**: the deploy turns on the ECS deployment circuit breaker with rollback. If new tasks keep
  failing, ECS returns to the previous version by itself, and the workflow fails.
- **Smoke test**: `test-endpoints.sh` (33 checks) runs against the public URL. If it fails, the workflow puts
  the previous task definition back.
- **Why the circuit breaker is not in Terraform**: at `lab-up` the database proxy needs 5 to 10 minutes before
  it accepts connections, and the first tasks have to keep retrying until then. The workflow switches the breaker
  on only for deployments to a running service. `lab-down` deletes the service anyway, so there is no drift to keep.

## Known limits

- A deploy only happens while the lab is on. At the next `lab-up` the task definition is rebuilt from Terraform,
  so pass `-var app_image_tag=<tag>` to start from the latest image (default is the one in `variables.tf`).
- `lab-schedule.yml` turns the lab off every night at 23:00 IST. A lab started by hand after that stays on
  until the next 23:00 run, so still finish late sessions with `lab-down.cmd`.
- Cost of the timetable at the normal size: about 9 h x 5 days x $0.19/h = about $8.50 a week (about $38 a
  month), more on load-test days. That is close to the $50 monthly budget alert; pause the schedule on days
  the lab is not needed.
