# CI/CD pipeline (Phase 5)

Two workflows in `.github/workflows/`:

| Workflow | When | What it does | AWS access |
|---|---|---|---|
| `ci.yml` | every pull request, every push to `main` | build, run the tests, JaCoCo coverage, SonarCloud analysis (once connected) | none |
| `deploy.yml` | after `ci.yml` passes on `main` (or run it by hand) | build the Docker image, Trivy scan, push to ECR, deploy to ECS if the lab is on, smoke test, roll back on failure | OIDC role, `main` branch only |

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

## Day to day

- **Open a pull request**: `ci.yml` runs. Merge only when it is green.
- **Merge to main**: the image is built, scanned and pushed. If the lab is **off** (the normal state) nothing
  else happens, and the run summary prints the tag to use at the next lab-up:
  `terraform apply -var="lab_on=true" -var="app_image_tag=<tag>"`.
- **Lab on**: the same run also deploys the new image to ECS and smoke-tests it through CloudFront.
- **Run by hand**: Actions tab > Deploy > Run workflow.

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
- Nothing turns the lab off by itself yet. Always finish a session with `lab-down.cmd`.
