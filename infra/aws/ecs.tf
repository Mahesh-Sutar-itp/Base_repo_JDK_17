# Step 6b: ECS on Fargate.
#
# Always on (free): cluster, log group, task execution role.
# Lab only: task definition and service (2 tasks, one per zone, ~ $0.049/h for both).

locals {
  ecs_count = var.lab_on ? 1 : 0
  app_image = "${aws_ecr_repository.app.repository_url}:${var.app_image_tag}"
}

resource "aws_ecs_cluster" "main" {
  name = var.project

  # Container Insights costs extra; basic ECS metrics in CloudWatch are free
  setting {
    name  = "containerInsights"
    value = "disabled"
  }

  tags = { Name = "${var.project}-cluster" }
}

resource "aws_cloudwatch_log_group" "app" {
  name              = "/ecs/${var.project}/backend"
  retention_in_days = var.log_retention_days
  tags              = { Name = "${var.project}-backend-logs" }
}

############################
# Task execution role: used by ECS (not by the app) to pull the image,
# write logs and read the two secrets at task start.
############################
data "aws_iam_policy_document" "ecs_tasks_assume" {
  statement {
    actions = ["sts:AssumeRole"]
    principals {
      type        = "Service"
      identifiers = ["ecs-tasks.amazonaws.com"]
    }
  }
}

resource "aws_iam_role" "task_execution" {
  name               = "${var.project}-ecs-task-execution-role"
  description        = "ECS pulls the image, writes logs and reads the app secrets"
  assume_role_policy = data.aws_iam_policy_document.ecs_tasks_assume.json
  tags               = { Name = "${var.project}-ecs-task-execution-role" }
}

# AWS managed: ECR pull + CloudWatch Logs
resource "aws_iam_role_policy_attachment" "task_execution_managed" {
  role       = aws_iam_role.task_execution.name
  policy_arn = "arn:aws:iam::aws:policy/service-role/AmazonECSTaskExecutionRolePolicy"
}

data "aws_iam_policy_document" "task_execution_secrets" {
  statement {
    sid       = "ReadAppSecrets"
    actions   = ["secretsmanager:GetSecretValue"]
    resources = [aws_secretsmanager_secret.db.arn, aws_secretsmanager_secret.jwt.arn]
  }
}

resource "aws_iam_role_policy" "task_execution_secrets" {
  name   = "read-app-secrets"
  role   = aws_iam_role.task_execution.id
  policy = data.aws_iam_policy_document.task_execution_secrets.json
}

# No task role: the app itself calls no AWS APIs, so it gets no AWS permissions at all.

############################
# Task definition (lab only: it needs the RDS Proxy address)
############################
locals {
  use_db = local.db_count == 1

  # prod profile = MariaDB via RDS Proxy. Without a database (db_enabled = false) the app
  # falls back to its default profile with in-memory H2 (each task has its own copy).
  app_environment = local.use_db ? [
    { name = "SPRING_PROFILES_ACTIVE", value = "prod" },
    { name = "DB_HOST", value = aws_db_proxy.main[0].endpoint },
    { name = "DB_PORT", value = "3306" },
    { name = "DB_NAME", value = var.db_name },
    { name = "DB_POOL_SIZE", value = tostring(var.app_db_pool_size) },
    { name = "CORS_ALLOWED_ORIGINS", value = var.cors_allowed_origins },
    { name = "SWAGGER_ENABLED", value = tostring(var.swagger_enabled) },
    { name = "LOG_LEVEL", value = "INFO" },
    # Also set in application-prod.properties; repeated here so images built before that change
    # (e.g. 0d72b16) get it too. Holds DB connections only during transactions (see load test 1).
    { name = "SPRING_JPA_OPEN_IN_VIEW", value = "false" },
    # Actuator on its own port (also the prod default); the ALB health check targets it
    { name = "MANAGEMENT_SERVER_PORT", value = tostring(local.management_port) },
    ] : [
    { name = "SPRING_PROFILES_ACTIVE", value = "default" },
    # The default profile keeps Actuator on 8080; move it so the ALB health check (8081) still works
    { name = "MANAGEMENT_SERVER_PORT", value = tostring(local.management_port) },
  ]

  # Injected by ECS from Secrets Manager at start; never stored in the task definition
  app_secrets = local.use_db ? [
    { name = "DB_USERNAME", valueFrom = "${aws_secretsmanager_secret.db.arn}:username::" },
    { name = "DB_PASSWORD", valueFrom = "${aws_secretsmanager_secret.db.arn}:password::" },
    { name = "JWT_SECRET_KEY", valueFrom = aws_secretsmanager_secret.jwt.arn },
    ] : [
    { name = "TREBOL_SECURITY_JWTSECRETKEY", valueFrom = aws_secretsmanager_secret.jwt.arn },
  ]
}

