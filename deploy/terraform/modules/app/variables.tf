variable "name" {
  type = string
}

variable "image_tag" {
  description = "Release tag deployed to all services (e.g. v0.1.0), present in ECR."
  type        = string
  validation {
    condition     = can(regex("^v[0-9]+\\.[0-9]+\\.[0-9]+$", var.image_tag))
    error_message = "image_tag must be a release tag like v1.2.3."
  }
}

# --- Network ------------------------------------------------------------------------------
variable "vpc_id" {
  type = string
}

variable "vpc_cidr_block" {
  description = "Used to scope the API's egress to the data tier."
  type        = string
}

variable "public_subnet_ids" {
  type = list(string)
}

variable "app_subnet_ids" {
  type = list(string)
}

# --- Data tier (from the data module) -------------------------------------------------------
variable "postgres_host" {
  type = string
}

variable "postgres_port" {
  type = number
}

variable "postgres_db_name" {
  type = string
}

variable "postgres_instance_id" {
  type = string
}

variable "postgres_master_secret_arn" {
  type = string
}

variable "redis_host" {
  type = string
}

variable "redis_port" {
  type = number
}

variable "redis_auth_secret_arn" {
  type = string
}

# --- Edge -----------------------------------------------------------------------------------
variable "certificate_arn" {
  description = "ACM certificate for the public domain (in the same region as the ALB)."
  type        = string
}

variable "waf_rate_limit_per_5min" {
  description = "Requests per client IP per 5 minutes before WAF blocks it."
  type        = number
  default     = 2000
}

# --- Application ----------------------------------------------------------------------------
variable "admin_email" {
  description = "Bootstrap administrator; the initial password is generated in Secrets Manager."
  type        = string
}

variable "smtp_host" {
  description = "SMTP server for e-mail alerts (e.g. email-smtp.<region>.amazonaws.com); empty disables e-mail."
  type        = string
  default     = ""
}

variable "smtp_port" {
  type    = number
  default = 587
}

variable "smtp_username" {
  type    = string
  default = ""
}

variable "alert_email_from" {
  type    = string
  default = "monitoring@localhost"
}

variable "worker_concurrency" {
  type    = number
  default = 20
}

# --- Capacity -------------------------------------------------------------------------------
variable "api_cpu" {
  type    = number
  default = 512
}

variable "api_memory" {
  type    = number
  default = 1024
}

variable "worker_cpu" {
  type    = number
  default = 512
}

variable "worker_memory" {
  type    = number
  default = 1024
}

variable "api_min_tasks" {
  type    = number
  default = 2
}

variable "api_max_tasks" {
  type    = number
  default = 6
}

variable "worker_min_tasks" {
  type    = number
  default = 2
}

variable "worker_max_tasks" {
  type    = number
  default = 6
}

variable "web_min_tasks" {
  type    = number
  default = 2
}

variable "web_max_tasks" {
  type    = number
  default = 4
}

# --- Operations -----------------------------------------------------------------------------
variable "log_retention_days" {
  type    = number
  default = 30
}

variable "alarm_email" {
  description = "Receives platform alarms (confirm the SNS subscription e-mail). Empty disables."
  type        = string
  default     = ""
}

variable "deletion_protection" {
  type    = bool
  default = true
}
