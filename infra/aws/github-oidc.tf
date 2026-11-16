# Phase 5, step 5.1: lets GitHub Actions work in AWS without any stored access keys.
#
# How it works: when a workflow runs, GitHub gives it a short-lived signed token (OIDC) that says
# which repo and branch it comes from. AWS checks that token against this role's trust policy and,
# if it matches, hands the workflow temporary credentials (1 hour). Nothing secret is stored in
# GitHub, so there is nothing to leak from this public repo.
#
# Always on and free (an IAM role costs nothing).
#
# The account already has a GitHub OIDC provider (created by the cloud team and shared by other
# projects), so we only LOOK IT UP. Creating or deleting it here could break other teams.
#
# Who may use the role: ONLY workflows running on the main branch of var.github_repo.
# Pull requests (including from forks) get a different token subject, so they cannot assume it.
# Pull requests only build and test; they never need AWS.
#
# What the role may do (least privilege):
#   - push images to our one ECR repository
#   - register a new task definition and update our one ECS service (deploy)
#   - pass the task execution role to ECS (required to register a task definition)
#   - read one SSM parameter (the public URL, for the smoke test)
# It cannot touch the database, secrets, networking, IAM or Terraform state.

variable "github_repo" {
  description = "GitHub repository allowed to assume the CI/CD role, as owner/name."
  type        = string
  default     = "nancy-itp/Base_repo_JDK_17"

  validation {
    condition     = can(regex("^[A-Za-z0-9-]+/[A-Za-z0-9._-]+$", var.github_repo))
    error_message = "github_repo must look like owner/name."
  }
}

variable "github_owner_id" {
  description = "Numeric GitHub ID of the repository owner (public: api.github.com/users/<owner>)."
  type        = string
  default     = "300505377"
}

variable "github_repo_id" {
  description = "Numeric GitHub ID of the repository (public: api.github.com/repos/<owner>/<name>)."
  type        = string
  default     = "1445925961"
}

locals {
  github_owner = split("/", var.github_repo)[0]
  github_name  = split("/", var.github_repo)[1]
}

variable "github_branch" {
  description = "The only branch whose workflows may assume the CI/CD role."
  type        = string
  default     = "main"
}

data "aws_iam_openid_connect_provider" "github" {
  url = "https://token.actions.githubusercontent.com"
}

data "aws_iam_policy_document" "github_trust" {
  statement {
    sid     = "GitHubActionsOnMainOnly"
    actions = ["sts:AssumeRoleWithWebIdentity"]

    principals {
      type        = "Federated"
      identifiers = [data.aws_iam_openid_connect_provider.github.arn]
    }

    # The token must be meant for AWS...
    condition {
      test     = "StringEquals"
      variable = "token.actions.githubusercontent.com:aud"
      values   = ["sts.amazonaws.com"]
    }

    # ...and come from this exact repo and branch (exact match, no wildcards).
    # GitHub now puts the numeric owner and repository IDs into the token subject
    # (repo:owner@ID/name@ID:ref:...), so that is the format that actually arrives. The old
    # name-only format is kept too, in case a repository still sends it. The IDs are public.
    condition {
      test     = "StringEquals"
      variable = "token.actions.githubusercontent.com:sub"
      values = [
        "repo:${local.github_owner}@${var.github_owner_id}/${local.github_name}@${var.github_repo_id}:ref:refs/heads/${var.github_branch}",
        "repo:${var.github_repo}:ref:refs/heads/${var.github_branch}",
      ]
    }
  }
}

resource "aws_iam_role" "github_actions" {
  name                 = "${var.project}-github-actions-role"
  description          = "GitHub Actions (${var.github_repo}, ${var.github_branch} branch): push to ECR and deploy to ECS"
  assume_role_policy   = data.aws_iam_policy_document.github_trust.json
  max_session_duration = 3600

  tags = { Name = "${var.project}-github-actions-role" }
}

data "aws_iam_policy_document" "github_actions" {
  # Needed to log in to ECR; this call cannot be limited to one repository
  statement {
    sid       = "EcrLogin"
    actions   = ["ecr:GetAuthorizationToken"]
    resources = ["*"]
  }

  # Push only (the workflow never pulls from ECR) + DescribeImages for the "already in ECR?" check
  statement {
    sid = "EcrPushOurRepoOnly"
    actions = [
      "ecr:BatchCheckLayerAvailability",
      "ecr:CompleteLayerUpload",
      "ecr:DescribeImages",
      "ecr:InitiateLayerUpload",
      "ecr:PutImage",
      "ecr:UploadLayerPart",
    ]
    resources = [aws_ecr_repository.app.arn]
  }

  # AWS does not allow limiting these to one resource
  statement {
    sid = "EcsTaskDefinitions"
    actions = [
      "ecs:DescribeTaskDefinition",
      "ecs:RegisterTaskDefinition",
    ]
    resources = ["*"]
  }

  # New revisions carry the same Owner/Project/Name tags as the first one (organization tag rule)
  statement {
    sid       = "EcsTagOurTaskDefinitionRevisions"
    actions   = ["ecs:TagResource"]
    resources = ["arn:aws:ecs:${var.region}:${data.aws_caller_identity.current.account_id}:task-definition/${var.project}-backend:*"]
  }

  statement {
    sid     = "EcsDeployOurServiceOnly"
    actions = ["ecs:DescribeServices", "ecs:UpdateService"]
    resources = [
      "arn:aws:ecs:${var.region}:${data.aws_caller_identity.current.account_id}:service/${aws_ecs_cluster.main.name}/${var.project}-backend",
    ]
  }

  # The public URL published at lab-up (see cloudfront.tf): tells the deploy workflow whether the lab
  # is on and where to send the smoke test. Read-only, that one parameter.
  statement {
    sid       = "ReadAppUrlParameter"
    actions   = ["ssm:GetParameter"]
    resources = ["arn:aws:ssm:${var.region}:${data.aws_caller_identity.current.account_id}:parameter/${var.project}/app-url"]
  }

  # A new task definition names the task execution role, so the deployer must be allowed to pass it,
  # and only to ECS tasks
  statement {
    sid       = "PassTaskExecutionRoleToEcs"
    actions   = ["iam:PassRole"]
    resources = [aws_iam_role.task_execution.arn]

    condition {
      test     = "StringEquals"
      variable = "iam:PassedToService"
      values   = ["ecs-tasks.amazonaws.com"]
    }
  }
}

resource "aws_iam_role_policy" "github_actions" {
  name   = "ecr-push-and-ecs-deploy"
  role   = aws_iam_role.github_actions.id
  policy = data.aws_iam_policy_document.github_actions.json
}

output "github_actions_role_arn" {
  description = "Role ARN for the workflows (aws-actions/configure-aws-credentials role-to-assume). Not a secret."
  value       = aws_iam_role.github_actions.arn
}
