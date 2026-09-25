variable "name" {
  description = "Name prefix for all network resources."
  type        = string
}

variable "cidr_block" {
  description = "VPC CIDR; /16 leaves room for the three /24 subnet tiers per AZ."
  type        = string
  default     = "10.20.0.0/16"
}

variable "availability_zones" {
  description = "At least two AZs for high availability."
  type        = list(string)
  validation {
    condition     = length(var.availability_zones) >= 2
    error_message = "Use at least two availability zones."
  }
}

variable "single_nat_gateway" {
  description = "One shared NAT gateway (cheaper) instead of one per AZ (survives an AZ outage)."
  type        = bool
  default     = false
}

variable "log_retention_days" {
  description = "Retention of VPC flow logs."
  type        = number
  default     = 30
}
