# ECS Fargate: api, worker and web services from the same images as Docker Compose.
# Service Connect gives Nginx the hostname "api", exactly as in Compose, so the web image
# needs no AWS-specific configuration.

resource "aws_ecs_cluster" "this" {
  name = var.name
  setting {
    name  = "containerInsights"
    value = "enhanced"
  }
}

resource "aws_service_discovery_http_namespace" "this" {
  name        = "${var.name}.local"
  description = "Service Connect namespace for ${var.name}"
}

resource "aws_cloudwatch_log_group" "service" {
  for_each          = toset(["api", "worker", "web"])
  name              = "/ecs/${var.name}/${each.key}"
  retention_in_days = var.log_retention_days
}

locals {
  image = { for k, repo in aws_ecr_repository.this : k => "${repo.repository_url}:${var.image_tag}" }

  # Settings shared by the API and the worker.
  common_environment = [
    { name = "DB_HOST", value = var.postgres_host },
    { name = "DB_PORT", value = tostring(var.postgres_port) },
    { name = "POSTGRES_DB", value = var.postgres_db_name },
    { name = "DB_SSLMODE", value = "require" },
    { name = "MONITORING_ALLOW_PRIVATE_TARGETS", value = "false" },
  ]
  common_secrets = [
    { name = "POSTGRES_USER", valueFrom = "${var.postgres_master_secret_arn}:username::" },
    { name = "POSTGRES_PASSWORD", valueFrom = "${var.postgres_master_secret_arn}:password::" },
    { name = "ALERT_ENCRYPTION_KEY", valueFrom = aws_secretsmanager_secret.alert_encryption_key.arn },
  ]

  # Same hardening as Docker Compose: non-root, read-only root filesystem, writable /tmp only.
  hardened = {
    readonlyRootFilesystem = true
    mountPoints            = [{ sourceVolume = "tmp", containerPath = "/tmp", readOnly = false }]
    linuxParameters        = { capabilities = { drop = ["ALL"] }, initProcessEnabled = true }
    stopTimeout            = 30
  }

  log_config = { for svc in ["api", "worker", "web"] : svc => {
    logDriver = "awslogs"
    options = {
      awslogs-group         = aws_cloudwatch_log_group.service[svc].name
      awslogs-region        = data.aws_region.current.region
      awslogs-stream-prefix = svc
    }
  } }
}

resource "aws_ecs_task_definition" "api" {
  family                   = "${var.name}-api"
  requires_compatibilities = ["FARGATE"]
  network_mode             = "awsvpc"
  cpu                      = var.api_cpu
  memory                   = var.api_memory
  execution_role_arn       = aws_iam_role.execution.arn
  task_role_arn            = aws_iam_role.task["api"].arn
  runtime_platform {
    operating_system_family = "LINUX"
    cpu_architecture        = "ARM64" # Graviton: cheaper; images are multi-arch
  }
  volume {
    name = "tmp"
  }

  container_definitions = jsonencode([merge(local.hardened, {
    name      = "api"
    image     = local.image["api"]
    essential = true
    user      = "10001:10001"
    portMappings = [
      { name = "api-http", containerPort = 8080, protocol = "tcp", appProtocol = "http" },
      { name = "api-management", containerPort = 8081, protocol = "tcp" },
    ]
    environment = concat(local.common_environment, [
      { name = "REDIS_HOST", value = var.redis_host },
      { name = "REDIS_PORT", value = tostring(var.redis_port) },
      { name = "REDIS_SSL", value = "true" },
      { name = "SESSION_REDIS_CONFIGURE_ACTION", value = "none" },
      { name = "SESSION_COOKIE_SECURE", value = "true" },
      { name = "ADMIN_EMAIL", value = var.admin_email },
    ])
    secrets = concat(local.common_secrets, [
      { name = "SPRING_DATA_REDIS_PASSWORD", valueFrom = var.redis_auth_secret_arn },
      { name = "ADMIN_PASSWORD", valueFrom = aws_secretsmanager_secret.admin_password.arn },
    ])
    healthCheck = {
      command     = ["CMD", "wget", "-qO-", "http://localhost:8081/actuator/health/readiness"]
      interval    = 15
      timeout     = 5
      retries     = 4
      startPeriod = 90
    }
    logConfiguration = local.log_config["api"]
  })])
}

