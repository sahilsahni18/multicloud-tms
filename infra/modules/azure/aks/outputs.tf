output "cluster_names" {
  value = [for c in azurerm_kubernetes_cluster.this : c.name]
}

output "egress_ips" {
  description = "Public egress IPs of the clusters; the MySQL firewall allows these."
  value       = [for ip in azurerm_public_ip.egress : ip.ip_address]
}
