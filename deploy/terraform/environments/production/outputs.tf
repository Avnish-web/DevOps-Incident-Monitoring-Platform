output "alb_dns_name" {
  description = "Create a DNS alias (Route 53) or CNAME for your hostname pointing here."
  value       = module.app.alb_dns_name
}

output "ecr_repository_urls" {
  value = module.app.ecr_repository_urls
}

output "cluster_name" {
  value = module.app.cluster_name
}

output "admin_password_secret_arn" {
  value = module.app.admin_password_secret_arn
}

output "alarm_topic_arn" {
  value = module.app.alarm_topic_arn
}
