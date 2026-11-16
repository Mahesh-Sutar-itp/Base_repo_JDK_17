terraform {
  # 1.11+ is needed later for the S3 backend's native lock file (use_lockfile).
  required_version = ">= 1.11.0"

  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "~> 6.0"
    }
  }

  # Bootstrap keeps LOCAL state on purpose: it creates the bucket that every
  # other stack uses for remote state. Do not commit terraform.tfstate.
}

provider "aws" {
  region = var.region

  default_tags {
    tags = {
      Project     = var.project
      Owner       = var.owner
      Environment = "poc"
      ManagedBy   = "terraform"
      Stack       = "bootstrap"
    }
  }
}
