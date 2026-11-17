# Step 6a: internal Application Load Balancer (lab only, ~ $0.03/h while it exists).
#
# Internal = no public IP. It sits in the private app subnets and is reached only by
# CloudFront through a VPC origin (Step 7). It spreads requests over the ECS tasks
# and only sends traffic to tasks whose liveness check passes (JVM/app alive).

locals {
  alb_count = var.lab_on ? 1 : 0
}

resource "aws_lb" "app" {
  count = local.alb_count

  name               = "${var.project}-alb"
  internal           = true
  load_balancer_type = "application"
  subnets            = aws_subnet.app[*].id
  security_groups    = [aws_security_group.alb.id]

  drop_invalid_header_fields = true # reject malformed headers (request-smuggling protection)
  idle_timeout               = 60

  tags = { Name = "${var.project}-alb" }
}

resource "aws_lb_target_group" "app" {
  count = local.alb_count

  name        = "${var.project}-app-tg"
  port        = local.app_port
  protocol    = "HTTP"
  target_type = "ip" # Fargate tasks register by IP
  vpc_id      = aws_vpc.main.id

  # Liveness on the separate Actuator port (8081): "is the app alive?" only.
  # - No database check: in load test r11171916 the readiness check (which waits for a DB connection)
  #   timed out at peak and ECS replaced 6 busy-but-working tasks, making the overload worse.
  # - Own port = own Tomcat threads, so the check doesn't queue behind user requests on 8080.
  # A task that can't reach the database still gets traffic; those requests fail fast (Hikari
  # connection-timeout 5 s -> 503 + Retry-After) and the alarms in monitoring.tf report it.
  # 5 failures x 15 s = 75 s of consecutive failures before a target is marked unhealthy.
  health_check {
    port                = tostring(local.management_port)
    path                = "/actuator/health/liveness"
    interval            = 15
    timeout             = 5
    healthy_threshold   = 2
    unhealthy_threshold = 5
    matcher             = "200"
  }

  # Give in-flight requests 30 s to finish when a task stops (matches graceful shutdown)
  deregistration_delay = 30

  # Send each request to the task with the fewest requests in progress (default is round robin).
  # Requests differ a lot in cost (a sign-up hashes a password with BCrypt, a product list does not),
  # so round robin can overload one task while another is idle. A newly started task (cold JVM) is
  # slower, keeps more requests in progress, and so automatically gets less traffic until it warms up.
  # (AWS does not allow slow_start together with this algorithm.)
  load_balancing_algorithm_type = "least_outstanding_requests"

  tags = { Name = "${var.project}-app-tg" }
}

# HTTP only: traffic between CloudFront and this internal ALB stays on the AWS network.
# Users connect to CloudFront over HTTPS.
resource "aws_lb_listener" "http" {
  count = local.alb_count

  load_balancer_arn = aws_lb.app[0].arn
  port              = 80
  protocol          = "HTTP"

  default_action {
    type             = "forward"
    target_group_arn = aws_lb_target_group.app[0].arn
  }

  tags = { Name = "${var.project}-http-listener" }
}
