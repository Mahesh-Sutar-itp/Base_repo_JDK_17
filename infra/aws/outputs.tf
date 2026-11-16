output "vpc_id" {
  value = aws_vpc.main.id
}

output "availability_zones" {
  value = local.azs
}

output "public_subnet_ids" {
  value = aws_subnet.public[*].id
}

output "app_subnet_ids" {
  value = aws_subnet.app[*].id
}

output "db_subnet_ids" {
  value = aws_subnet.db[*].id
}

output "nat_gateway_public_ips" {
  description = "Outbound IPs of the ECS tasks (useful if a third party needs to allow-list them)"
  value       = aws_eip.nat[*].public_ip
}

output "lab_status" {
  value = var.lab_on ? "ON - hourly-billed resources are running" : "OFF - only free/near-free resources exist"
}

output "ecr_repository_url" {
  description = "Push images here: <url>:<tag>"
  value       = aws_ecr_repository.app.repository_url
}

output "db_secret_arn" {
  description = "Secrets Manager ARN of the DB credentials (values are not printed)"
  value       = aws_secretsmanager_secret.db.arn
}

output "jwt_secret_arn" {
  description = "Secrets Manager ARN of the JWT signing key (value is not printed)"
  value       = aws_secretsmanager_secret.jwt.arn
}

output "security_group_ids" {
  value = {
    alb   = aws_security_group.alb.id
    app   = aws_security_group.app.id
    proxy = aws_security_group.proxy.id
    db    = aws_security_group.db.id
  }
}

output "db_proxy_endpoint" {
  description = "What the app uses as DB_HOST (lab on only)"
  value       = local.db_count == 1 ? aws_db_proxy.main[0].endpoint : "no database (lab off or db_enabled = false)"
}

output "alb_dns_name" {
  description = "Internal ALB address (reachable only inside the VPC / via CloudFront)"
  value       = var.lab_on ? aws_lb.app[0].dns_name : "lab off - no load balancer"
}

output "ecs_cluster" {
  value = aws_ecs_cluster.main.name
}

output "app_url" {
  description = "Public HTTPS address of the API (changes every lab session)"
  value       = var.lab_on ? "https://${aws_cloudfront_distribution.main[0].domain_name}" : "lab off - no public URL"
}

output "alarm_topic_arn" {
  description = "SNS topic that receives CloudWatch alarm notifications"
  value       = aws_sns_topic.alarms.arn
}

output "dashboard_url" {
  description = "CloudWatch dashboard (lab on only)"
  value       = var.lab_on ? "https://${var.region}.console.aws.amazon.com/cloudwatch/home?region=${var.region}#dashboards/dashboard/${aws_cloudwatch_dashboard.main[0].dashboard_name}" : "lab off - no dashboard"
}
