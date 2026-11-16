# Budget alerts (cost safety net).
#
# One monthly budget with several usage alerts. With the defaults (limit $50):
#     30% = $15 spent   -> early heads-up
#     50% = $25 spent   -> half way
#     90% = $45 spent   -> almost at the limit
#   plus one FORECAST alert: AWS predicts the month will end above 100% of the limit.
# Change var.budget_monthly_limit or var.budget_alert_percentages to adjust; the dollar amounts follow.
#
# Alerts go by email to var.alarm_email (set in alarm.auto.tfvars, git-ignored).
#
# Always on and free: the account's first budgets are free (about $0.10/month for extra ones).
#
# IMPORTANT - what the budget counts: the account is shared, so a budget on ALL spend would be over the
# limit already. By default it counts only resources tagged Project = trebol-poc. AWS can filter on a tag
# only after the tag is ACTIVATED as a cost allocation tag (Billing console; usually the cloud team or the
# management account does it). Until then this budget reads $0 and never alerts, so check once that its
# "Actual spend" in the Budgets console moves after a lab session. Set budget_filter_by_project_tag = false
# to count everything the account spends instead.

variable "budget_monthly_limit" {
  description = "Monthly budget in USD."
  type        = number
  default     = 50
}

variable "budget_alert_percentages" {
  description = "Send an alert when actual spend passes each of these percentages of the monthly limit."
  type        = list(number)
  default     = [30, 50, 90]

  validation {
    condition     = alltrue([for p in var.budget_alert_percentages : p > 0 && p <= 100])
    error_message = "Percentages must be between 1 and 100."
  }
}

variable "budget_filter_by_project_tag" {
  description = "true = count only resources tagged Project=<project> (needs the tag activated for cost allocation); false = all spend in the account."
  type        = bool
  default     = true
}

resource "aws_budgets_budget" "monthly" {
  # The budget needs at least one email address to notify
  count = var.alarm_email == null ? 0 : 1

  name         = "${var.project}-monthly"
  budget_type  = "COST"
  limit_amount = tostring(var.budget_monthly_limit)
  limit_unit   = "USD"
  time_unit    = "MONTHLY"

  dynamic "cost_filter" {
    for_each = var.budget_filter_by_project_tag ? [1] : []
    content {
      name = "TagKeyValue"
      # AWS wants "user:<TagKey>$<TagValue>" here
      values = [format("user:Project$%s", var.project)]
    }
  }

  # Usage alerts: actual spend passes 30% / 50% / 90% (by default) of the limit
  dynamic "notification" {
    for_each = var.budget_alert_percentages
    content {
      comparison_operator        = "GREATER_THAN"
      threshold                  = notification.value
      threshold_type             = "PERCENTAGE"
      notification_type          = "ACTUAL"
      subscriber_email_addresses = [var.alarm_email]
    }
  }

  # Forecast alert: AWS expects the month to end above the limit
  notification {
    comparison_operator        = "GREATER_THAN"
    threshold                  = 100
    threshold_type             = "PERCENTAGE"
    notification_type          = "FORECASTED"
    subscriber_email_addresses = [var.alarm_email]
  }

  tags = { Name = "${var.project}-monthly-budget" }
}
