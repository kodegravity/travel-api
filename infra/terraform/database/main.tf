# PostgreSQL for app data (traveler profiles; Okta keeps identity and credentials). RDS single-AZ db.t4g.micro.
# Cost: about $14/month (instance ~$11.70, 20 GB gp3 ~$2.30, managed secret $0.40); backups up to 20 GB are free.
# Reachable only from the travel-session tasks, over TLS (rds.force_ssl is on by default from PostgreSQL 15).

data "aws_subnet" "first" {
  id = var.subnet_ids[0]
}

resource "aws_security_group" "db" {
  name        = "${var.name}-sg"
  description = "PostgreSQL on 5432 from the travel-session tasks only"
  vpc_id      = data.aws_subnet.first.vpc_id

  tags = {
    Name = "${var.name}-sg"
  }
}

resource "aws_vpc_security_group_ingress_rule" "from_tasks" {
  for_each = toset(var.client_security_group_ids)

  security_group_id            = aws_security_group.db.id
  referenced_security_group_id = each.value
  ip_protocol                  = "tcp"
  from_port                    = 5432
  to_port                      = 5432
  description                  = "PostgreSQL from travel-session tasks"
}

resource "aws_db_subnet_group" "db" {
  name       = var.name
  subnet_ids = var.subnet_ids
}

resource "aws_db_instance" "this" {
  identifier     = var.name
  engine         = "postgres"
  engine_version = var.engine_version
  instance_class = var.instance_class
  db_name        = "roam"
  username       = "roam_admin"

  # RDS generates the password and keeps it in Secrets Manager, rotating it every 7 days. It never enters Terraform
  # state; the app reads it at connect time (TRAVEL_POSTGRES_SECRET_ID), so a rotation needs no restart.
  manage_master_user_password = true

  allocated_storage = var.storage_gb
  # No storage autoscaling: growth needs a deliberate change, so the bill cannot creep.
  max_allocated_storage = 0
  storage_type          = "gp3"
  storage_encrypted     = true

  db_subnet_group_name   = aws_db_subnet_group.db.name
  vpc_security_group_ids = [aws_security_group.db.id]
  publicly_accessible    = false
  multi_az               = false
  ca_cert_identifier     = "rds-ca-rsa2048-g1"

  backup_retention_period    = 7
  backup_window              = "07:00-07:30"
  maintenance_window         = "sun:08:00-sun:08:30"
  auto_minor_version_upgrade = true
  copy_tags_to_snapshot      = true

  deletion_protection       = true
  skip_final_snapshot       = false
  final_snapshot_identifier = "${var.name}-final"

  performance_insights_enabled = false
  monitoring_interval          = 0

  tags = {
    project = "roam-ai"
  }
}

# The travel-session task reads the managed secret itself (not through ECS secrets), so it can re-read after a rotation.
data "aws_iam_role" "travel_session_task" {
  name = var.task_role_name
}

resource "aws_iam_role_policy" "read_db_secret" {
  name = "read-${var.name}-secret"
  role = data.aws_iam_role.travel_session_task.id

  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Effect   = "Allow"
      Action   = ["secretsmanager:GetSecretValue"]
      Resource = [aws_db_instance.this.master_user_secret[0].secret_arn]
    }]
  })
}
