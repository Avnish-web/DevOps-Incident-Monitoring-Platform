# Private registries that ECS pulls from. Release images are copied here from GHCR by the
# deploy workflow; tags are immutable so a version always means the same image.

locals {
  images = toset(["api", "worker", "web"])
}

resource "aws_ecr_repository" "this" {
  for_each             = local.images
  name                 = "monitoring-${each.key}"
  image_tag_mutability = "IMMUTABLE"
  force_delete         = false

  image_scanning_configuration {
    scan_on_push = true
  }
  encryption_configuration {
    encryption_type = "AES256"
  }
}

resource "aws_ecr_lifecycle_policy" "this" {
  for_each   = aws_ecr_repository.this
  repository = each.value.name
  policy = jsonencode({
    rules = [{
      rulePriority = 1
      description  = "Keep the 20 most recent images"
      selection = {
        tagStatus   = "any"
        countType   = "imageCountMoreThan"
        countNumber = 20
      }
      action = { type = "expire" }
    }]
  })
}
