# TrackFlow on AWS, dev: EKS in two regions, one RDS in the primary region.
#
#   us-east-1 (primary)          us-west-2 (standby)
#   VPC 10.10/16  <-- peering -->  VPC 10.20/16
#   EKS + ECR + RDS               EKS + ECR  (backend uses the us-east-1 RDS)
#   S3 + CloudFront (SPA, /api -> Traffic Manager)
#
# Traffic Manager itself lives in the Azure stack; only its hostname is used here.

terraform {
  required_version = ">= 1.10"

  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "~> 6.0"
    }
    random = {
      source  = "hashicorp/random"
      version = "~> 3.6"
    }
  }

  # Filled in by backend.hcl (see backend.hcl.example): tofu init -backend-config=backend.hcl
  backend "s3" {}
}

locals {
  name = "trackflow-${var.environment}"
  tags = {
    project     = "trackflow"
    environment = var.environment
    managed-by  = "opentofu"
  }
  api_fqdn = "${var.traffic_manager_dns_name}.trafficmanager.net"
}

provider "aws" {
  alias  = "primary"
  region = var.primary_region
  default_tags {
    tags = local.tags
  }
}

provider "aws" {
  alias  = "standby"
  region = var.standby_region
  default_tags {
    tags = local.tags
  }
}

# ----------------------------------------------------------------- network --

module "vpc_primary" {
  source    = "../../../modules/aws/vpc"
  providers = { aws = aws.primary }

  name       = "${local.name}-${var.primary_region}"
  cidr_block = var.primary_cidr
}

module "vpc_standby" {
  source    = "../../../modules/aws/vpc"
  providers = { aws = aws.standby }

  name       = "${local.name}-${var.standby_region}"
  cidr_block = var.standby_cidr
}

# Cross-region peering lets the standby backend reach the primary RDS privately.
# Creating it is free; only cross-region traffic is billed (cents for a demo).
resource "aws_vpc_peering_connection" "primary_to_standby" {
  provider    = aws.primary
  vpc_id      = module.vpc_primary.vpc_id
  peer_vpc_id = module.vpc_standby.vpc_id
  peer_region = var.standby_region
  tags        = { Name = "${local.name}-peering" }
}

resource "aws_vpc_peering_connection_accepter" "standby" {
  provider                  = aws.standby
  vpc_peering_connection_id = aws_vpc_peering_connection.primary_to_standby.id
  auto_accept               = true
  tags                      = { Name = "${local.name}-peering" }
}

resource "aws_route" "primary_db_to_standby" {
  provider                  = aws.primary
  route_table_id            = module.vpc_primary.database_route_table_id
  destination_cidr_block    = var.standby_cidr
  vpc_peering_connection_id = aws_vpc_peering_connection.primary_to_standby.id
}

resource "aws_route" "standby_nodes_to_primary" {
  provider                  = aws.standby
  route_table_id            = module.vpc_standby.public_route_table_id
  destination_cidr_block    = var.primary_cidr
  vpc_peering_connection_id = aws_vpc_peering_connection_accepter.standby.id
}

# --------------------------------------------------------------- registries --

module "ecr_primary" {
  source    = "../../../modules/aws/ecr"
  providers = { aws = aws.primary }
}

module "ecr_standby" {
  source    = "../../../modules/aws/ecr"
  providers = { aws = aws.standby }
}

# ----------------------------------------------------------------- clusters --

module "eks_primary" {
  source    = "../../../modules/aws/eks"
  providers = { aws = aws.primary }

  name               = "${local.name}-${var.primary_region}"
  cluster_count      = var.cluster_count
  kubernetes_version = var.kubernetes_version
  subnet_ids         = module.vpc_primary.public_subnet_ids
  node_instance_type = var.node_instance_type
  admin_role_arns    = var.admin_role_arns
}

module "eks_standby" {
  source    = "../../../modules/aws/eks"
  providers = { aws = aws.standby }

  name               = "${local.name}-${var.standby_region}"
  cluster_count      = var.cluster_count
  kubernetes_version = var.kubernetes_version
  subnet_ids         = module.vpc_standby.public_subnet_ids
  node_instance_type = var.node_instance_type
  admin_role_arns    = var.admin_role_arns
}

# ----------------------------------------------------------------- database --

module "rds" {
  source    = "../../../modules/aws/rds"
  providers = { aws = aws.primary }

  name                       = local.name
  vpc_id                     = module.vpc_primary.vpc_id
  subnet_ids                 = module.vpc_primary.database_subnet_ids
  allowed_security_group_ids = module.eks_primary.cluster_security_group_ids
  allowed_cidr_blocks        = [var.standby_cidr]
}

# Production path (paid account only; the Free plan blocks cross-region
# replicas): a read replica next to the standby cluster.
resource "aws_db_subnet_group" "replica" {
  count    = var.enable_replicas ? 1 : 0
  provider = aws.standby

  name       = "${local.name}-replica"
  subnet_ids = module.vpc_standby.database_subnet_ids
}

resource "aws_security_group" "replica" {
  count    = var.enable_replicas ? 1 : 0
  provider = aws.standby

  name        = "${local.name}-mysql-replica"
  description = "MySQL replica from TrackFlow EKS nodes only"
  vpc_id      = module.vpc_standby.vpc_id

  ingress {
    from_port       = 3306
    to_port         = 3306
    protocol        = "tcp"
    security_groups = module.eks_standby.cluster_security_group_ids
  }
}

resource "aws_db_instance" "replica" {
  count    = var.enable_replicas ? 1 : 0
  provider = aws.standby

  identifier             = "${local.name}-replica"
  replicate_source_db    = module.rds.arn
  instance_class         = "db.t4g.micro"
  db_subnet_group_name   = aws_db_subnet_group.replica[0].name
  vpc_security_group_ids = [aws_security_group.replica[0].id]
  storage_encrypted      = true
  publicly_accessible    = false
  skip_final_snapshot    = true
}

# ----------------------------------------------------------------- frontend --

module "frontend" {
  source    = "../../../modules/aws/frontend"
  providers = { aws = aws.primary }

  name              = local.name
  api_origin_domain = local.api_fqdn
}

module "dns" {
  count     = var.domain_name == "" ? 0 : 1
  source    = "../../../modules/aws/route53"
  providers = { aws = aws.primary }

  zone_name                 = var.domain_name
  cloudfront_domain_name    = module.frontend.domain_name
  cloudfront_hosted_zone_id = module.frontend.hosted_zone_id
  api_target_fqdn           = local.api_fqdn
}
