# Step 8: auto scaling, alarms, dashboard.
#
# Always on (free): SNS alarm topic (+ email subscription if alarm_email is set).
# Lab only (they watch lab resources): ECS auto scaling, CloudWatch alarms, dashboard.
#
# Auto scaling keeps at least app_desired_count tasks (one per zone) and adds tasks, up to
# autoscaling_max_tasks, when average CPU or requests per task rise. Three policies; if they want a
# different size, the larger one wins:
#   - target tracking on CPU (50%) and on requests per task: steady, gradual scaling
#   - step scaling: emergency +2 tasks after one minute above 70% CPU (sudden surges)
# Scale-out is quick (60 s), scale-in slow (5 min) so a short dip in traffic does not remove tasks.

locals {
  monitor_count = var.lab_on ? 1 : 0

  # Names the CloudWatch metrics use to identify our resources
  alb_dimension = var.lab_on ? aws_lb.app[0].arn_suffix : ""
  tg_dimension  = var.lab_on ? aws_lb_target_group.app[0].arn_suffix : ""

  # one() gives null (instead of an error) when the resource does not exist, because
  # locals are evaluated even while the lab is off
  ecs_service_name = one(aws_ecs_service.app[*].name)
  db_identifier    = one(aws_db_instance.main[*].identifier)
  db_proxy_name    = one(aws_db_proxy.main[*].name)
  cdn_id           = one(aws_cloudfront_distribution.main[*].id)
}

############################
# SNS topic for alarm notifications
############################
resource "aws_sns_topic" "alarms" {
  name = "${var.project}-alarms"
  tags = { Name = "${var.project}-alarms" }
}

# Email subscriptions stay "pending" until the recipient clicks the link AWS emails them.
resource "aws_sns_topic_subscription" "email" {
  count = var.alarm_email == null ? 0 : 1

  topic_arn = aws_sns_topic.alarms.arn
  protocol  = "email"
  endpoint  = var.alarm_email
}

# Only CloudWatch alarms of this account may publish to the topic
data "aws_caller_identity" "current" {}

data "aws_iam_policy_document" "alarms_topic" {
  statement {
    sid       = "AllowCloudWatchAlarms"
    actions   = ["sns:Publish"]
    resources = [aws_sns_topic.alarms.arn]

    principals {
      type        = "Service"
      identifiers = ["cloudwatch.amazonaws.com"]
    }
    condition {
      test     = "StringEquals"
      variable = "aws:SourceAccount"
      values   = [data.aws_caller_identity.current.account_id]
    }
  }
}

resource "aws_sns_topic_policy" "alarms" {
  arn    = aws_sns_topic.alarms.arn
  policy = data.aws_iam_policy_document.alarms_topic.json
}

############################
# ECS service auto scaling (lab only)
############################
resource "aws_appautoscaling_target" "app" {
  count = local.monitor_count

  service_namespace  = "ecs"
  scalable_dimension = "ecs:service:DesiredCount"
  resource_id        = "service/${aws_ecs_cluster.main.name}/${aws_ecs_service.app[0].name}"
  min_capacity       = var.app_desired_count
  max_capacity       = var.autoscaling_max_tasks

  tags = { Name = "${var.project}-backend-scaling" }
}

resource "aws_appautoscaling_policy" "cpu" {
  count = local.monitor_count

  name               = "${var.project}-cpu-target"
  policy_type        = "TargetTrackingScaling"
  service_namespace  = aws_appautoscaling_target.app[0].service_namespace
  scalable_dimension = aws_appautoscaling_target.app[0].scalable_dimension
  resource_id        = aws_appautoscaling_target.app[0].resource_id

  target_tracking_scaling_policy_configuration {
    target_value       = var.autoscaling_cpu_target
    scale_out_cooldown = 60
    scale_in_cooldown  = 300

    predefined_metric_specification {
      predefined_metric_type = "ECSServiceAverageCPUUtilization"
    }
  }
}

