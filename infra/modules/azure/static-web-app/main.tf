# Standby copy of the SPA on Azure Static Web Apps (Free plan, HTTPS built in).
# CD uploads frontend/dist with the deployment token.

terraform {
  required_providers {
    azurerm = {
      source = "hashicorp/azurerm"
    }
  }
}

resource "azurerm_static_web_app" "this" {
  name                = var.name
  location            = var.location
  resource_group_name = var.resource_group_name
  sku_tier            = "Free"
  sku_size            = "Free"
  tags                = var.tags
}
