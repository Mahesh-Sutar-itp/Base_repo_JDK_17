# Step 7b: CloudFront with a VPC origin (lab only).
#
# CloudFront is the only public entry point: HTTPS on its default *.cloudfront.net
# certificate (Route 53 / ACM are not available). It reaches the INTERNAL load balancer
# privately through a VPC origin, so the ALB, app and database stay off the internet.
#
# Cost: pay-as-you-go, inside the always-free tier (1 TB out + 10M requests/month) -> $0.
# The URL changes every lab session because the distribution is recreated.
# Creating and deleting take ~10-15 min each (VPC origin + distribution).

locals {
  cdn_count = var.lab_on ? 1 : 0

  # AWS managed policies
  cache_policy_caching_disabled         = "4135ea2d-6df8-44a3-9df3-4b5a84be39ad" # Managed-CachingDisabled
  origin_request_all_viewer_except_host = "b689b0a8-53d0-40ab-baf2-68738e2966ac" # Managed-AllViewerExceptHostHeader
  response_headers_security_headers     = "67f7725c-6f97-4210-82d7-5512b31e9d03" # Managed-SecurityHeadersPolicy
}

resource "aws_cloudfront_vpc_origin" "alb" {
  count = local.cdn_count

  vpc_origin_endpoint_config {
    name                   = "${var.project}-alb-origin"
    arn                    = aws_lb.app[0].arn
    http_port              = 80
    https_port             = 443
    origin_protocol_policy = "http-only" # ALB listens on HTTP 80 inside the VPC

    origin_ssl_protocols {
      items    = ["TLSv1.2"]
      quantity = 1
    }
  }

  tags = { Name = "${var.project}-alb-vpc-origin" }
}

resource "aws_cloudfront_distribution" "main" {
  count = local.cdn_count

  enabled         = true
  comment         = "${var.project} API (VPC origin -> internal ALB)"
  is_ipv6_enabled = true
  http_version    = "http2and3"
  price_class     = "PriceClass_100" # North America + Europe edges: cheapest
  web_acl_id      = aws_wafv2_web_acl.main[0].arn

  origin {
    origin_id   = "internal-alb"
    domain_name = aws_lb.app[0].dns_name

    vpc_origin_config {
      vpc_origin_id            = aws_cloudfront_vpc_origin.alb[0].id
      origin_keepalive_timeout = 5
      origin_read_timeout      = 30
    }
  }

  # API traffic: nothing is cached; every header (incl. Authorization), cookie and query
  # string is passed to the app, except Host (the ALB expects its own).
  default_cache_behavior {
    target_origin_id         = "internal-alb"
    viewer_protocol_policy   = "redirect-to-https"
    allowed_methods          = ["GET", "HEAD", "OPTIONS", "PUT", "POST", "PATCH", "DELETE"]
    cached_methods           = ["GET", "HEAD"]
    compress                 = true
    cache_policy_id          = local.cache_policy_caching_disabled
    origin_request_policy_id = local.origin_request_all_viewer_except_host

    # HSTS, X-Content-Type-Options, X-Frame-Options, Referrer-Policy, X-XSS-Protection
    response_headers_policy_id = local.response_headers_security_headers
  }

  restrictions {
    geo_restriction {
      restriction_type = "none"
    }
  }

  viewer_certificate {
    cloudfront_default_certificate = true # HTTPS on *.cloudfront.net
  }

  tags = { Name = "${var.project}-cdn" }
}

# AWS creates "CloudFront-VPCOrigins-Service-SG" in our VPC when the VPC origin is created.
# The ALB accepts traffic only from that group (nothing else can reach it).
data "aws_security_group" "cloudfront_vpc_origin" {
  count  = local.cdn_count
  vpc_id = aws_vpc.main.id

  filter {
    name   = "group-name"
    values = ["CloudFront-VPCOrigins-Service-SG"]
  }

  depends_on = [aws_cloudfront_vpc_origin.alb]
}

resource "aws_vpc_security_group_ingress_rule" "alb_from_cloudfront" {
  count = local.cdn_count

  security_group_id            = aws_security_group.alb.id
  description                  = "HTTP from CloudFront VPC origin only"
  ip_protocol                  = "tcp"
  from_port                    = 80
  to_port                      = 80
  referenced_security_group_id = data.aws_security_group.cloudfront_vpc_origin[0].id
  tags                         = { Name = "${var.project}-alb-from-cloudfront" }
}

# Publishes the public URL so the deploy workflow can smoke-test it (it changes every lab session).
# A standard SSM parameter is free. Exists only while the lab is on; the workflow reads it to find out
# whether there is anything to deploy to and where to send the smoke test.
resource "aws_ssm_parameter" "app_url" {
  count = local.cdn_count

  name        = "/${var.project}/app-url"
  description = "Public HTTPS address of the API (set at lab-up, removed at lab-down)"
  type        = "String"
  value       = "https://${aws_cloudfront_distribution.main[0].domain_name}"

  tags = { Name = "${var.project}-app-url" }
}
