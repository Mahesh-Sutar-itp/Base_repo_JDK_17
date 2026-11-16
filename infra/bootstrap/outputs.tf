output "state_bucket" {
  description = "Use this as the bucket in infra/aws backend config."
  value       = aws_s3_bucket.tfstate.bucket
}

output "region" {
  value = var.region
}
