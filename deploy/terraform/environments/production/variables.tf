variable "region" {
  type    = string
  default = "eu-central-1"
}

variable "availability_zones" {
  type    = list(string)
  default = ["eu-central-1a", "eu-central-1b"]
}

variable "vpc_cidr" {
  type    = string
  default = "10.20.0.0/16"
}

variable "single_nat_gateway" {
  description = "true saves ~one NAT gateway per extra AZ at the cost of AZ-failure resilience."
  type        = bool
  default     = false
}

variable "image_tag" {
  description = "Release to deploy (set by the deploy workflow)."
  type        = string
}

variable "certificate_arn" {
  description = "ACM certificate ARN for the public hostname."
  type        = string
}

variable "admin_email" {
  type = string
}

variable "alarm_email" {
  type    = string
  default = ""
}

variable "smtp_host" {
  type    = string
  default = ""
}

variable "smtp_username" {
  type    = string
  default = ""
}

variable "alert_email_from" {
  type    = string
  default = "monitoring@localhost"
}

variable "db_instance_class" {
  type    = string
  default = "db.t4g.small"
}

variable "db_multi_az" {
  type    = bool
  default = true
}

variable "redis_node_type" {
  type    = string
  default = "cache.t4g.micro"
}

variable "redis_replicas" {
  type    = number
  default = 1
}

variable "deletion_protection" {
  type    = bool
  default = true
}
