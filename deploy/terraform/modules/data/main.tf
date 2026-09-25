# PostgreSQL (RDS) and Redis (ElastiCache) in the isolated data subnets.
# Only the security groups passed in may connect; both enforce TLS and encryption at rest.

# ---------------------------------------------------------------------------------------
# PostgreSQL
# ---------------------------------------------------------------------------------------
resource "aws_db_subnet_group" "this" {
  name       = "${var.name}-postgres"
  subnet_ids = var.data_subnet_ids
}

resource "aws_security_group" "postgres" {
  name        = "${var.name}-postgres"
  description = "PostgreSQL: API and worker only"
  vpc_id      = var.vpc_id
}

resource "aws_vpc_security_group_ingress_rule" "postgres" {
  for_each                     = toset(var.postgres_client_security_group_ids)
  security_group_id            = aws_security_group.postgres.id
  referenced_security_group_id = each.value
  ip_protocol                  = "tcp"
  from_port                    = 5432
  to_port                      = 5432
  description                  = "PostgreSQL from application tasks"
}

resource "aws_db_parameter_group" "this" {
  name   = "${var.name}-postgres17"
  family = "postgres17"

  parameter {
    name  = "rds.force_ssl"
    value = "1"
  }
  parameter {
    name  = "log_min_duration_statement"
    value = "1000" # log statements slower than 1 s
  }
}

resource "aws_db_instance" "this" {
  identifier     = "${var.name}-postgres"
  engine         = "postgres"
  engine_version = "17"
  instance_class = var.db_instance_class

  db_name  = "monitoring"
  username = "monitoring"
  # RDS generates and rotates the password in Secrets Manager; it never appears in state.
  manage_master_user_password = true

  allocated_storage     = 20
  max_allocated_storage = var.db_max_storage_gb
  storage_type          = "gp3"
  storage_encrypted     = true

  multi_az               = var.db_multi_az
  db_subnet_group_name   = aws_db_subnet_group.this.name
  vpc_security_group_ids = [aws_security_group.postgres.id]
  parameter_group_name   = aws_db_parameter_group.this.name
  publicly_accessible    = false
  ca_cert_identifier     = "rds-ca-rsa2048-g1"

  backup_retention_period    = var.db_backup_retention_days
  backup_window              = "02:00-03:00"
  maintenance_window         = "sun:03:30-sun:04:30"
  auto_minor_version_upgrade = true
  copy_tags_to_snapshot      = true
  deletion_protection        = var.deletion_protection
  skip_final_snapshot        = false
  final_snapshot_identifier  = "${var.name}-postgres-final"

  performance_insights_enabled    = true
  enabled_cloudwatch_logs_exports = ["postgresql"]
}

# ---------------------------------------------------------------------------------------
# Redis (sessions and login rate limiting)
# ---------------------------------------------------------------------------------------
resource "aws_elasticache_subnet_group" "this" {
  name       = "${var.name}-redis"
  subnet_ids = var.data_subnet_ids
}

resource "aws_security_group" "redis" {
  name        = "${var.name}-redis"
  description = "Redis: API only"
  vpc_id      = var.vpc_id
}

resource "aws_vpc_security_group_ingress_rule" "redis" {
  for_each                     = toset(var.redis_client_security_group_ids)
  security_group_id            = aws_security_group.redis.id
  referenced_security_group_id = each.value
  ip_protocol                  = "tcp"
  from_port                    = 6379
  to_port                      = 6379
  description                  = "Redis from the API"
}

# Spring Session's indexed repository needs keyspace notifications. ElastiCache forbids the
# CONFIG command, so they are enabled here and the API runs with
# SESSION_REDIS_CONFIGURE_ACTION=none.
resource "aws_elasticache_parameter_group" "this" {
  name   = "${var.name}-redis7"
  family = "redis7"

  parameter {
    name  = "notify-keyspace-events"
    value = "Egx"
  }
}

resource "random_password" "redis_auth" {
  length  = 48
  special = false # ElastiCache AUTH tokens allow only a subset of symbols
}

resource "aws_secretsmanager_secret" "redis_auth" {
  name                    = "${var.name}/redis-auth-token"
  description             = "ElastiCache AUTH token used by the API"
  recovery_window_in_days = 7
}

resource "aws_secretsmanager_secret_version" "redis_auth" {
  secret_id     = aws_secretsmanager_secret.redis_auth.id
  secret_string = random_password.redis_auth.result
}

resource "aws_elasticache_replication_group" "this" {
  replication_group_id = "${var.name}-redis"
  description          = "Sessions and rate limiting for ${var.name}"
  engine               = "redis"
  engine_version       = "7.1"
  node_type            = var.redis_node_type
  port                 = 6379

  num_cache_clusters         = var.redis_replicas + 1
  automatic_failover_enabled = var.redis_replicas > 0
  multi_az_enabled           = var.redis_replicas > 0

  subnet_group_name    = aws_elasticache_subnet_group.this.name
  security_group_ids   = [aws_security_group.redis.id]
  parameter_group_name = aws_elasticache_parameter_group.this.name

  at_rest_encryption_enabled = true
  transit_encryption_enabled = true
  auth_token                 = random_password.redis_auth.result
  auto_minor_version_upgrade = true
  maintenance_window         = "sun:04:30-sun:05:30"
  # Sessions are disposable: losing them only signs users out.
  snapshot_retention_limit = 0
}
