# Step 2: container registry for the app image.
#
# Always on (lab on or off): storage is about $0.10 per GB-month, and the lifecycle
# policy below keeps only a few images, so this costs a few cents a month.

resource "aws_ecr_repository" "app" {
  name = "${var.project}/backend"

  # Each tag points at one image forever (tag = git commit SHA), so a deploy
  # can never silently pick up a different image under the same tag.
  image_tag_mutability = "IMMUTABLE"

  # Basic scanning on push is free; findings show in the ECR console.
  image_scanning_configuration {
    scan_on_push = true
  }

  encryption_configuration {
    encryption_type = "AES256"
  }

  # false = Terraform refuses to delete the repo while it still holds images.
  # Set ecr_force_delete=true only for the final teardown after the demo.
  force_delete = var.ecr_force_delete

  tags = { Name = "${var.project}-backend" }
}

resource "aws_ecr_lifecycle_policy" "app" {
  repository = aws_ecr_repository.app.name

  policy = jsonencode({
    rules = [
      {
        rulePriority = 1
        description  = "Delete untagged images after 1 day"
        selection = {
          tagStatus   = "untagged"
          countType   = "sinceImagePushed"
          countUnit   = "days"
          countNumber = 1
        }
        action = { type = "expire" }
      },
      {
        rulePriority = 2
        description  = "Keep only the newest ${var.ecr_keep_images} images"
        selection = {
          tagStatus   = "any"
          countType   = "imageCountMoreThan"
          countNumber = var.ecr_keep_images
        }
        action = { type = "expire" }
      },
    ]
  })
}
