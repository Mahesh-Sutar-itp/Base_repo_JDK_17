provider "aws" {
  region = var.region

  # Added to every resource, so costs and ownership are easy to find in the console and billing
  default_tags {
    tags = {
      Project   = var.project
      Owner     = var.owner
      ManagedBy = "terraform"
    }
  }
}
