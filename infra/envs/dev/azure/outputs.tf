output "resource_group" {
  value = azurerm_resource_group.this.name
}

output "aks_clusters" {
  description = "az aks get-credentials -g <resource_group> -n <cluster>"
  value = {
    (var.primary_location) = module.aks_primary.cluster_names
    (var.standby_location) = module.aks_standby.cluster_names
  }
}

output "acr_login_server" {
  value = module.acr.login_server
}

output "db_jdbc_url" {
  value = module.mysql.jdbc_url
}

output "db_username" {
  value = module.mysql.username
}

output "db_password" {
  description = "tofu output -raw db_password (goes into the backend-secrets Secret)"
  value       = module.mysql.password
  sensitive   = true
}

output "static_web_app_url" {
  value = "https://${module.static_web_app.default_host_name}"
}

output "static_web_app_token" {
  value     = module.static_web_app.api_key
  sensitive = true
}

output "api_fqdn" {
  value = module.traffic_manager.fqdn
}
