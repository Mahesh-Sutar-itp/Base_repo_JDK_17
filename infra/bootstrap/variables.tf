variable "region" {
  description = "AWS region for the state bucket."
  type        = string
  default     = "us-east-1"
}

variable "project" {
  description = "Prefix used in names and tags."
  type        = string
  default     = "trebol-poc"
}

variable "noncurrent_version_days" {
  description = "Days to keep old versions of state files before S3 deletes them."
  type        = number
  default     = 30
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