resource "aws_ecs_task_definition" "worker" {
  family                   = "${var.name}-worker"
  requires_compatibilities = ["FARGATE"]
  network_mode             = "awsvpc"
  cpu                      = var.worker_cpu
  memory                   = var.worker_memory
  execution_role_arn       = aws_iam_role.execution.arn
  task_role_arn            = aws_iam_role.task["worker"].arn
  runtime_platform {
    operating_system_family = "LINUX"
    cpu_architecture        = "ARM64"
  }
  volume {
    name = "tmp"
  }

  container_definitions = jsonencode([merge(local.hardened, {
    name      = "worker"
    image     = local.image["worker"]
    essential = true
    user      = "10001:10001"
    portMappings = [
      { name = "worker-management", containerPort = 8082, protocol = "tcp" },
    ]
    environment = concat(local.common_environment, [
      { name = "WORKER_CONCURRENCY", value = tostring(var.worker_concurrency) },
      { name = "SPRING_MAIL_HOST", value = var.smtp_host },
      { name = "SPRING_MAIL_PORT", value = tostring(var.smtp_port) },
      { name = "SPRING_MAIL_USERNAME", value = var.smtp_username },
      { name = "SPRING_MAIL_PROPERTIES_MAIL_SMTP_STARTTLS_ENABLE", value = "true" },
      { name = "SPRING_MAIL_PROPERTIES_MAIL_SMTP_STARTTLS_REQUIRED", value = var.smtp_host == "" ? "false" : "true" },
      { name = "ALERT_EMAIL_FROM", value = var.alert_email_from },
    ])
    secrets = concat(local.common_secrets, [
      { name = "SPRING_MAIL_PASSWORD", valueFrom = aws_secretsmanager_secret.smtp_password.arn },
    ])
    healthCheck = {
      command     = ["CMD", "wget", "-qO-", "http://localhost:8082/actuator/health/readiness"]
      interval    = 15
      timeout     = 5
      retries     = 4
      startPeriod = 90
    }
    logConfiguration = local.log_config["worker"]
  })])
}

resource "aws_ecs_task_definition" "web" {
  family                   = "${var.name}-web"
  requires_compatibilities = ["FARGATE"]
  network_mode             = "awsvpc"
  cpu                      = 256
  memory                   = 512
  execution_role_arn       = aws_iam_role.execution.arn
  task_role_arn            = aws_iam_role.task["web"].arn
  runtime_platform {
    operating_system_family = "LINUX"
    cpu_architecture        = "ARM64"
  }
  volume {
    name = "tmp"
  }

  container_definitions = jsonencode([merge(local.hardened, {
    name         = "web"
    image        = local.image["web"]
    essential    = true
    user         = "101:101"
    portMappings = [{ name = "web-http", containerPort = 8080, protocol = "tcp", appProtocol = "http" }]
    healthCheck = {
      command     = ["CMD", "wget", "-qO-", "http://127.0.0.1:8080/"]
      interval    = 15
      timeout     = 5
      retries     = 3
      startPeriod = 10
    }
    logConfiguration = local.log_config["web"]
  })])
}

# ---------------------------------------------------------------------------------------
# Services
# ---------------------------------------------------------------------------------------
resource "aws_ecs_service" "api" {
  name                   = "${var.name}-api"
  cluster                = aws_ecs_cluster.this.id
  task_definition        = aws_ecs_task_definition.api.arn
  desired_count          = var.api_min_tasks
  launch_type            = "FARGATE"
  enable_execute_command = false
  propagate_tags         = "SERVICE"
  # The API migrates the schema: Terraform waits for it before rolling out the worker.
  wait_for_steady_state = true

  network_configuration {
    subnets          = var.app_subnet_ids
    security_groups  = [aws_security_group.api.id]
    assign_public_ip = false
  }

  service_connect_configuration {
    enabled   = true
    namespace = aws_service_discovery_http_namespace.this.arn
    service {
      port_name      = "api-http"
      discovery_name = "api"
      client_alias {
        dns_name = "api"
        port     = 8080
      }
    }
  }

  deployment_circuit_breaker {
    enable   = true
    rollback = true
  }
  deployment_minimum_healthy_percent = 100
  deployment_maximum_percent         = 200

  lifecycle {
    ignore_changes = [desired_count] # managed by autoscaling
  }
}

