# Step 4: security groups (free).
#
# Traffic is allowed only along the request path, one hop at a time:
#
#   CloudFront --80--> ALB --8080--> ECS tasks --3306--> RDS Proxy --3306--> MariaDB
#                                         \--443--> AWS APIs (ECR, Secrets Manager, CloudWatch)
#
# Groups reference each other instead of IP ranges, so new tasks are covered automatically.
# Terraform removes AWS's default "allow all outbound" rule from every group it creates,
# so only the rules below exist.

locals {
  app_port = 8080
  db_port  = 3306
}

resource "aws_security_group" "alb" {
  name        = "${var.project}-alb-sg"
  description = "Internal ALB: reached only by CloudFront (VPC origin)"
  vpc_id      = aws_vpc.main.id
  tags        = { Name = "${var.project}-alb-sg" }
}

resource "aws_security_group" "app" {
  name        = "${var.project}-app-sg"
  description = "ECS Fargate tasks: reached only by the ALB"
  vpc_id      = aws_vpc.main.id
  tags        = { Name = "${var.project}-app-sg" }
}

resource "aws_security_group" "proxy" {
  name        = "${var.project}-rds-proxy-sg"
  description = "RDS Proxy: reached only by the ECS tasks"
  vpc_id      = aws_vpc.main.id
  tags        = { Name = "${var.project}-rds-proxy-sg" }
}

resource "aws_security_group" "db" {
  name        = "${var.project}-db-sg"
  description = "MariaDB: reached only by RDS Proxy"
  vpc_id      = aws_vpc.main.id
  tags        = { Name = "${var.project}-db-sg" }
}

############################
# ALB
############################
# Inbound from CloudFront is added in the CloudFront step: AWS creates the
# "CloudFront-VPCOrigins-Service-SG" group only when the VPC origin is created,
# and the rule must reference that group.

resource "aws_vpc_security_group_egress_rule" "alb_to_app" {
  security_group_id            = aws_security_group.alb.id
  description                  = "Forward requests and health checks to the app"
  ip_protocol                  = "tcp"
  from_port                    = local.app_port
  to_port                      = local.app_port
  referenced_security_group_id = aws_security_group.app.id
  tags                         = { Name = "${var.project}-alb-to-app" }
}

############################
# ECS tasks
############################
resource "aws_vpc_security_group_ingress_rule" "app_from_alb" {
  security_group_id            = aws_security_group.app.id
  description                  = "Requests and health checks from the ALB"
  ip_protocol                  = "tcp"
  from_port                    = local.app_port
  to_port                      = local.app_port
  referenced_security_group_id = aws_security_group.alb.id
  tags                         = { Name = "${var.project}-app-from-alb" }
}

resource "aws_vpc_security_group_egress_rule" "app_to_proxy" {
  security_group_id            = aws_security_group.app.id
  description                  = "Database connections through RDS Proxy"
  ip_protocol                  = "tcp"
  from_port                    = local.db_port
  to_port                      = local.db_port
  referenced_security_group_id = aws_security_group.proxy.id
  tags                         = { Name = "${var.project}-app-to-proxy" }
}

# HTTPS out (via the NAT gateway, or the S3 endpoint for image layers):
# pull images from ECR, read secrets, send logs to CloudWatch.
resource "aws_vpc_security_group_egress_rule" "app_https_out" {
  security_group_id = aws_security_group.app.id
  description       = "HTTPS to AWS APIs (ECR, Secrets Manager, CloudWatch Logs)"
  ip_protocol       = "tcp"
  from_port         = 443
  to_port           = 443
  cidr_ipv4         = "0.0.0.0/0"
  tags              = { Name = "${var.project}-app-https-out" }
}

############################
# RDS Proxy
############################
resource "aws_vpc_security_group_ingress_rule" "proxy_from_app" {
  security_group_id            = aws_security_group.proxy.id
  description                  = "Database connections from the ECS tasks"
  ip_protocol                  = "tcp"
  from_port                    = local.db_port
  to_port                      = local.db_port
  referenced_security_group_id = aws_security_group.app.id
  tags                         = { Name = "${var.project}-proxy-from-app" }
}

resource "aws_vpc_security_group_egress_rule" "proxy_to_db" {
  security_group_id            = aws_security_group.proxy.id
  description                  = "Pooled connections to MariaDB"
  ip_protocol                  = "tcp"
  from_port                    = local.db_port
  to_port                      = local.db_port
  referenced_security_group_id = aws_security_group.db.id
  tags                         = { Name = "${var.project}-proxy-to-db" }
}

############################
# MariaDB
############################
resource "aws_vpc_security_group_ingress_rule" "db_from_proxy" {
  security_group_id            = aws_security_group.db.id
  description                  = "Connections from RDS Proxy only"
  ip_protocol                  = "tcp"
  from_port                    = local.db_port
  to_port                      = local.db_port
  referenced_security_group_id = aws_security_group.proxy.id
  tags                         = { Name = "${var.project}-db-from-proxy" }
}
# No outbound rules: the database never starts connections.

############################
# VPC default security group: remove all its rules, so anything launched
# without an explicit group gets no access at all (AWS security best practice).
############################
resource "aws_default_security_group" "default" {
  vpc_id = aws_vpc.main.id
  tags   = { Name = "${var.project}-default-sg-locked" }
}
