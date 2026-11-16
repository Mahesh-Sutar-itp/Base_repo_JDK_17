terraform {
  required_version = ">= 1.11.0"

  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "~> 6.0"
    }
    random = {
      source  = "hashicorp/random"
      version = "~> 3.6"
    }
  }

  # State lives in the S3 bucket created by infra/bootstrap. The bucket name is passed at init time:
  #   terraform init -backend-config="bucket=trebol-poc-tfstate-<ACCOUNT_ID>"
  # use_lockfile = S3-native state locking (Terraform 1.10+). It replaces the DynamoDB lock table
  # listed in the service plan, so that table is no longer needed.
  backend "s3" {
    key          = "trebol-poc/aws.tfstate"
    region       = "us-east-1"
    encrypt      = true
    use_lockfile = true
  }
}
