# MySQL Flexible Server on the free B1ms size. Public endpoint, but the
# firewall admits only the AKS egress IPs, and TLS is enforced (server default).

terraform {
  required_providers {
    azurerm = {
      source = "hashicorp/azurerm"
    }
    random = {
      source = "hashicorp/random"
    }
  }
}

resource "random_password" "admin" {
  length  = 32
  special = false
}

resource "azurerm_mysql_flexible_server" "this" {
  name                   = var.name
  location               = var.location
  resource_group_name    = var.resource_group_name
  sku_name               = var.sku_name
  version                = "8.0.21"
  administrator_login    = var.username
  administrator_password = random_password.admin.result

  backup_retention_days        = var.backup_retention_days
  geo_redundant_backup_enabled = false

  storage {
    size_gb           = 20
    auto_grow_enabled = false
  }

  tags = var.tags

  lifecycle {
    # Azure picks the zone when none is given; do not fight it on later plans.
    ignore_changes = [zone]
  }
}

resource "azurerm_mysql_flexible_database" "this" {
  name                = var.database_name
  resource_group_name = var.resource_group_name
  server_name         = azurerm_mysql_flexible_server.this.name
  charset             = "utf8mb4"
  collation           = "utf8mb4_0900_ai_ci"
}

resource "azurerm_mysql_flexible_server_configuration" "time_zone" {
  name                = "time_zone"
  resource_group_name = var.resource_group_name
  server_name         = azurerm_mysql_flexible_server.this.name
  value               = "+00:00"
}

resource "azurerm_mysql_flexible_server_firewall_rule" "allowed" {
  count = length(var.allowed_ips)

  name                = "allowed-${count.index}"
  resource_group_name = var.resource_group_name
  server_name         = azurerm_mysql_flexible_server.this.name
  start_ip_address    = var.allowed_ips[count.index]
  end_ip_address      = var.allowed_ips[count.index]
}
