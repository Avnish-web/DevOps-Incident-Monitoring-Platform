# Production environment: network + data tier + application on ECS Fargate.
# Applied by .github/workflows/deploy.yml with -var image_tag=<release>.

terraform {
  required_version = ">= 1.10"

  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "~> 6.66"
    }
    random = {
      source  = "hashicorp/random"
      version = "~> 3.7"
    }
  }

  # bucket and region come from -backend-config (see deploy.yml); S3-native locking.
  backend "s3" {
    key          = "monitoring/production/terraform.tfstate"
    encrypt      = true
    use_lockfile = true
  }
}

provider "aws" {
  region = var.region
  default_tags {
    tags = {
      Project     = "monitoring-platform"
      Environment = "production"
      ManagedBy   = "terraform"
    }
  }
}

locals {
  name = "monitoring-production"
}

module "network" {
  source             = "../../modules/network"
  name               = local.name
  cidr_block         = var.vpc_cidr
  availability_zones = var.availability_zones
  single_nat_gateway = var.single_nat_gateway
}

module "data" {
  source                             = "../../modules/data"
  name                               = local.name
  vpc_id                             = module.network.vpc_id
  data_subnet_ids                    = module.network.private_data_subnet_ids
  postgres_client_security_group_ids = [module.app.api_security_group_id, module.app.worker_security_group_id]
  redis_client_security_group_ids    = [module.app.api_security_group_id]
  db_instance_class                  = var.db_instance_class
  db_multi_az                        = var.db_multi_az
  redis_node_type                    = var.redis_node_type
  redis_replicas                     = var.redis_replicas
  deletion_protection                = var.deletion_protection
}

module "app" {
  source            = "../../modules/app"
  name              = local.name
  image_tag         = var.image_tag
  vpc_id            = module.network.vpc_id
  vpc_cidr_block    = module.network.vpc_cidr_block
  public_subnet_ids = module.network.public_subnet_ids
  app_subnet_ids    = module.network.private_app_subnet_ids

  postgres_host              = module.data.postgres_host
  postgres_port              = module.data.postgres_port
  postgres_db_name           = module.data.postgres_db_name
  postgres_instance_id       = module.data.postgres_instance_id
  postgres_master_secret_arn = module.data.postgres_master_secret_arn
  redis_host                 = module.data.redis_host
  redis_port                 = module.data.redis_port
  redis_auth_secret_arn      = module.data.redis_auth_secret_arn

  certificate_arn     = var.certificate_arn
  admin_email         = var.admin_email
  alarm_email         = var.alarm_email
  smtp_host           = var.smtp_host
  smtp_username       = var.smtp_username
  alert_email_from    = var.alert_email_from
  deletion_protection = var.deletion_protection
}
