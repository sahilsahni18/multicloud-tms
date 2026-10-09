# MySQL 8 on the free-tier instance size, reachable only from the EKS nodes
# (same VPC) and from the peered standby VPC. Never publicly accessible.

terraform {
  required_providers {
    aws = {
      source = "hashicorp/aws"
    }
    random = {
      source = "hashicorp/random"
    }
  }
}

resource "random_password" "master" {
  length  = 32
  special = false # avoids escaping problems in JDBC URLs and shell scripts
}

resource "aws_db_subnet_group" "this" {
  name       = var.name
  subnet_ids = var.subnet_ids
}

resource "aws_security_group" "this" {
  name        = "${var.name}-mysql"
  description = "MySQL from TrackFlow EKS nodes only"
  vpc_id      = var.vpc_id
}

# count, not for_each: the group IDs are only known after the clusters exist.
resource "aws_vpc_security_group_ingress_rule" "from_sg" {
  count = length(var.allowed_security_group_ids)

  security_group_id            = aws_security_group.this.id
  referenced_security_group_id = var.allowed_security_group_ids[count.index]
  ip_protocol                  = "tcp"
  from_port                    = 3306
  to_port                      = 3306
}

resource "aws_vpc_security_group_ingress_rule" "from_cidr" {
  for_each = toset(var.allowed_cidr_blocks)

  security_group_id = aws_security_group.this.id
  cidr_ipv4         = each.value
  ip_protocol       = "tcp"
  from_port         = 3306
  to_port           = 3306
}

resource "aws_db_parameter_group" "this" {
  name   = var.name
  family = "mysql8.0"

  # Same settings as the local MySQL (docker-compose.yml).
  parameter {
    name  = "time_zone"
    value = "UTC"
  }
  parameter {
    name  = "character_set_server"
    value = "utf8mb4"
  }
  parameter {
    name  = "collation_server"
    value = "utf8mb4_0900_ai_ci"
  }
  parameter {
    name  = "require_secure_transport"
    value = "1"
  }
}

resource "aws_db_instance" "this" {
  identifier     = var.name
  engine         = "mysql"
  engine_version = var.engine_version
  instance_class = var.instance_class

  allocated_storage     = 20
  max_allocated_storage = 0 # no autoscaling past the free 20 GB
  storage_type          = "gp2"
  storage_encrypted     = true

  db_name  = var.database_name
  username = var.username
  password = random_password.master.result

  db_subnet_group_name   = aws_db_subnet_group.this.name
  vpc_security_group_ids = [aws_security_group.this.id]
  parameter_group_name   = aws_db_parameter_group.this.name
  publicly_accessible    = false
  multi_az               = false

  backup_retention_period = var.backup_retention_days
  backup_window           = "03:00-04:00"
  maintenance_window      = "sun:04:30-sun:05:30"

  # Demo environment: destroy must finish in one go and leave nothing billable.
  skip_final_snapshot = true
  deletion_protection = false
  apply_immediately   = true
}