resource "aws_ecs_service" "worker" {
  name                   = "${var.name}-worker"
  cluster                = aws_ecs_cluster.this.id
  task_definition        = aws_ecs_task_definition.worker.arn
  desired_count          = var.worker_min_tasks
  launch_type            = "FARGATE"
  enable_execute_command = false
  propagate_tags         = "SERVICE"

  network_configuration {
    subnets          = var.app_subnet_ids
    security_groups  = [aws_security_group.worker.id]
    assign_public_ip = false
  }

  deployment_circuit_breaker {
    enable   = true
    rollback = true
  }
  deployment_minimum_healthy_percent = 50
  deployment_maximum_percent         = 200

  lifecycle {
    ignore_changes = [desired_count]
  }

  depends_on = [aws_ecs_service.api]
}

resource "aws_ecs_service" "web" {
  name                              = "${var.name}-web"
  cluster                           = aws_ecs_cluster.this.id
  task_definition                   = aws_ecs_task_definition.web.arn
  desired_count                     = var.web_min_tasks
  launch_type                       = "FARGATE"
  enable_execute_command            = false
  propagate_tags                    = "SERVICE"
  health_check_grace_period_seconds = 30

  network_configuration {
    subnets          = var.app_subnet_ids
    security_groups  = [aws_security_group.web.id]
    assign_public_ip = false
  }

  # Client-only Service Connect: resolves "api" for Nginx's upstream.
  service_connect_configuration {
    enabled   = true
    namespace = aws_service_discovery_http_namespace.this.arn
  }

  load_balancer {
    target_group_arn = aws_lb_target_group.web.arn
    container_name   = "web"
    container_port   = 8080
  }

  deployment_circuit_breaker {
    enable   = true
    rollback = true
  }
  deployment_minimum_healthy_percent = 100
  deployment_maximum_percent         = 200

  lifecycle {
    ignore_changes = [desired_count]
  }

  depends_on = [aws_lb_listener.https, aws_ecs_service.api]
}

# ---------------------------------------------------------------------------------------
# Autoscaling (CPU target tracking)
# ---------------------------------------------------------------------------------------
locals {
  scaling = {
    api    = { service = aws_ecs_service.api.name, min = var.api_min_tasks, max = var.api_max_tasks }
    worker = { service = aws_ecs_service.worker.name, min = var.worker_min_tasks, max = var.worker_max_tasks }
    web    = { service = aws_ecs_service.web.name, min = var.web_min_tasks, max = var.web_max_tasks }
  }
}

resource "aws_appautoscaling_target" "this" {
  for_each           = local.scaling
  service_namespace  = "ecs"
  scalable_dimension = "ecs:service:DesiredCount"
  resource_id        = "service/${aws_ecs_cluster.this.name}/${each.value.service}"
  min_capacity       = each.value.min
  max_capacity       = each.value.max
}

resource "aws_appautoscaling_policy" "cpu" {
  for_each           = local.scaling
  name               = "${each.key}-cpu-target"
  policy_type        = "TargetTrackingScaling"
  service_namespace  = aws_appautoscaling_target.this[each.key].service_namespace
  scalable_dimension = aws_appautoscaling_target.this[each.key].scalable_dimension
  resource_id        = aws_appautoscaling_target.this[each.key].resource_id

  target_tracking_scaling_policy_configuration {
    target_value       = 60
    scale_in_cooldown  = 300
    scale_out_cooldown = 60
    predefined_metric_specification {
      predefined_metric_type = "ECSServiceAverageCPUUtilization"
    }
  }
}