resource "aws_ecs_task_definition" "app" {
  count = local.ecs_count

  family                   = "${var.project}-backend"
  requires_compatibilities = ["FARGATE"]
  network_mode             = "awsvpc"
  cpu                      = var.app_cpu
  memory                   = var.app_memory
  execution_role_arn       = aws_iam_role.task_execution.arn

  runtime_platform {
    operating_system_family = "LINUX"
    cpu_architecture        = "X86_64" # image is built on x86_64 (CloudShell / CI)
  }

  container_definitions = jsonencode([
    {
      name      = "app"
      image     = local.app_image
      essential = true

      portMappings = [
        { containerPort = local.app_port, protocol = "tcp" },        # API (user traffic)
        { containerPort = local.management_port, protocol = "tcp" }, # Actuator (health checks)
      ]

      environment = local.app_environment
      secrets     = local.app_secrets

      # Liveness only (is the JVM up?), on the Actuator port. The ALB checks the same URL.
      healthCheck = {
        command     = ["CMD-SHELL", "wget -qO- http://localhost:${local.management_port}/actuator/health/liveness || exit 1"]
        interval    = 30
        timeout     = 5
        retries     = 3
        startPeriod = 120
      }

      # Matches Spring's graceful shutdown: finish in-flight requests before stopping
      stopTimeout = 30

      logConfiguration = {
        logDriver = "awslogs"
        options = {
          awslogs-group         = aws_cloudwatch_log_group.app.name
          awslogs-region        = var.region
          awslogs-stream-prefix = "app"
        }
      }
    }
  ])

  tags = { Name = "${var.project}-backend-task" }

  # deploy.yml registers newer revisions with a different image. Terraform must not replace this one
  # (that would register a stale revision and deregister the running one). The cost: a change to the
  # container settings above (image tag, environment, secrets) only reaches the lab at the next lab-up.
  lifecycle {
    ignore_changes = [container_definitions]
  }
}

############################
# Service (lab only)
############################
resource "aws_ecs_service" "app" {
  count = local.ecs_count

  name            = "${var.project}-backend"
  cluster         = aws_ecs_cluster.main.id
  task_definition = aws_ecs_task_definition.app[0].arn
  desired_count   = var.app_desired_count
  launch_type     = "FARGATE"

  network_configuration {
    subnets          = aws_subnet.app[*].id # one task per zone
    security_groups  = [aws_security_group.app.id]
    assign_public_ip = false
  }

  load_balancer {
    target_group_arn = aws_lb_target_group.app[0].arn
    container_name   = "app"
    container_port   = local.app_port
  }

  # Spring Boot + Flyway need time to start before the ALB judges them
  health_check_grace_period_seconds = 180

  deployment_minimum_healthy_percent = 100
  deployment_maximum_percent         = 200

  # Circuit breaker is off here on purpose: on lab-up the proxy needs ~5-10 min before it
  # accepts connections, and ECS should keep retrying tasks until it does instead of
  # declaring the first deployment failed. The deploy workflow (deploy.yml) turns it on,
  # with rollback, for every rollout it starts.

  propagate_tags = "SERVICE"
  tags           = { Name = "${var.project}-backend-service" }

  lifecycle {
    ignore_changes = [
      # Auto scaling (monitoring.tf) changes the task count at run time; don't reset it
      desired_count,
      # The deploy workflow registers new task definition revisions and points the service at them.
      # Without this, any `terraform apply` while the lab is on would roll the image back to
      # var.app_image_tag. The first start after a lab-up still uses app_image_tag.
      task_definition,
      # Turned on by deploy.yml at each rollout; Terraform's config deliberately has none (see above)
      deployment_circuit_breaker,
    ]
  }

  depends_on = [aws_lb_listener.http, aws_db_proxy_target.main]
}
