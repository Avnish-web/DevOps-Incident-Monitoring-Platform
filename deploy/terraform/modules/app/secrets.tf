# Application secrets, generated here and injected into tasks by ECS (never as plain
# environment variables in the task definition).

# 32 random bytes, base64: the AES-256 key for alert channel targets (shared by API and worker).
resource "random_bytes" "alert_encryption_key" {
  length = 32
}

resource "aws_secretsmanager_secret" "alert_encryption_key" {
  name        = "${var.name}/alert-encryption-key"
  description = "AES-256 key encrypting alert channel targets at rest. Losing it makes stored channels unreadable."
  # Longer recovery window: this key protects data, it cannot simply be regenerated.
  recovery_window_in_days = 30
}

resource "aws_secretsmanager_secret_version" "alert_encryption_key" {
  secret_id     = aws_secretsmanager_secret.alert_encryption_key.id
  secret_string = random_bytes.alert_encryption_key.base64
  lifecycle {
    # Rotating this key requires re-encrypting stored channels first.
    ignore_changes = [secret_string]
  }
}

resource "random_password" "admin" {
  length  = 32
  special = false
}

resource "aws_secretsmanager_secret" "admin_password" {
  name                    = "${var.name}/bootstrap-admin-password"
  description             = "Initial password of the bootstrap administrator (${var.admin_email}); change it after the first login."
  recovery_window_in_days = 7
}

resource "aws_secretsmanager_secret_version" "admin_password" {
  secret_id     = aws_secretsmanager_secret.admin_password.id
  secret_string = random_password.admin.result
}

# Optional SMTP password for e-mail alerts (set its value in Secrets Manager, not Terraform).
resource "aws_secretsmanager_secret" "smtp_password" {
  name                    = "${var.name}/smtp-password"
  description             = "SMTP password for e-mail alerts (set manually; leave empty if unused)."
  recovery_window_in_days = 7
}

resource "aws_secretsmanager_secret_version" "smtp_password" {
  secret_id     = aws_secretsmanager_secret.smtp_password.id
  secret_string = " "
  lifecycle {
    ignore_changes = [secret_string]
  }
}