resource "aws_appautoscaling_policy" "requests" {
  count = local.monitor_count

  name               = "${var.project}-requests-target"
  policy_type        = "TargetTrackingScaling"
  service_namespace  = aws_appautoscaling_target.app[0].service_namespace
  scalable_dimension = aws_appautoscaling_target.app[0].scalable_dimension
  resource_id        = aws_appautoscaling_target.app[0].resource_id

  target_tracking_scaling_policy_configuration {
    target_value       = var.autoscaling_requests_per_target
    scale_out_cooldown = 60
    scale_in_cooldown  = 300

    predefined_metric_specification {
      predefined_metric_type = "ALBRequestCountPerTarget"
      resource_label         = "${local.alb_dimension}/${local.tg_dimension}"
    }
  }
}

# Emergency scale-out (step scaling). Target tracking above waits for 3 one-minute data points, and a
# new task then needs ~2-3 min to start and pass health checks, so a sudden surge reached 100% CPU
# before help arrived. This alarm fires after ONE minute above the threshold and adds several tasks at
# once. When both policies want a size, Application Auto Scaling uses the larger one.
# Scale-in stays with target tracking (slow and gradual).
resource "aws_appautoscaling_policy" "cpu_fast_out" {
  count = local.monitor_count

  name               = "${var.project}-cpu-fast-scale-out"
  policy_type        = "StepScaling"
  service_namespace  = aws_appautoscaling_target.app[0].service_namespace
  scalable_dimension = aws_appautoscaling_target.app[0].scalable_dimension
  resource_id        = aws_appautoscaling_target.app[0].resource_id

  step_scaling_policy_configuration {
    adjustment_type         = "ChangeInCapacity"
    cooldown                = 120 # new tasks need time to start before the next step
    metric_aggregation_type = "Average"

    step_adjustment {
      metric_interval_lower_bound = 0 # anything above the alarm threshold
      scaling_adjustment          = var.autoscaling_fast_step_tasks
    }
  }
}

resource "aws_cloudwatch_metric_alarm" "cpu_fast_out" {
  count = local.monitor_count

  alarm_name          = "${var.project}-cpu-fast-scale-out"
  alarm_description   = "Average task CPU above ${var.autoscaling_fast_cpu_threshold}% for 1 minute: add ${var.autoscaling_fast_step_tasks} tasks now"
  namespace           = "AWS/ECS"
  metric_name         = "CPUUtilization"
  dimensions          = { ClusterName = aws_ecs_cluster.main.name, ServiceName = aws_ecs_service.app[0].name }
  statistic           = "Average"
  period              = 60
  evaluation_periods  = 1
  threshold           = var.autoscaling_fast_cpu_threshold
  comparison_operator = "GreaterThanThreshold"
  treat_missing_data  = "notBreaching"
  alarm_actions       = [aws_appautoscaling_policy.cpu_fast_out[0].arn] # scaling only, no email

  tags = { Name = "${var.project}-cpu-fast-scale-out" }
}

############################
# Alarms (lab only). Missing data = no problem (e.g. no traffic).
############################
resource "aws_cloudwatch_metric_alarm" "alb_5xx" {
  count = local.monitor_count

  alarm_name          = "${var.project}-app-5xx-errors"
  alarm_description   = "The app returned more than 10 server errors (5xx) in 5 minutes"
  namespace           = "AWS/ApplicationELB"
  metric_name         = "HTTPCode_Target_5XX_Count"
  dimensions          = { LoadBalancer = local.alb_dimension, TargetGroup = local.tg_dimension }
  statistic           = "Sum"
  period              = 300
  evaluation_periods  = 1
  threshold           = 10
  comparison_operator = "GreaterThanThreshold"
  treat_missing_data  = "notBreaching"
  alarm_actions       = [aws_sns_topic.alarms.arn]
  ok_actions          = [aws_sns_topic.alarms.arn]

  tags = { Name = "${var.project}-app-5xx-errors" }
}

