output "default_host_name" {
  value = azurerm_static_web_app.this.default_host_name
}

output "api_key" {
  description = "Deployment token for the CD upload step."
  value       = azurerm_static_web_app.this.api_key
  sensitive   = true
}
