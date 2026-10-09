output "fqdn" {
  value = azurerm_mysql_flexible_server.this.fqdn
}

output "jdbc_url" {
  value = "jdbc:mysql://${azurerm_mysql_flexible_server.this.fqdn}:3306/${var.database_name}?sslMode=REQUIRED"
}

output "username" {
  value = var.username
}

output "password" {
  value     = random_password.admin.result
  sensitive = true
}
