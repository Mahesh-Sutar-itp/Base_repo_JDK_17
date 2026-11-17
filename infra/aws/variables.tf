variable "project" {
  description = "Name prefix for all resources"
  type        = string
  default     = "trebol-poc"
}

variable "owner" {
  description = <<-EOT
    Owner tag on every resource. The organization's tag policy requires the owner's email address.
    Set it in owner.auto.tfvars (git-ignored, loaded automatically; see owner.auto.tfvars.example).
  EOT
  type        = string

  validation {
    condition     = can(regex("^[^@ ]+@[^@ ]+$", var.owner))
    error_message = "owner must be an email address (required by the organization's tag policy)."
  }
}

variable "region" {
  description = "AWS region (us-east-1 also hosts the CloudFront WAF rules)"
  type        = string
  default     = "us-east-1"
}

variable "vpc_cidr" {
  description = "Address range for the whole VPC"
  type        = string
  default     = "10.20.0.0/16"
}

variable "az_count" {
  description = "Number of Availability Zones to spread across"
  type        = number
  default     = 2
}

variable "lab_on" {
  description = <<-EOT
    Switch for everything that is billed by the hour: NAT gateway, database, RDS Proxy, load balancer,
    ECS service, CloudFront, WAF, alarms and dashboard.
    false (default) = lab off: only free or near-free resources exist (VPC, subnets, ECR, secrets, IAM).
    true            = lab on: the full stack runs. Start with lab-up.cmd, stop with lab-down.cmd.
    The default is false so a plain `terraform apply` can never leave costly resources running by accident.
  EOT
  type        = bool
  default     = false
}

variable "ecr_keep_images" {
  description = "How many images ECR keeps; older ones are deleted automatically."
  type        = number
  default     = 10
}

variable "ecr_force_delete" {
  description = "Allow Terraform to delete the ECR repository even if it holds images (final teardown only)."
  type        = bool
  default     = false
}

variable "db_username" {
  description = "MariaDB master user name."
  type        = string
  default     = "trebol_admin"
}

variable "db_name" {
  description = "Database (schema) the app uses; Flyway creates the tables in it."
  type        = string
  default     = "trebol"
}

variable "db_engine_version" {
  description = "MariaDB version on RDS (matches docker-compose: mariadb:10.11)."
  type        = string
  default     = "10.11"
}

variable "db_instance_class" {
  description = <<-EOT
    DB size. The organization's SCP allows creating db.t3.micro (db.t4g.micro was denied on create).
    Ask the cloud team before using a bigger class for the load test.
  EOT
  type        = string
  default     = "db.t3.micro"
}

variable "db_multi_az" {
  description = "false = one zone (practice). true = standby copy in the second zone (load test and demo)."
  type        = bool
  default     = false
}

variable "db_allocated_storage" {
  description = "Database storage in GB (gp3)."
  type        = number
  default     = 20
}

variable "db_enabled" {
  description = "Create RDS MariaDB + RDS Proxy when the lab is on. false = app runs on its built-in H2 database."
  type        = bool
  default     = true
}

variable "app_image_tag" {
  description = "Image tag in ECR to run (git short SHA). Clean scan: 3a33ddf."
  type        = string
  default     = "3a33ddf"
}

variable "app_cpu" {
  description = "Fargate task CPU units (512 = 0.5 vCPU)."
  type        = number
  default     = 512
}

variable "app_memory" {
  description = "Fargate task memory in MiB."
  type        = number
  default     = 1024
}

variable "app_desired_count" {
  description = "Number of app tasks (2 = one per zone)."
  type        = number
  default     = 2
}

variable "app_db_pool_size" {
  description = <<-EOT
    Hikari connections per task. Small on purpose: the read cache means most requests need no DB
    connection, and RDS Proxy shares the few connections db.t3.micro allows (10-24 for the proxy)
    across all tasks. At 10 per task, load test 3 ran out with ~10 tasks.
  EOT
  type        = number
  default     = 3
}

variable "rds_proxy_debug_logging" {
  description = <<-EOT
    Turn on RDS Proxy debug logs (CloudWatch /aws/rds/proxy/<name>), which state why a session was
    pinned. For short diagnostic runs only: the logs can include SQL text.
  EOT
  type        = bool
  default     = false
}

variable "cors_allowed_origins" {
  description = "Browser origins allowed by CORS (only matters for a web front end; none in scope)."
  type        = string
  default     = "http://localhost:4200"
}

variable "swagger_enabled" {
  description = <<-EOT
    Serve Swagger UI (/swagger-ui/index.html) and the OpenAPI description (/v3/api-docs) through
    CloudFront, e.g. for a review session. Off by default: it publishes the whole API surface.
    Like other container settings, it takes effect when the task definition is created (lab-up).
  EOT
  type        = bool
  default     = false
}

variable "log_retention_days" {
  description = "How long CloudWatch keeps app logs."
  type        = number
  default     = 7
}

variable "waf_login_rate_limit" {
  description = "Max requests to /public/login per IP in 5 minutes before WAF answers 429 (minimum 10)."
  type        = number
  default     = 100
}

variable "waf_loadtest_ips" {
  description = "Load-test generator IPs in CIDR form (e.g. [\"203.0.113.10/32\"]), excluded from the login rate limit."
  type        = list(string)
  default     = []
}

variable "autoscaling_max_tasks" {
  description = "Upper limit for ECS auto scaling (the minimum is app_desired_count). Each task costs about $0.025/h."
  type        = number
  default     = 6
}

variable "autoscaling_cpu_target" {
  description = <<-EOT
    Average CPU % across tasks that auto scaling tries to hold. 50 leaves headroom for the 4-6 minutes
    a new task needs (alarm + start + health checks); at 60 the first load test reached 100% first.
  EOT
  type        = number
  default     = 50
}

variable "autoscaling_fast_cpu_threshold" {
  description = <<-EOT
    Emergency scale-out: when average CPU passes this % for ONE minute, add autoscaling_fast_step_tasks
    tasks at once. Target tracking needs 3 minutes of data before it acts; this step policy reacts sooner.
  EOT
  type        = number
  default     = 70
}

variable "autoscaling_fast_step_tasks" {
  description = "Tasks added at once by the emergency scale-out (CPU above autoscaling_fast_cpu_threshold)."
  type        = number
  default     = 2
}

variable "autoscaling_fast_step_tasks_high" {
  description = "Tasks added at once when CPU is 15 points or more above the emergency threshold (a surge)."
  type        = number
  default     = 4
}

variable "autoscaling_requests_per_target" {
  description = <<-EOT
    Requests per minute per task that auto scaling tries to hold (ALB RequestCountPerTarget).
    Load test 2 measured ~5,200/min per 1 vCPU task at ~60% CPU; 4,000 keeps headroom. (1,000 made
    this policy ask for far more tasks than CPU needed.)
  EOT
  type        = number
  default     = 4000
}

variable "latency_alarm_seconds" {
  description = "Alarm when the p95 response time of the app (seen by the ALB) stays above this many seconds."
  type        = number
  default     = 0.5
}

variable "alarm_email" {
  description = <<-EOT
    Email address that receives alarm notifications (SNS). AWS sends a confirmation email that must be
    clicked once. Set it in alarm.auto.tfvars (git-ignored); null = topic only, no subscription.
  EOT
  type        = string
  default     = null
  sensitive   = true
}
