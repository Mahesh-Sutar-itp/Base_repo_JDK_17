# Step 7a: AWS WAF on CloudFront (lab only).
#
# Cost while it exists: $5/month per web ACL + $1/month per rule (billed per hour, so a
# 5-hour lab costs cents) + $0.60 per million requests.
# CloudFront WAF rules must live in us-east-1, which is our region.
#
# Rules, in the order WAF checks them:
#   0  Login rate limit   - blocks one IP that calls /public/login too often (HTTP 429)
#   1  IP reputation      - AWS list of known malicious IPs (bots, scanners)
#   2  Known bad inputs   - Log4Shell-style and other exploit payloads
#   3  Common rule set    - OWASP-style protections (SQL injection, XSS, bad sizes...)

locals {
  waf_count = var.lab_on ? 1 : 0
}

# Load-test generator IPs, excluded from the login rate limit (plan assumption 06).
# Free; always on so the list is kept between sessions.
resource "aws_wafv2_ip_set" "loadtest" {
  name               = "${var.project}-loadtest-ips"
  description        = "k6 load generator IPs, excluded from the login rate limit"
  scope              = "CLOUDFRONT"
  ip_address_version = "IPV4"
  addresses          = var.waf_loadtest_ips
  tags               = { Name = "${var.project}-loadtest-ips" }
}

resource "aws_wafv2_web_acl" "main" {
  count = local.waf_count

  name        = "${var.project}-web-acl"
  description = "Trebol POC: login rate limit + AWS managed rules"
  scope       = "CLOUDFRONT"

  default_action {
    allow {}
  }

  rule {
    name     = "login-rate-limit"
    priority = 0

    action {
      block {
        custom_response {
          response_code = 429 # Too Many Requests
        }
      }
    }

    statement {
      rate_based_statement {
        limit                 = var.waf_login_rate_limit
        evaluation_window_sec = 300
        aggregate_key_type    = "IP"

        # Count only login requests, and never from the load-test IPs
        scope_down_statement {
          and_statement {
            statement {
              byte_match_statement {
                search_string         = "/public/login"
                positional_constraint = "STARTS_WITH"
                field_to_match {
                  uri_path {}
                }
                text_transformation {
                  priority = 0
                  type     = "LOWERCASE"
                }
              }
            }
            statement {
              not_statement {
                statement {
                  ip_set_reference_statement {
                    arn = aws_wafv2_ip_set.loadtest.arn
                  }
                }
              }
            }
          }
        }
      }
    }

    visibility_config {
      cloudwatch_metrics_enabled = true
      metric_name                = "login-rate-limit"
      sampled_requests_enabled   = true
    }
  }

  dynamic "rule" {
    for_each = [
      { priority = 1, name = "AWSManagedRulesAmazonIpReputationList" },
      { priority = 2, name = "AWSManagedRulesKnownBadInputsRuleSet" },
      { priority = 3, name = "AWSManagedRulesCommonRuleSet" },
    ]
    content {
      name     = rule.value.name
      priority = rule.value.priority

      override_action {
        none {} # use the rule group's own actions (block)
      }

      statement {
        managed_rule_group_statement {
          vendor_name = "AWS"
          name        = rule.value.name
        }
      }

      visibility_config {
        cloudwatch_metrics_enabled = true
        metric_name                = rule.value.name
        sampled_requests_enabled   = true
      }
    }
  }

  visibility_config {
    cloudwatch_metrics_enabled = true
    metric_name                = "${var.project}-web-acl"
    sampled_requests_enabled   = true
  }

  tags = { Name = "${var.project}-web-acl" }
}