resource "aws_cloudwatch_metric_alarm" "latency_p95" {
  count = local.monitor_count

  alarm_name          = "${var.project}-latency-p95"
  alarm_description   = "p95 response time above ${var.latency_alarm_seconds} s (goal of the load test) in 3 of 5 minutes"
  namespace           = "AWS/ApplicationELB"
  metric_name         = "TargetResponseTime"
  dimensions          = { LoadBalancer = local.alb_dimension, TargetGroup = local.tg_dimension }
  extended_statistic  = "p95"
  period              = 60
  evaluation_periods  = 5
  datapoints_to_alarm = 3
  threshold           = var.latency_alarm_seconds
  comparison_operator = "GreaterThanThreshold"
  treat_missing_data  = "notBreaching"
  alarm_actions       = [aws_sns_topic.alarms.arn]
  ok_actions          = [aws_sns_topic.alarms.arn]

  tags = { Name = "${var.project}-latency-p95" }
}

resource "aws_cloudwatch_metric_alarm" "unhealthy_hosts" {
  count = local.monitor_count

  alarm_name          = "${var.project}-unhealthy-tasks"
  alarm_description   = "At least one task has failed the ALB readiness check for 3 minutes"
  namespace           = "AWS/ApplicationELB"
  metric_name         = "UnHealthyHostCount"
  dimensions          = { LoadBalancer = local.alb_dimension, TargetGroup = local.tg_dimension }
  statistic           = "Maximum"
  period              = 60
  evaluation_periods  = 3
  threshold           = 0
  comparison_operator = "GreaterThanThreshold"
  treat_missing_data  = "notBreaching"
  alarm_actions       = [aws_sns_topic.alarms.arn]
  ok_actions          = [aws_sns_topic.alarms.arn]

  tags = { Name = "${var.project}-unhealthy-tasks" }
}

# Total-outage alarms. The alarms above stay silent when there is NO data, which is exactly what
# happens when every task is gone (no targets = no per-target metrics). These two cover that case.
# Missing data counts as a problem here, and 10 minutes must pass first so the normal lab-up
# start-up (database proxy ~5-10 min, then the app ~1-2 min) does not send a false alarm.
resource "aws_cloudwatch_metric_alarm" "no_healthy_tasks" {
  count = local.monitor_count

  alarm_name          = "${var.project}-no-healthy-tasks"
  alarm_description   = "No task has passed the ALB readiness check for 10 minutes: the API is down"
  namespace           = "AWS/ApplicationELB"
  metric_name         = "HealthyHostCount"
  dimensions          = { LoadBalancer = local.alb_dimension, TargetGroup = local.tg_dimension }
  statistic           = "Maximum"
  period              = 60
  evaluation_periods  = 10
  threshold           = 1
  comparison_operator = "LessThanThreshold"
  treat_missing_data  = "breaching"
  alarm_actions       = [aws_sns_topic.alarms.arn]
  ok_actions          = [aws_sns_topic.alarms.arn]

  tags = { Name = "${var.project}-no-healthy-tasks" }
}

# 5xx answers made by the load balancer itself (502/503/504: no target, target closed or timed out).
# The 5xx alarm above counts only errors the app returned.
resource "aws_cloudwatch_metric_alarm" "alb_own_5xx" {
  count = local.monitor_count

  alarm_name          = "${var.project}-alb-5xx-errors"
  alarm_description   = "The load balancer itself returned more than 10 server errors (5xx) in 5 minutes"
  namespace           = "AWS/ApplicationELB"
  metric_name         = "HTTPCode_ELB_5XX_Count"
  dimensions          = { LoadBalancer = local.alb_dimension }
  statistic           = "Sum"
  period              = 300
  evaluation_periods  = 1
  threshold           = 10
  comparison_operator = "GreaterThanThreshold"
  treat_missing_data  = "notBreaching"
  alarm_actions       = [aws_sns_topic.alarms.arn]
  ok_actions          = [aws_sns_topic.alarms.arn]

  tags = { Name = "${var.project}-alb-5xx-errors" }
}

