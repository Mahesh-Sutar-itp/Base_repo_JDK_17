# CI/CD pipeline (Phase 5)

Three workflows in `.github/workflows/`:

| Workflow | When | What it does | AWS access |
|---|---|---|---|
| `ci.yml` | every pull request, every push to `main` | build, run the tests, JaCoCo coverage, SonarCloud analysis (once connected) | none |
| `deploy.yml` | after `ci.yml` passes on `main` (or run it by hand) | build the Docker image, Trivy scan, push to ECR, deploy to ECS if the lab is on, smoke test, roll back on failure | OIDC role, `main` branch only |
| `lab-schedule.yml` | by hand only (Run workflow > up / down); no timetable since 23 Nov 2026 | `terraform apply -var lab_on=true/false`, the same as `lab-up.cmd` / `lab-down.cmd` | separate OIDC role, GitHub environment `lab` only |

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
   - Required reviewers are optional: with them, every up/down run waits for an approval click.
   - **Environment secrets**: `OWNER_EMAIL` (same value as `owner.auto.tfvars`) and `ALARM_EMAIL` (same as
     `alarm.auto.tfvars`). Both are required: without `ALARM_EMAIL` Terraform would delete the alarm email
     subscription.
   - **Environment variables**: `LAB_ROLE_ARN` = the output of step 1.

The workflow ran on a timetable (Mon-Fri 14:00-23:00 IST) until 23 Nov 2026. That was removed because GitHub
runs schedules on a best-effort basis and started the 14:00 run 8-9 hours late, so the lab came up around
22:30 IST and went down around 04:00 IST. A repository variable `LAB_SCHEDULE_PAUSED`, if it still exists, is
no longer read and can be deleted.

## Day to day

- **Open a pull request**: `ci.yml` runs. Merge only when it is green.
- **Merge to main**: the image is built, scanned and pushed. If the lab is **off** (the normal state) nothing
  else happens, and the run summary prints the tag to use at the next lab-up:
  `terraform apply -var="lab_on=true" -var="app_image_tag=<tag>"`.
- **Lab on**: the same run also deploys the new image to ECS and smoke-tests it through CloudFront.
- **Run by hand**: Actions tab > Deploy > Run workflow.
- **Lab up / down from GitHub**: Actions > Lab schedule > Run workflow > `up` or `down`. Up takes 20-30 minutes
  and uses the image of the latest `main` commit (or the newest image in ECR) at the normal size; for a load
  test run `lab-up-loadtest.cmd` from the laptop instead. The public URL is not printed (public repo):
  `terraform output app_url`. Nothing switches the lab off by itself: finish every session with `down`.
- **The workflow applies `main`**: an up from GitHub builds the lab from whatever is on `main`. Changes to
  always-on resources its role may not change (IAM roles and policies, the budget, ECR, secrets, the VPC)
  must be applied from the laptop first, e.g. `terraform apply -target=aws_budgets_budget.monthly`;
  otherwise the run fails with AccessDenied until you do.

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
- Nothing turns the lab off by itself. A lab left on costs ~$0.19/hour at normal size (~$4.50 a day) and
  ~$0.55/hour at load-test size; the $100 budget alert emails at $30, $50 and $90. Always finish a session with
  `down` (GitHub) or `lab-down.cmd` (laptop).
