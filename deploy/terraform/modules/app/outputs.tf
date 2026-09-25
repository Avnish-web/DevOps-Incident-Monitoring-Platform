output "alb_dns_name" {
  description = "Point the application's DNS record (CNAME/alias) here."
  value       = aws_lb.this.dns_name
}

output "alb_zone_id" {
  value = aws_lb.this.zone_id
}

output "cluster_name" {
  value = aws_ecs_cluster.this.name
}

output "ecr_repository_urls" {
  value = { for k, repo in aws_ecr_repository.this : k => repo.repository_url }
}

output "api_security_group_id" {
  value = aws_security_group.api.id
}

output "worker_security_group_id" {
  value = aws_security_group.worker.id
}

output "admin_password_secret_arn" {
  description = "Read the initial admin password from here, then change it in the dashboard."
  value       = aws_secretsmanager_secret.admin_password.arn
}

output "alarm_topic_arn" {
  value = aws_sns_topic.alarms.arn
}
