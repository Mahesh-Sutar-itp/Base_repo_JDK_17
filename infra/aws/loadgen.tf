# Load generator: k6 running as ECS Fargate tasks, on test days only.
#
# Why Fargate and not EC2: the trainee account only allows small EC2 types (t2/t3.micro, t3.medium),
# while Fargate already runs the app every session, so no new kind of permission is needed. Results
# go to CloudWatch Logs; there are no instances, keys or SSH to manage.
#
# Traffic path: k6 task (private app subnet) -> NAT gateway -> internet -> CloudFront + WAF -> ALB.
# So the test goes through the real front door. The NAT gateway's public IP is added to the WAF
# load-test IP set automatically, so thousands of logins are not stopped by the login rate limit.
#
# Created only when lab_on AND loadgen_enabled. The task DEFINITION is created here; tasks are
# started for each test run with `aws ecs run-task` (see the loadgen_run_command output), and stop
# by themselves when the test ends.
#
# Cost: 4 vCPU / 8 GB per task ~ $0.20/h while running; 3 tasks for a 20-minute test ~ $0.20.

variable "loadgen_enabled" {
  description = "Create the k6 load-generator task definition (needs lab_on = true)."
  type        = bool
  default     = false
}

variable "loadgen_cpu" {
  description = "CPU units per k6 task (4096 = 4 vCPU; each task keeps ~1,700 requests in flight with no think time)."
  type        = number
  default     = 4096
}

variable "loadgen_memory" {
  description = "Memory per k6 task in MiB."
  type        = number
  default     = 8192
}

variable "loadgen_k6_image" {
  description = "k6 container image (pinned version, same as the laptop)."
  type        = string
  default     = "grafana/k6:2.4.0"
}

variable "loadgen_admin_password" {
  description = <<-EOT
    Password of the app's demo admin account, used by the k6 admin scenario. Set it in
    loadgen.auto.tfvars (git-ignored). Note: it is stored as a plain environment variable of the
    k6 task definition (visible to account users with ECS read access) - fine for the demo seed
    password, change the approach if the admin password becomes a real secret.
  EOT
  type        = string
  default     = null
  sensitive   = true
}

locals {
  loadgen_on    = var.lab_on && var.loadgen_enabled
  loadgen_count = local.loadgen_on ? 1 : 0

  # NAT gateway IPs as /32 CIDRs for the WAF IP set (only while the lab is on)
  nat_ip_cidrs = [for ip in aws_eip.nat[*].public_ip : "${ip}/32"]
}

resource "aws_cloudwatch_log_group" "loadgen" {
  count             = local.loadgen_count
  name              = "/ecs/${var.project}/k6"
  retention_in_days = var.log_retention_days
  tags              = { Name = "${var.project}-k6-logs" }
}

resource "aws_security_group" "loadgen" {
  count       = local.loadgen_count
  name        = "${var.project}-k6-sg"
  description = "k6 load generator: outbound HTTPS only (to CloudFront and the image registry)"
  vpc_id      = aws_vpc.main.id
  tags        = { Name = "${var.project}-k6-sg" }
}

resource "aws_vpc_security_group_egress_rule" "loadgen_https" {
  count             = local.loadgen_count
  security_group_id = aws_security_group.loadgen[0].id
  description       = "HTTPS to CloudFront and Docker Hub"
  ip_protocol       = "tcp"
  from_port         = 443
  to_port           = 443
  cidr_ipv4         = "0.0.0.0/0"
  tags              = { Name = "${var.project}-k6-https-out" }
}

resource "aws_ecs_task_definition" "loadgen" {
  count = local.loadgen_count

  family                   = "${var.project}-k6"
  requires_compatibilities = ["FARGATE"]
  network_mode             = "awsvpc"
  cpu                      = var.loadgen_cpu
  memory                   = var.loadgen_memory
  execution_role_arn       = aws_iam_role.task_execution.arn # pull image + write logs; no AWS access for k6

  runtime_platform {
    operating_system_family = "LINUX"
    cpu_architecture        = "X86_64"
  }

  container_definitions = jsonencode([
    {
      name      = "k6"
      image     = var.loadgen_k6_image
      essential = true

      # The script travels in an environment variable (base64), so no image build or bucket is needed.
      # K6_ARGS / INSTANCE / RUN_ID are set per task by `aws ecs run-task --overrides`.
      entryPoint = ["sh", "-c"]
      command    = ["echo \"$K6_SCRIPT_B64\" | base64 -d > /tmp/test.js && exec k6 run --quiet --no-color $K6_ARGS /tmp/test.js"]

      environment = [
        { name = "K6_SCRIPT_B64", value = filebase64("${path.module}/loadgen/k6-steps.js") },
        { name = "BASE_URL", value = "https://${aws_cloudfront_distribution.main[0].domain_name}" },
        { name = "ADMIN_PASSWORD", value = coalesce(var.loadgen_admin_password, "not-set") },
        { name = "PEAK_CONCURRENCY", value = "5000" }, # total concurrent requests over all tasks
        { name = "K6_ARGS", value = "" },
        { name = "INSTANCE", value = "0" },
      ]

      logConfiguration = {
        logDriver = "awslogs"
        options = {
          awslogs-group         = aws_cloudwatch_log_group.loadgen[0].name
          awslogs-region        = var.region
          awslogs-stream-prefix = "k6"
        }
      }
    }
  ])

  tags = { Name = "${var.project}-k6-task" }

  lifecycle {
    precondition {
      condition     = var.loadgen_admin_password != null
      error_message = "Set loadgen_admin_password in loadgen.auto.tfvars before enabling the load generator."
    }
  }
}

output "loadgen_run_command" {
  description = "Starts ONE k6 task (repeat with INSTANCE 1 and 2 and their segments to split the users over 3 tasks)."
  value = local.loadgen_on ? join(" ", [
    "aws ecs run-task --cluster ${aws_ecs_cluster.main.name} --launch-type FARGATE",
    "--task-definition ${aws_ecs_task_definition.loadgen[0].family} --propagate-tags TASK_DEFINITION",
    "--network-configuration \"awsvpcConfiguration={subnets=[${join(",", aws_subnet.app[*].id)}],securityGroups=[${aws_security_group.loadgen[0].id}],assignPublicIp=DISABLED}\"",
    "--overrides '{\"containerOverrides\":[{\"name\":\"k6\",\"environment\":[{\"name\":\"INSTANCE\",\"value\":\"0\"},{\"name\":\"RUN_ID\",\"value\":\"r1\"},{\"name\":\"K6_ARGS\",\"value\":\"--execution-segment 0:1/3 --execution-segment-sequence 0,1/3,2/3,1\"}]}]}'",
  ]) : "load generator off (needs lab_on = true and loadgen_enabled = true)"
}