resource "aws_cloudwatch_metric_alarm" "ecs_cpu" {
  count = local.monitor_count

  alarm_name          = "${var.project}-ecs-cpu-high"
  alarm_description   = "Average task CPU above 85% for 5 minutes (auto scaling is not keeping up or is at its maximum)"
  namespace           = "AWS/ECS"
  metric_name         = "CPUUtilization"
  dimensions          = { ClusterName = aws_ecs_cluster.main.name, ServiceName = aws_ecs_service.app[0].name }
  statistic           = "Average"
  period              = 60
  evaluation_periods  = 5
  threshold           = 85
  comparison_operator = "GreaterThanThreshold"
  treat_missing_data  = "notBreaching"
  alarm_actions       = [aws_sns_topic.alarms.arn]
  ok_actions          = [aws_sns_topic.alarms.arn]

  tags = { Name = "${var.project}-ecs-cpu-high" }
}

# Database alarm only exists when a database does (lab on and db_enabled)
resource "aws_cloudwatch_metric_alarm" "db_cpu" {
  count = local.db_count

  alarm_name          = "${var.project}-db-cpu-high"
  alarm_description   = "Database CPU above 80% for 5 minutes (db.t3.micro is the first thing to saturate under load)"
  namespace           = "AWS/RDS"
  metric_name         = "CPUUtilization"
  dimensions          = { DBInstanceIdentifier = aws_db_instance.main[0].identifier }
  statistic           = "Average"
  period              = 60
  evaluation_periods  = 5
  threshold           = 80
  comparison_operator = "GreaterThanThreshold"
  treat_missing_data  = "notBreaching"
  alarm_actions       = [aws_sns_topic.alarms.arn]
  ok_actions          = [aws_sns_topic.alarms.arn]

  tags = { Name = "${var.project}-db-cpu-high" }
}

