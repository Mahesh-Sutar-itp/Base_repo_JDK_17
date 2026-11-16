# infra/bootstrap – Terraform state bucket (Phase 4, Step 0)

Creates `trebol-poc-tfstate-<account-id>` in us-east-1: private, versioned,
SSE-S3 encrypted, HTTPS-only, old versions expire after 30 days.
State for this stack stays local (it creates the remote-state bucket).
No DynamoDB table: the main stack uses the S3 backend's `use_lockfile`.

Run once, from cmd:

    cd C:\POC\Base_repo_JDK_17\infra\bootstrap
    terraform init
    terraform fmt -check
    terraform validate
    terraform plan -out tfplan
    terraform apply tfplan

Cost: a few KB of state, effectively $0.
Keep `terraform.tfstate` from this folder somewhere safe (not in git). If it
is lost, re-adopt the bucket with `terraform import aws_s3_bucket.tfstate <name>`.
