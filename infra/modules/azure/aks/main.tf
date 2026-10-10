# AKS cluster(s) on the Free tier (no control-plane charge) with one small node.
# Pull access to ACR is granted to the kubelet identity, so no image pull secret.

terraform {
  required_providers {
    azurerm = {
      source = "hashicorp/azurerm"
    }
  }
}

locals {
  clusters = { for i in range(var.cluster_count) : tostring(i + 1) => "${var.name}-${i + 1}" }
}

# A static egress IP per cluster: its address is what the MySQL firewall allows.
resource "azurerm_public_ip" "egress" {
  for_each = local.clusters

  name                = "${each.value}-egress"
  location            = var.location
  resource_group_name = var.resource_group_name
  allocation_method   = "Static"
  sku                 = "Standard"
  tags                = var.tags
}

resource "azurerm_kubernetes_cluster" "this" {
  for_each = local.clusters

  name                = each.value
  location            = var.location
  resource_group_name = var.resource_group_name
  dns_prefix          = each.value
  kubernetes_version  = var.kubernetes_version
  sku_tier            = "Free"

  default_node_pool {
    name                        = "system"
    vm_size                     = var.node_vm_size
    node_count                  = var.node_count
    os_disk_size_gb             = 30
    temporary_name_for_rotation = "rotate"
    upgrade_settings {
      max_surge = "1"
    }
  }

  identity {
    type = "SystemAssigned"
  }

  network_profile {
    network_plugin      = "azure"
    network_plugin_mode = "overlay"
    load_balancer_sku   = "standard"
    load_balancer_profile {
      outbound_ip_address_ids = [azurerm_public_ip.egress[each.key].id]
    }
  }

  oidc_issuer_enabled = true
  tags                = var.tags
}

resource "azurerm_role_assignment" "acr_pull" {
  for_each = var.grant_acr_pull ? azurerm_kubernetes_cluster.this : {}

  scope                            = var.acr_id
  role_definition_name             = "AcrPull"
  principal_id                     = each.value.kubelet_identity[0].object_id
  skip_service_principal_aad_check = true
}

# The egress IP lives in our resource group, not the cluster's node resource
# group, so the cluster identity needs join rights on it; without this the
# cloud controller fails every load balancer with LinkedAuthorizationFailed.
resource "azurerm_role_assignment" "egress_ip" {
  for_each = azurerm_kubernetes_cluster.this

  scope                            = azurerm_public_ip.egress[each.key].id
  role_definition_name             = "Network Contributor"
  principal_id                     = each.value.identity[0].principal_id
  skip_service_principal_aad_check = true
}
