# Lab up/down from GitHub: a second GitHub OIDC role, used ONLY by .github/workflows/lab-schedule.yml,
# which runs the same `terraform apply -var lab_on=true|false` as lab-up.cmd / lab-down.cmd when started
# by hand (the weekday timetable was removed on 23 Nov 2026). See docs/ci-cd.md.
#
# Always on and free (an IAM role costs nothing).
#
# Why a separate role from the deploy role (github-oidc.tf):
#   - It needs much wider rights (create/delete NAT, ALB, RDS, RDS Proxy, CloudFront, WAF, ECS service).
#   - Only jobs that run in the GitHub environment "lab" can assume it. That environment is limited to the
#     main branch in the repository settings, and the token subject for an environment job is different
#     ("...:environment:lab"), so deploy.yml and pull requests cannot get this role.
#
# What it may NOT do: create or change IAM (no way to give itself more rights), touch S3 other than the
# Terraform state file, or write secrets. All IAM resources here are always-on, so a lab switch never
# needs IAM writes. If a merged change edits IAM (this file, github-oidc.tf, the task or proxy roles),
# the workflow run fails with AccessDenied on purpose: apply that change from the laptop first.

variable "lab_schedule_environment" {
  description = "GitHub environment whose jobs may assume the lab scheduler role (restricted to main in GitHub)."
  type        = string
  default     = "lab"
}

locals {
  account_id   = data.aws_caller_identity.current.account_id
  state_bucket = "${var.project}-tfstate-${local.account_id}"
}

data "aws_iam_policy_document" "lab_scheduler_trust" {
  statement {
    sid     = "GitHubActionsLabEnvironmentOnly"
    actions = ["sts:AssumeRoleWithWebIdentity"]

    principals {
      type        = "Federated"
      identifiers = [data.aws_iam_openid_connect_provider.github.arn]
    }

    condition {
      test     = "StringEquals"
      variable = "token.actions.githubusercontent.com:aud"
      values   = ["sts.amazonaws.com"]
    }

    # Exact match on this repo + the "lab" environment (both subject formats, as in github-oidc.tf)
    condition {
      test     = "StringEquals"
      variable = "token.actions.githubusercontent.com:sub"
      values = [
        "repo:${local.github_owner}@${var.github_owner_id}/${local.github_name}@${var.github_repo_id}:environment:${var.lab_schedule_environment}",
        "repo:${var.github_repo}:environment:${var.lab_schedule_environment}",
      ]
    }
  }
}

resource "aws_iam_role" "lab_scheduler" {
  name               = "${var.project}-lab-scheduler-role"
  description        = "GitHub Actions (${var.github_repo}, environment ${var.lab_schedule_environment}): terraform apply lab_on=true/false"
  assume_role_policy = data.aws_iam_policy_document.lab_scheduler_trust.json
  # lab-up waits for RDS, RDS Proxy and CloudFront (about 20-30 min); 2 h leaves room for a retry
  max_session_duration = 7200

  tags = { Name = "${var.project}-lab-scheduler-role" }
}