############################
# Dashboard (lab only). The first 3 dashboards of an account are free.
############################
locals {
  dashboard = {
    widgets = [
      {
        type = "metric", x = 0, y = 0, width = 12, height = 6
        properties = {
          title  = "Requests per minute and errors"
          region = var.region
          view   = "timeSeries"
          stat   = "Sum"
          period = 60
          metrics = [
            ["AWS/ApplicationELB", "RequestCount", "LoadBalancer", local.alb_dimension, { label = "Requests" }],
            [".", "HTTPCode_Target_5XX_Count", ".", ".", { label = "5xx errors", color = "#d62728" }],
            [".", "HTTPCode_Target_4XX_Count", ".", ".", { label = "4xx" }],
          ]
        }
      },
      {
        type = "metric", x = 12, y = 0, width = 12, height = 6
        properties = {
          title  = "Response time (goal: p95 under ${var.latency_alarm_seconds} s)"
          region = var.region
          view   = "timeSeries"
          period = 60
          metrics = [
            ["AWS/ApplicationELB", "TargetResponseTime", "LoadBalancer", local.alb_dimension, { stat = "p95", label = "p95" }],
            ["...", { stat = "p50", label = "p50" }],
            ["...", { stat = "p99", label = "p99" }],
          ]
          annotations = { horizontal = [{ label = "goal", value = var.latency_alarm_seconds }] }
        }
      },
      {
        type = "metric", x = 0, y = 6, width = 12, height = 6
        properties = {
          title  = "ECS tasks: CPU, memory, healthy tasks"
          region = var.region
          view   = "timeSeries"
          period = 60
          metrics = [
            ["AWS/ECS", "CPUUtilization", "ClusterName", aws_ecs_cluster.main.name, "ServiceName", local.ecs_service_name, { stat = "Average", label = "CPU %" }],
            [".", "MemoryUtilization", ".", ".", ".", ".", { stat = "Average", label = "Memory %" }],
            ["AWS/ApplicationELB", "HealthyHostCount", "LoadBalancer", local.alb_dimension, "TargetGroup", local.tg_dimension, { stat = "Minimum", label = "Healthy tasks", yAxis = "right" }],
          ]
        }
      },
      # Load-test view of the front door. CloudFront and WAF (CloudFront scope) metrics live in
      # us-east-1, which is our region. Rule "ALL" = the whole web ACL; the rate-limit rule is
      # plotted on its own to show when /public/login starts answering 429.
      {
        type = "metric", x = 0, y = 12, width = 12, height = 6
        properties = {
          title  = "CloudFront: requests and error rate"
          region = var.region
          view   = "timeSeries"
          period = 60
          metrics = [
            ["AWS/CloudFront", "Requests", "DistributionId", local.cdn_id, "Region", "Global", { stat = "Sum", label = "Requests" }],
            [".", "5xxErrorRate", ".", ".", ".", ".", { stat = "Average", label = "5xx %", yAxis = "right", color = "#d62728" }],
            [".", "4xxErrorRate", ".", ".", ".", ".", { stat = "Average", label = "4xx %", yAxis = "right" }],
          ]
        }
      },
      {
        type = "metric", x = 12, y = 12, width = 12, height = 6
        properties = {
          title  = "WAF: allowed and blocked requests"
          region = var.region
          view   = "timeSeries"
          stat   = "Sum"
          period = 60
          metrics = [
            ["AWS/WAFV2", "AllowedRequests", "WebACL", "${var.project}-web-acl", "Rule", "ALL", "Region", "Global", { label = "Allowed" }],
            [".", "BlockedRequests", ".", ".", ".", ".", ".", ".", { label = "Blocked (all rules)", color = "#d62728" }],
            [".", ".", ".", ".", ".", "login-rate-limit", ".", ".", { label = "Blocked by login rate limit" }],
          ]
        }
      },
      # Load balancer view: requests per task is what the request-based auto scaling follows,
      # and ELB 5xx / rejected connections appear when the ALB itself cannot keep up.
      {
        type = "metric", x = 0, y = 18, width = 12, height = 6
        properties = {
          title  = "Load balancer: requests per task, connections, ELB errors"
          region = var.region
          view   = "timeSeries"
          stat   = "Sum"
          period = 60
          metrics = [
            ["AWS/ApplicationELB", "RequestCountPerTarget", "TargetGroup", local.tg_dimension, { label = "Requests per task (scaling target: ${var.autoscaling_requests_per_target})" }],
            [".", "ActiveConnectionCount", "LoadBalancer", local.alb_dimension, { label = "Active connections", yAxis = "right", stat = "Average" }],
            [".", "HTTPCode_ELB_5XX_Count", ".", ".", { label = "ELB 5xx", color = "#d62728" }],
            [".", "RejectedConnectionCount", ".", ".", { label = "Rejected connections", color = "#9467bd" }],
          ]
        }
      },
    ]

    # Database widgets only when there is a database (range(0) = empty list)
    db_widgets = [for _ in range(local.db_count) : {
      type = "metric", x = 12, y = 6, width = 12, height = 6
      properties = {
        title  = "Database"
        region = var.region
        view   = "timeSeries"
        period = 60
        metrics = [
          ["AWS/RDS", "CPUUtilization", "DBInstanceIdentifier", local.db_identifier, { stat = "Average", label = "CPU %" }],
          [".", "DatabaseConnections", ".", ".", { stat = "Maximum", label = "Connections", yAxis = "right" }],
          [".", "FreeableMemory", ".", ".", { stat = "Minimum", label = "Free memory (bytes)", yAxis = "right" }],
        ]
      }
    }]

    # RDS Proxy: client connections (tasks -> proxy) vs database connections (proxy -> RDS) shows
    # how well the proxy pools. Placed next to the load balancer widget.
    proxy_widgets = [for _ in range(local.db_count) : {
      type = "metric", x = 12, y = 18, width = 12, height = 6
      properties = {
        title  = "RDS Proxy: connections and queries"
        region = var.region
        view   = "timeSeries"
        period = 60
        metrics = [
          ["AWS/RDS", "ClientConnections", "ProxyName", local.db_proxy_name, { stat = "Maximum", label = "Client connections (from tasks)" }],
          [".", "DatabaseConnections", ".", ".", { stat = "Maximum", label = "Database connections (to RDS)" }],
          [".", "QueryRequests", ".", ".", { stat = "Sum", label = "Queries per minute", yAxis = "right" }],
        ]
      }
    }]
  }
}

resource "aws_cloudwatch_dashboard" "main" {
  count = local.monitor_count

  dashboard_name = "${var.project}-overview"
  dashboard_body = jsonencode({ widgets = concat(local.dashboard.widgets, local.dashboard.db_widgets, local.dashboard.proxy_widgets) })
}
