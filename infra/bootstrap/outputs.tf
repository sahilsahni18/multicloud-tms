# Copy these into the GitHub repository (Settings -> Secrets and variables ->
# Actions -> Variables) and into infra/envs/*/backend.hcl.

output "aws_state_bucket" {
  value = aws_s3_bucket.state.bucket
}

output "aws_github_role_arn" {
  description = "GitHub variable AWS_ROLE_ARN"
  value       = aws_iam_role.github.arn
}

output "azure_state_resource_group" {
  value = azurerm_resource_group.state.name
}

output "azure_state_storage_account" {
  value = azurerm_storage_account.state.name
}

output "azure_client_id" {
  description = "GitHub variable AZURE_CLIENT_ID"
  value       = azuread_application.github.client_id
}

output "azure_tenant_id" {
  description = "GitHub variable AZURE_TENANT_ID"
  value       = data.azurerm_subscription.current.tenant_id
}

output "azure_subscription_id" {
  description = "GitHub variable AZURE_SUBSCRIPTION_ID"
  value       = data.azurerm_subscription.current.subscription_id
}
