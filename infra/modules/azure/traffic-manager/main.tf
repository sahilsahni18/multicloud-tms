# The single failover switch for the API across both clouds: priority routing
# over the four cluster ingresses, health-probed on /actuator/health.
# 10 s probes x 3 tolerated failures + 60 s TTL keeps failover under 2 minutes.

terraform {
  required_providers {
    azurerm = {
      source = "hashicorp/azurerm"
    }
  }
}

resource "azurerm_traffic_manager_profile" "this" {
  name                   = var.name
  resource_group_name    = var.resource_group_name
  traffic_routing_method = "Priority"
  tags                   = var.tags

  dns_config {
    relative_name = var.dns_name
    ttl           = 60
  }

  monitor_config {
    protocol                     = "HTTP"
    port                         = 80
    path                         = "/actuator/health"
    interval_in_seconds          = 10
    timeout_in_seconds           = 5
    tolerated_number_of_failures = 3
    expected_status_code_ranges  = ["200-200"]
  }
}

# Endpoints are the cluster ingress load balancers, which only exist after the
# Kubernetes deploy, so they come in as a variable (empty on the first apply).
resource "azurerm_traffic_manager_external_endpoint" "this" {
  for_each = { for e in var.endpoints : e.name => e }

  name              = each.value.name
  profile_id        = azurerm_traffic_manager_profile.this.id
  target            = each.value.target
  priority          = each.value.priority
  endpoint_location = each.value.location
  enabled           = each.value.enabled
}
