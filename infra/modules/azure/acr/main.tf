# Container registry. Geo-replication needs the Premium SKU, so both AKS
# regions pull from this one registry (cross-region pulls are fine for a demo).

terraform {
  required_providers {
    azurerm = {
      source = "hashicorp/azurerm"
    }
  }
}

resource "azurerm_container_registry" "this" {
  name                = var.name
  location            = var.location
  resource_group_name = var.resource_group_name
  sku                 = var.sku
  admin_enabled       = false # CI pushes with OIDC, AKS pulls with its identity
  tags                = var.tags
}
