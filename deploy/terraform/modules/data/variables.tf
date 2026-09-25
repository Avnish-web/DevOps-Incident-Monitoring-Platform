variable "name" {
  type = string
}

variable "vpc_id" {
  type = string
}

variable "data_subnet_ids" {
  description = "Isolated subnets (no internet route) for the databases."
  type        = list(string)
}

variable "postgres_client_security_group_ids" {
  description = "Security groups allowed to connect to PostgreSQL (API, worker)."
  type        = list(string)
}

variable "redis_client_security_group_ids" {
  description = "Security groups allowed to connect to Redis (API)."
  type        = list(string)
}

variable "db_instance_class" {
  type    = string
  default = "db.t4g.small"
}

variable "db_multi_az" {
  description = "Synchronous standby in a second AZ (automatic failover)."
  type        = bool
  default     = true
}

variable "db_max_storage_gb" {
  description = "Storage autoscaling ceiling."
  type        = number
  default     = 100
}

variable "db_backup_retention_days" {
  type    = number
  default = 7
}

variable "deletion_protection" {
  type    = bool
  default = true
}

variable "redis_node_type" {
  type    = string
  default = "cache.t4g.micro"
}

variable "redis_replicas" {
  description = "Read replicas; at least 1 enables automatic failover across AZs."
  type        = number
  default     = 1
}
