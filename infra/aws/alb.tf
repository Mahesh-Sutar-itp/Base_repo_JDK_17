# Step 6a: internal Application Load Balancer (lab only, ~ $0.03/h while it exists).
#
# Internal = no public IP. It sits in the private app subnets and is reached only by
# CloudFront through a VPC origin (Step 7). It spreads requests over the ECS tasks
# and only sends traffic to tasks whose readiness check passes (app + database OK).

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

  # Readiness includes the database: a task that can't reach MariaDB gets no traffic
  health_check {
    path                = "/actuator/health/readiness"
    matcher             = "200"
    interval            = 15
    timeout             = 5
    healthy_threshold   = 2
    unhealthy_threshold = 3
  }

  # Give in-flight requests 30 s to finish when a task stops (matches graceful shutdown)
  deregistration_delay = 30

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
