# One-time setup, run from your laptop with your own CLI logins:
#   - remote state: an S3 bucket (AWS stacks) and a storage container (Azure stacks)
#   - GitHub OIDC: an AWS IAM role and an Azure app that GitHub Actions can assume
#     for this repository only, so no long-lived cloud keys are ever stored.
#
# Its own state stays local (gitignored); everything here is cheap to recreate.
# Cost: S3 + a Standard_LRS storage account holding a few KB, i.e. effectively 0.

terraform {
  required_version = ">= 1.10"

  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "~> 6.0"
    }
    azurerm = {
      source  = "hashicorp/azurerm"
      version = "~> 4.0"
    }
    azuread = {
      source  = "hashicorp/azuread"
      version = "~> 3.0"
    }
    random = {
      source  = "hashicorp/random"
      version = "~> 3.6"
    }
  }
}

provider "aws" {
  region = var.aws_region
  default_tags {
    tags = local.tags
  }
}

provider "azurerm" {
  features {}
  subscription_id = var.azure_subscription_id
}

provider "azuread" {}

locals {
  tags = {
    project    = "trackflow"
    managed-by = "opentofu"
    stack      = "bootstrap"
  }
  # Workflows on main, pull requests, and the two GitHub environments used by
  # .github/workflows/cloud.yml ("cloud" = apply with approval, "cloud-teardown"
  # = plan/destroy) may assume the roles.
  # GitHub sends either the classic subject (repo:owner/name:...) or, with
  # immutable subjects on, one that embeds the owner and repo IDs
  # (repo:owner@123/name@456:...). Both are trusted.
  github_subject_prefixes = compact([
    "repo:${var.github_repository}",
    var.github_immutable_subject_prefix,
  ])
  github_subjects = flatten([
    for p in local.github_subject_prefixes : [
      "${p}:ref:refs/heads/main",
      "${p}:environment:cloud",
      "${p}:environment:cloud-teardown",
      "${p}:pull_request",
    ]
  ])
}

resource "random_string" "suffix" {
  length  = 6
  special = false
  upper   = false
}

# ---------------------------------------------------------------- AWS state --

resource "aws_s3_bucket" "state" {
  bucket = "trackflow-tofu-state-${random_string.suffix.result}"
}

resource "aws_s3_bucket_versioning" "state" {
  bucket = aws_s3_bucket.state.id
  versioning_configuration {
    status = "Enabled"
  }
}

resource "aws_s3_bucket_server_side_encryption_configuration" "state" {
  bucket = aws_s3_bucket.state.id
  rule {
    apply_server_side_encryption_by_default {
      sse_algorithm = "AES256"
    }
  }
}

resource "aws_s3_bucket_public_access_block" "state" {
  bucket                  = aws_s3_bucket.state.id
  block_public_acls       = true
  block_public_policy     = true
  ignore_public_acls      = true
  restrict_public_buckets = true
}

# ----------------------------------------------------------- AWS GitHub OIDC --

resource "aws_iam_openid_connect_provider" "github" {
  url            = "https://token.actions.githubusercontent.com"
  client_id_list = ["sts.amazonaws.com"]
}

data "aws_iam_policy_document" "github_trust" {
  statement {
    actions = ["sts:AssumeRoleWithWebIdentity"]
    principals {
      type        = "Federated"
      identifiers = [aws_iam_openid_connect_provider.github.arn]
    }
    condition {
      test     = "StringEquals"
      variable = "token.actions.githubusercontent.com:aud"
      values   = ["sts.amazonaws.com"]
    }
    condition {
      test     = "StringLike"
      variable = "token.actions.githubusercontent.com:sub"
      values   = local.github_subjects
    }
  }
}

resource "aws_iam_role" "github" {
  name                 = "trackflow-github-actions"
  assume_role_policy   = data.aws_iam_policy_document.github_trust.json
  max_session_duration = 3600
}

# Broad on purpose: the provisioning workflow creates VPCs, EKS, RDS, IAM roles.
# The trust policy above is what limits it to this repository.
resource "aws_iam_role_policy_attachment" "github_admin" {
  role       = aws_iam_role.github.name
  policy_arn = "arn:aws:iam::aws:policy/AdministratorAccess"
}

# -------------------------------------------------------------- Azure state --

resource "azurerm_resource_group" "state" {
  name     = "trackflow-tfstate"
  location = var.azure_location
  tags     = local.tags
}

resource "azurerm_storage_account" "state" {
  name                            = "tftfstate${random_string.suffix.result}"
  resource_group_name             = azurerm_resource_group.state.name
  location                        = azurerm_resource_group.state.location
  account_tier                    = "Standard"
  account_replication_type        = "LRS"
  min_tls_version                 = "TLS1_2"
  allow_nested_items_to_be_public = false
  tags                            = local.tags

  blob_properties {
    versioning_enabled = true
  }
}

resource "azurerm_storage_container" "state" {
  name                  = "tfstate"
  storage_account_id    = azurerm_storage_account.state.id
  container_access_type = "private"
}

# --------------------------------------------------------- Azure GitHub OIDC --

data "azurerm_subscription" "current" {}

resource "azuread_application" "github" {
  display_name = "trackflow-github-actions"
}

resource "azuread_service_principal" "github" {
  client_id = azuread_application.github.client_id
}

resource "azuread_application_federated_identity_credential" "github" {
  for_each = toset(local.github_subjects)

  application_id = azuread_application.github.id
  display_name   = replace(replace(each.value, "/[^A-Za-z0-9-]/", "-"), "--", "-")
  audiences      = ["api://AzureADTokenExchange"]
  issuer         = "https://token.actions.githubusercontent.com"
  subject        = each.value
}

# Contributor creates resources; User Access Administrator lets the stack grant
# AKS pull access on ACR (a role assignment).
resource "azurerm_role_assignment" "github" {
  for_each = toset(["Contributor", "User Access Administrator"])

  scope                = data.azurerm_subscription.current.id
  role_definition_name = each.value
  principal_id         = azuread_service_principal.github.object_id
}

resource "azurerm_role_assignment" "github_state" {
  scope                = azurerm_storage_account.state.id
  role_definition_name = "Storage Blob Data Contributor"
  principal_id         = azuread_service_principal.github.object_id
}