data "aws_iam_policy_document" "lab_scheduler" {
  # Terraform state: the one state file and its lock file, nothing else in S3
  statement {
    sid       = "StateBucketList"
    actions   = ["s3:ListBucket"]
    resources = ["arn:aws:s3:::${local.state_bucket}"]
  }
  statement {
    sid       = "StateFileReadWrite"
    actions   = ["s3:GetObject", "s3:PutObject", "s3:DeleteObject"]
    resources = ["arn:aws:s3:::${local.state_bucket}/trebol-poc/aws.tfstate*"]
  }

  # Read-only: Terraform refreshes every resource in the state on each run, lab on or off
  statement {
    sid = "ReadForRefresh"
    actions = [
      "application-autoscaling:Describe*",
      "application-autoscaling:ListTagsForResource",
      "budgets:ListTagsForResource",
      "budgets:ViewBudget",
      "cloudfront:Get*",
      "cloudfront:List*",
      "cloudwatch:Describe*",
      "cloudwatch:GetDashboard",
      "cloudwatch:ListDashboards",
      "cloudwatch:ListTagsForResource",
      "ec2:Describe*",
      "ec2:GetManagedPrefixListEntries",
      "ecr:DescribeImages",
      "ecr:DescribeRepositories",
      "ecr:GetLifecyclePolicy",
      "ecr:GetRepositoryPolicy",
      "ecr:ListTagsForResource",
      "ecs:Describe*",
      "ecs:List*",
      "elasticloadbalancing:Describe*",
      "iam:GetOpenIDConnectProvider",
      "iam:GetPolicy",
      "iam:GetPolicyVersion",
      "iam:GetRole",
      "iam:GetRolePolicy",
      "iam:ListAttachedRolePolicies",
      "iam:ListOpenIDConnectProviders",
      "iam:ListRolePolicies",
      "logs:DescribeLogGroups",
      "logs:ListTagsForResource",
      "logs:ListTagsLogGroup",
      "rds:Describe*",
      "rds:ListTagsForResource",
      "secretsmanager:DescribeSecret",
      "secretsmanager:GetResourcePolicy",
      "sns:GetSubscriptionAttributes",
      "sns:GetTopicAttributes",
      "sns:ListSubscriptionsByTopic",
      "sns:ListTagsForResource",
      "ssm:DescribeParameters",
      "wafv2:Get*",
      "wafv2:List*",
    ]
    resources = ["*"]
  }

  # Refreshing a secret version reads its value. The same values are already in the state file above.
  statement {
    sid       = "ReadOurSecretsForRefresh"
    actions   = ["secretsmanager:GetSecretValue"]
    resources = [aws_secretsmanager_secret.db.arn, aws_secretsmanager_secret.jwt.arn]
  }

  # Create/delete the lab-only resources. Region-locked; names scoped where AWS ARNs allow it.
  statement {
    sid = "LabNetworking"
    actions = [
      "ec2:AllocateAddress",
      "ec2:AuthorizeSecurityGroupEgress",
      "ec2:AuthorizeSecurityGroupIngress",
      "ec2:CreateNatGateway",
      "ec2:CreateRoute",
      "ec2:CreateSecurityGroup",
      "ec2:CreateTags",
      "ec2:DeleteNatGateway",
      "ec2:DeleteRoute",
      "ec2:DeleteSecurityGroup",
      "ec2:DeleteTags",
      "ec2:DisassociateAddress",
      "ec2:ModifySecurityGroupRules",
      "ec2:ReleaseAddress",
      "ec2:ReplaceRoute",
      "ec2:RevokeSecurityGroupEgress",
      "ec2:RevokeSecurityGroupIngress",
      "ec2:UpdateSecurityGroupRuleDescriptionsEgress",
      "ec2:UpdateSecurityGroupRuleDescriptionsIngress",
    ]
    resources = ["*"]
    condition {
      test     = "StringEquals"
      variable = "aws:RequestedRegion"
      values   = [var.region]
    }
  }

  statement {
    sid     = "LabLoadBalancer"
    actions = ["elasticloadbalancing:*"]
    resources = [
      "arn:aws:elasticloadbalancing:${var.region}:${local.account_id}:loadbalancer/app/${var.project}-*/*",
      "arn:aws:elasticloadbalancing:${var.region}:${local.account_id}:targetgroup/${var.project}-*/*",
      "arn:aws:elasticloadbalancing:${var.region}:${local.account_id}:listener/app/${var.project}-*/*",
      "arn:aws:elasticloadbalancing:${var.region}:${local.account_id}:listener-rule/app/${var.project}-*/*",
    ]
  }

  statement {
    sid = "LabEcsService"
    actions = [
      "ecs:CreateService",
      "ecs:DeleteService",
      "ecs:TagResource",
      "ecs:UntagResource",
      "ecs:UpdateService",
    ]
    resources = [
      "arn:aws:ecs:${var.region}:${local.account_id}:service/${aws_ecs_cluster.main.name}/*",
      "arn:aws:ecs:${var.region}:${local.account_id}:task-definition/${var.project}-*:*",
    ]
  }

  # AWS does not allow limiting these to one resource
  statement {
    sid       = "LabEcsTaskDefinitions"
    actions   = ["ecs:DeregisterTaskDefinition", "ecs:RegisterTaskDefinition"]
    resources = ["*"]
  }

  # RDS Proxy ARNs use generated IDs (prx-...), so RDS can only be region-locked
  statement {
    sid = "LabDatabase"
    actions = [
      "rds:AddTagsToResource",
      "rds:CreateDBInstance",
      "rds:CreateDBProxy",
      "rds:DeleteDBInstance",
      "rds:DeleteDBProxy",
      "rds:DeregisterDBProxyTargets",
      "rds:ModifyDBInstance",
      "rds:ModifyDBProxy",
      "rds:ModifyDBProxyTargetGroup",
      "rds:RegisterDBProxyTargets",
      "rds:RemoveTagsFromResource",
    ]
    resources = ["*"]
    condition {
      test     = "StringEquals"
      variable = "aws:RequestedRegion"
      values   = [var.region]
    }
  }

  # CloudFront, WAF (CloudFront scope lives in us-east-1), alarms, dashboard and auto scaling.
  # Target-tracking auto scaling creates its own CloudWatch alarms in the caller's name, hence alarm:*.
  statement {
    sid = "LabEdgeAndMonitoring"
    actions = [
      "application-autoscaling:DeleteScalingPolicy",
      "application-autoscaling:DeregisterScalableTarget",
      "application-autoscaling:PutScalingPolicy",
      "application-autoscaling:RegisterScalableTarget",
      "application-autoscaling:TagResource",
      "application-autoscaling:UntagResource",
      "cloudfront:CreateDistribution",
      "cloudfront:CreateDistributionWithTags",
      "cloudfront:CreateVpcOrigin",
      "cloudfront:DeleteDistribution",
      "cloudfront:DeleteVpcOrigin",
      "cloudfront:TagResource",
      "cloudfront:UntagResource",
      "cloudfront:UpdateDistribution",
      "cloudfront:UpdateVpcOrigin",
      "cloudwatch:DeleteAlarms",
      "cloudwatch:DeleteDashboards",
      "cloudwatch:PutDashboard",
      "cloudwatch:PutMetricAlarm",
      "cloudwatch:TagResource",
      "cloudwatch:UntagResource",
      "wafv2:AssociateWebACL",
      "wafv2:CreateWebACL",
      "wafv2:DeleteWebACL",
      "wafv2:DisassociateWebACL",
      "wafv2:TagResource",
      "wafv2:UntagResource",
      "wafv2:UpdateIPSet",
      "wafv2:UpdateWebACL",
    ]
    resources = ["*"]
    condition {
      test     = "StringEquals"
      variable = "aws:RequestedRegion"
      values   = [var.region]
    }
  }

  # The public URL parameter (created at lab-up, deleted at lab-down)
  statement {
    sid = "LabAppUrlParameter"
    actions = [
      "ssm:AddTagsToResource",
      "ssm:DeleteParameter",
      "ssm:GetParameter",
      "ssm:GetParameters",
      "ssm:ListTagsForResource",
      "ssm:PutParameter",
      "ssm:RemoveTagsFromResource",
    ]
    resources = ["arn:aws:ssm:${var.region}:${local.account_id}:parameter/${var.project}/*"]
  }

  # The task definition and the proxy name existing roles; they may only be handed to their own service
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
  statement {
    sid       = "PassProxyRoleToRds"
    actions   = ["iam:PassRole"]
    resources = [aws_iam_role.proxy.arn]
    condition {
      test     = "StringEquals"
      variable = "iam:PassedToService"
      values   = ["rds.amazonaws.com"]
    }
  }

  # AWS-defined service roles; they already exist after the first lab-up, this only covers a fresh account
  statement {
    sid       = "ServiceLinkedRolesOnly"
    actions   = ["iam:CreateServiceLinkedRole"]
    resources = ["*"]
    condition {
      test     = "StringEquals"
      variable = "iam:AWSServiceName"
      values = [
        "ecs.amazonaws.com",
        "ecs.application-autoscaling.amazonaws.com",
        "elasticloadbalancing.amazonaws.com",
        "rds.amazonaws.com",
        "vpcorigin.cloudfront.amazonaws.com",
      ]
    }
  }
}

resource "aws_iam_role_policy" "lab_scheduler" {
  name   = "terraform-lab-on-off"
  role   = aws_iam_role.lab_scheduler.id
  policy = data.aws_iam_policy_document.lab_scheduler.json
}

output "lab_scheduler_role_arn" {
  description = "Role ARN for lab-schedule.yml (GitHub environment variable LAB_ROLE_ARN). Not a secret."
  value       = aws_iam_role.lab_scheduler.arn
}
