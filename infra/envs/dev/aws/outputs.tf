output "eks_clusters" {
  description = "aws eks update-kubeconfig --region <region> --name <cluster>"
  value = {
    (var.primary_region) = module.eks_primary.cluster_names
    (var.standby_region) = module.eks_standby.cluster_names
  }
}

output "ecr_repositories" {
  value = {
    (var.primary_region) = module.ecr_primary.repository_urls
    (var.standby_region) = module.ecr_standby.repository_urls
  }
}

output "db_jdbc_url" {
  value = module.rds.jdbc_url
}

output "db_username" {
  value = module.rds.username
}

output "db_password" {
  description = "tofu output -raw db_password (goes into the backend-secrets Secret)"
  value       = module.rds.password
  sensitive   = true
}

output "frontend_bucket" {
  description = "Empty while CloudFront is disabled."
  value       = var.enable_cloudfront ? module.frontend[0].bucket : ""
}

output "cloudfront_distribution_id" {
  value = var.enable_cloudfront ? module.frontend[0].distribution_id : ""
}

output "app_url" {
  description = "Where users open the app; also a CORS origin the backend allows."
  value = (
    !var.enable_cloudfront ? "http://${local.api_fqdn}" :
    var.domain_name == "" ? "https://${module.frontend[0].domain_name}" :
    "https://${module.dns[0].app_fqdn}"
  )
}

output "api_fqdn" {
  value = local.api_fqdn
}

output "jwt_secret" {
  description = "Base64, 64 bytes; goes into the backend-secrets Secret."
  value       = random_bytes.jwt_secret.base64
  sensitive   = true
}
