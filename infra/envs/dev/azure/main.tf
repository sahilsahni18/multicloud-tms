# TrackFlow on Azure, dev: AKS in two regions, one MySQL in the primary region,
# the standby SPA on Static Web Apps, and Traffic Manager, the failover switch
# for the API across all four clusters (AWS and Azure).
#
#   eastasia (primary)               koreacentral (standby)
#   AKS + ACR + MySQL Flexible       AKS  (backend uses the eastasia MySQL,
#                                          admitted by its egress IP)
#   Traffic Manager: 1 AWS primary, 2 AWS standby, 3 Azure primary, 4 Azure standby

terraform {
  required_version = ">= 1.10"

  required_providers {
    azurerm = {
      source  = "hashicorp/azurerm"
      version = "~> 4.0"
    }
    random = {
      source  = "hashicorp/random"
      version = "~> 3.6"
    }
  }

  # Filled in by backend.hcl (see backend.hcl.example): tofu init -backend-config=backend.hcl
  backend "azurerm" {}
}

provider "azurerm" {
  features {
    resource_group {
      # tofu destroy must succeed even if AKS left load balancers or disks behind.
      prevent_deletion_if_contains_resources = false
    }
  }
  subscription_id = var.subscription_id # null = ARM_SUBSCRIPTION_ID / az CLI default
}

locals {
  name = "trackflow-${var.environment}"
  tags = {
    project     = "trackflow"
    environment = var.environment
    managed-by  = "opentofu"
  }
}

# Globally unique names (ACR, MySQL) get a stable random suffix.
resource "random_string" "suffix" {
  length  = 5
  special = false
  upper   = false
}

resource "azurerm_resource_group" "this" {
  name     = local.name
  location = var.primary_location
  tags     = local.tags
}

module "acr" {
  source = "../../../modules/azure/acr"

  name                = "trackflow${var.environment}${random_string.suffix.result}"
  location            = azurerm_resource_group.this.location
  resource_group_name = azurerm_resource_group.this.name
  tags                = local.tags
}

module "aks_primary" {
  source = "../../../modules/azure/aks"

  name                = "${local.name}-${var.primary_location}"
  location            = var.primary_location
  resource_group_name = azurerm_resource_group.this.name
  cluster_count       = var.cluster_count
  node_vm_size        = var.node_vm_size
  acr_id              = module.acr.id
  tags                = local.tags
}

module "aks_standby" {
  source = "../../../modules/azure/aks"

  name                = "${local.name}-${var.standby_location}"
  location            = var.standby_location
  resource_group_name = azurerm_resource_group.this.name
  cluster_count       = var.cluster_count
  node_vm_size        = var.node_vm_size
  acr_id              = module.acr.id
  tags                = local.tags
}

module "mysql" {
  source = "../../../modules/azure/mysql"

  name                = "${local.name}-mysql-${random_string.suffix.result}"
  location            = var.primary_location
  resource_group_name = azurerm_resource_group.this.name
  allowed_ips         = concat(module.aks_primary.egress_ips, module.aks_standby.egress_ips)
  tags                = local.tags
}

module "static_web_app" {
  source = "../../../modules/azure/static-web-app"

  name                = "${local.name}-web"
  location            = var.static_web_app_location
  resource_group_name = azurerm_resource_group.this.name
  tags                = local.tags
}

module "traffic_manager" {
  source = "../../../modules/azure/traffic-manager"

  name                = "${local.name}-api"
  dns_name            = var.traffic_manager_dns_name
  resource_group_name = azurerm_resource_group.this.name
  endpoints           = var.traffic_manager_endpoints
  tags                = local.tags
}

# ------------------------------------------------------------------ secrets --

# JWT signing key shared by this cloud's clusters (they share one database, so
# a token issued in one region stays valid after failover to the other).
# Generated here so no person or workflow ever handles it; CD reads it from state.
resource "random_bytes" "jwt_secret" {
  length = 64
}
