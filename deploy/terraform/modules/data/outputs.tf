output "postgres_host" {
  value = aws_db_instance.this.address
}

output "postgres_port" {
  value = aws_db_instance.this.port
}

output "postgres_db_name" {
  value = aws_db_instance.this.db_name
}

output "postgres_instance_id" {
  value = aws_db_instance.this.identifier
}

# JSON secret with "username" and "password" keys, managed and rotated by RDS.
output "postgres_master_secret_arn" {
  value = aws_db_instance.this.master_user_secret[0].secret_arn
}

output "redis_host" {
  value = aws_elasticache_replication_group.this.primary_endpoint_address
}

output "redis_port" {
  value = aws_elasticache_replication_group.this.port
}

output "redis_auth_secret_arn" {
  value = aws_secretsmanager_secret.redis_auth.arn
}
