output "cluster_names" {
  value = [for c in aws_eks_cluster.this : c.name]
}

output "cluster_endpoints" {
  value = { for k, c in aws_eks_cluster.this : c.name => c.endpoint }
}

output "cluster_security_group_ids" {
  description = "Security groups EKS attaches to nodes; used to let them reach RDS."
  value       = [for c in aws_eks_cluster.this : c.vpc_config[0].cluster_security_group_id]
}
