# Traffic flow: internet -> ALB (443) -> web (8080) -> api (8080). The worker accepts nothing.
# All tasks may call out (checks, alert webhooks, AWS APIs) through the NAT gateway.

resource "aws_security_group" "alb" {
  name        = "${var.name}-alb"
  description = "Public load balancer"
  vpc_id      = var.vpc_id
}

resource "aws_vpc_security_group_ingress_rule" "alb_https" {
  security_group_id = aws_security_group.alb.id
  cidr_ipv4         = "0.0.0.0/0"
  ip_protocol       = "tcp"
  from_port         = 443
  to_port           = 443
  description       = "HTTPS from the internet"
}

resource "aws_vpc_security_group_ingress_rule" "alb_http" {
  security_group_id = aws_security_group.alb.id
  cidr_ipv4         = "0.0.0.0/0"
  ip_protocol       = "tcp"
  from_port         = 80
  to_port           = 80
  description       = "HTTP from the internet (redirected to HTTPS)"
}

resource "aws_vpc_security_group_egress_rule" "alb_to_web" {
  security_group_id            = aws_security_group.alb.id
  referenced_security_group_id = aws_security_group.web.id
  ip_protocol                  = "tcp"
  from_port                    = 8080
  to_port                      = 8080
  description                  = "To the web tasks"
}

resource "aws_security_group" "web" {
  name        = "${var.name}-web"
  description = "Nginx + dashboard tasks"
  vpc_id      = var.vpc_id
}

resource "aws_vpc_security_group_ingress_rule" "web_from_alb" {
  security_group_id            = aws_security_group.web.id
  referenced_security_group_id = aws_security_group.alb.id
  ip_protocol                  = "tcp"
  from_port                    = 8080
  to_port                      = 8080
  description                  = "From the load balancer"
}

resource "aws_security_group" "api" {
  name        = "${var.name}-api"
  description = "API tasks"
  vpc_id      = var.vpc_id
}

resource "aws_vpc_security_group_ingress_rule" "api_from_web" {
  security_group_id            = aws_security_group.api.id
  referenced_security_group_id = aws_security_group.web.id
  ip_protocol                  = "tcp"
  from_port                    = 8080
  to_port                      = 8080
  description                  = "API port from Nginx (Service Connect)"
}

resource "aws_security_group" "worker" {
  name        = "${var.name}-worker"
  description = "Worker tasks: no inbound traffic"
  vpc_id      = var.vpc_id
}

# --- Egress --------------------------------------------------------------------------------
# HTTPS to AWS APIs (ECR image pulls, CloudWatch Logs, Secrets Manager, ECS/Service Connect)
# goes through the NAT gateway. Restricted to port 443; AWS service IPs are not stable CIDRs.
# To remove this rule entirely, add VPC interface endpoints for those services (extra cost).
# trivy:ignore:AWS-0104
resource "aws_vpc_security_group_egress_rule" "https" {
  for_each = {
    web = aws_security_group.web.id
    api = aws_security_group.api.id
  }
  security_group_id = each.value
  cidr_ipv4         = "0.0.0.0/0"
  ip_protocol       = "tcp"
  from_port         = 443
  to_port           = 443
  description       = "HTTPS to AWS APIs for ${each.key}"
}

resource "aws_vpc_security_group_egress_rule" "web_to_api" {
  security_group_id            = aws_security_group.web.id
  referenced_security_group_id = aws_security_group.api.id
  ip_protocol                  = "tcp"
  from_port                    = 8080
  to_port                      = 8080
  description                  = "Nginx to the API"
}

resource "aws_vpc_security_group_egress_rule" "api_to_data" {
  for_each          = { postgres = 5432, redis = 6379 }
  security_group_id = aws_security_group.api.id
  cidr_ipv4         = var.vpc_cidr_block
  ip_protocol       = "tcp"
  from_port         = each.value
  to_port           = each.value
  description       = "API to ${each.key} inside the VPC"
}

# The worker checks arbitrary public hosts on arbitrary ports and delivers webhooks/SMTP, so
# its egress cannot be narrowed by the network. The SSRF guard (applied to every connection,
# see docs/security.md I1) decides which destinations are allowed.
# trivy:ignore:AWS-0104
resource "aws_vpc_security_group_egress_rule" "worker_all_tcp" {
  security_group_id = aws_security_group.worker.id
  cidr_ipv4         = "0.0.0.0/0"
  ip_protocol       = "tcp"
  from_port         = 0
  to_port           = 65535
  description       = "Checks, webhooks, SMTP, AWS APIs and PostgreSQL"
}
